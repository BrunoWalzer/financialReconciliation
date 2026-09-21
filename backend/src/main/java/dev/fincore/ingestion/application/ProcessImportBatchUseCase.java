package dev.fincore.ingestion.application;

import dev.fincore.audit.domain.ActorRef;
import dev.fincore.configuration.application.GetSourceUseCase;
import dev.fincore.configuration.domain.Source;
import dev.fincore.evidence.domain.FinancialRecord;
import dev.fincore.ingestion.domain.ImportBatch;
import dev.fincore.ingestion.domain.ImportStatus;
import dev.fincore.ingestion.domain.RejectedRecord;
import dev.fincore.ingestion.infrastructure.FileStorage;
import dev.fincore.ingestion.infrastructure.ImportBatchRepository;
import dev.fincore.ingestion.infrastructure.IngestionProperties;
import dev.fincore.ingestion.parser.CsvEncodingDetector;
import dev.fincore.ingestion.parser.CsvLineSplitter;
import dev.fincore.ingestion.parser.ParseContext;
import dev.fincore.ingestion.parser.ParseResult;
import dev.fincore.ingestion.parser.RawLine;
import dev.fincore.ingestion.parser.RecordParser;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * O trabalho que a fila {@code fincore.import.process} carrega (Implementation Plan M8,
 * TDS 17.1): parsear, persistir em lotes e varrer integridade — exatamente o mesmo pipeline
 * do M5/M7, só que disparado pelo worker (via {@code ImportJobListener}) em vez de dentro da
 * requisição HTTP. Nenhum parser, normalizador ou passo de persistência é duplicado: tudo
 * aqui reusa {@link ImportBatchTransactionalSteps}, os mesmos {@link RecordParser} e o mesmo
 * {@link GetSourceUseCase} que o M5 já usava.
 *
 * <p><b>Guarda de idempotência</b> (TDS 17.3): entidade inexistente ou já num estado que só
 * sai por ação humana (terminal, ou {@code FAILED} aguardando {@code POST .../retry}) é
 * no-op. Uma redelivery da mesma mensagem, ou duas mensagens para o mesmo lote, nunca
 * duplicam {@link FinancialRecord} — a mesma proteção {@code ON CONFLICT DO NOTHING} do M5.
 *
 * <p>Não tem {@code @PreAuthorize}: não existe requisição HTTP aqui para proteger — a
 * autorização já foi checada quando {@code POST /imports} criou o lote em {@code RECEIVED}
 * (Implementation Plan M8, seção 18: "o worker não é um usuário HTTP"). Quem invoca isto
 * ({@code ImportJobListener}) estabelece um {@code Authentication} de sistema só para que os
 * casos de uso internos com {@code isAuthenticated()} (M5/M7) continuem funcionando.
 */
@Service
public class ProcessImportBatchUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProcessImportBatchUseCase.class);

    private static final String EMPTY_FILE = "EMPTY_FILE";
    private static final String LAYOUT_MISMATCH = "LAYOUT_MISMATCH";

    private final ImportBatchRepository importBatchRepository;
    private final GetSourceUseCase getSourceUseCase;
    private final ImportBatchTransactionalSteps transactionalSteps;
    private final FileStorage fileStorage;
    private final IngestionProperties properties;
    private final Clock clock;
    private final Map<String, RecordParser> parsersBySourceCode;

    public ProcessImportBatchUseCase(
            ImportBatchRepository importBatchRepository,
            GetSourceUseCase getSourceUseCase,
            ImportBatchTransactionalSteps transactionalSteps,
            FileStorage fileStorage,
            IngestionProperties properties,
            Clock clock,
            List<RecordParser> parsers) {
        this.importBatchRepository = importBatchRepository;
        this.getSourceUseCase = getSourceUseCase;
        this.transactionalSteps = transactionalSteps;
        this.fileStorage = fileStorage;
        this.properties = properties;
        this.clock = clock;
        this.parsersBySourceCode = parsers.stream().collect(Collectors.toMap(RecordParser::sourceCode, p -> p));
    }

    public void execute(UUID batchId) {
        Optional<ImportBatch> maybeBatch = importBatchRepository.findById(batchId);
        if (maybeBatch.isEmpty()) {
            log.warn("import_batch {} não encontrado — mensagem órfã, ack sem processar", batchId);
            return;
        }
        ImportBatch batch = maybeBatch.get();
        if (isStoppedState(batch.status())) {
            log.info("import_batch {} já em {}, no-op (redelivery ou corrida entre workers)", batchId, batch.status());
            return;
        }

        try {
            processFrom(batchId, batch);
        } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
            // Dois workers genuinamente concorrentes no mesmo lote (ex.: o sweep republica
            // um lote que outro worker, mais lento, ainda está processando de fato) — quem
            // perde a corrida do @Version não corrompeu nada; o vencedor já avançou o
            // estado. Idempotência por guarda de estado (TDS 17.3) trataria a próxima
            // entrega como no-op de qualquer forma; aqui só evitamos a espera do retry.
            log.info("import_batch {} teve conflito de versão — outro worker já avançou o estado, no-op", batchId);
        }
    }

    private void processFrom(UUID batchId, ImportBatch batch) {
        Source source = getSourceUseCase.execute(batch.sourceId());
        RecordParser parser = parsersBySourceCode.get(source.code());
        ActorRef actor = ActorRef.system(batchId);
        if (parser == null) {
            transactionalSteps.markFailed(
                    batchId, "nenhum RecordParser registrado para " + source.code(), clock.instant(), actor);
            return;
        }

        byte[] content = fileStorage.read(batch.storageKey());
        processFile(batchId, content, source, parser, actor);
    }

    /** Estados que só saem por ação humana ou já terminaram — nunca reprocessados por mensagem. */
    private static boolean isStoppedState(ImportStatus status) {
        return status.isTerminal() || status == ImportStatus.FAILED;
    }

    private void processFile(UUID batchId, byte[] content, Source source, RecordParser parser, ActorRef actor) {
        transactionalSteps.startProcessing(batchId, clock.instant());

        CsvEncodingDetector.Detection detection = CsvEncodingDetector.detect(content);
        ParseContext context = new ParseContext(
                source.id(), ZoneId.of(source.timezone()), source.decimalSeparator().charAt(0),
                source.thousandsSeparator().charAt(0), source.dateFormats());

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ByteArrayInputStream(content, detection.bomLength(), content.length - detection.bomLength()),
                detection.charset()))) {

            String headerLine = reader.readLine();
            if (headerLine == null) {
                transactionalSteps.rejectStructurally(batchId, EMPTY_FILE, clock.instant(), actor);
                return;
            }
            List<String> header = CsvLineSplitter.split(headerLine);
            if (!parser.layout().matchesHeader(header)) {
                transactionalSteps.rejectStructurally(batchId, LAYOUT_MISMATCH, clock.instant(), actor);
                return;
            }

            processDataLines(batchId, reader, parser, context, source.id(), actor);

        } catch (IOException e) {
            transactionalSteps.markFailed(batchId, "falha de leitura: " + e.getMessage(), clock.instant(), actor);
        } catch (RuntimeException e) {
            transactionalSteps.markFailed(batchId, "falha de processamento: " + e.getMessage(), clock.instant(), actor);
        }
    }

    private void processDataLines(
            UUID batchId, BufferedReader reader, RecordParser parser, ParseContext context, UUID sourceId, ActorRef actor)
            throws IOException {

        List<FinancialRecord> pendingValid = new ArrayList<>();
        List<RejectedRecord> pendingRejected = new ArrayList<>();

        int totalDataLines = 0;
        int accepted = 0;
        int rejectedCount = 0;
        int alreadyExisting = 0;

        String rawLine;
        int physicalLineNumber = 1; // o cabeçalho já consumiu a linha 1.
        while ((rawLine = reader.readLine()) != null) {
            physicalLineNumber++;
            if (rawLine.isBlank()) {
                continue;
            }
            totalDataLines++;

            RawLine line = new RawLine(physicalLineNumber, rawLine, CsvLineSplitter.split(rawLine));
            ParseResult result = parser.parseLine(line, context);
            if (result instanceof ParseResult.Accepted accepted_) {
                pendingValid.add(toFinancialRecord(sourceId, batchId, physicalLineNumber, rawLine, accepted_, clock.instant()));
            } else if (result instanceof ParseResult.Rejected rejected_) {
                pendingRejected.add(new RejectedRecord(
                        batchId, physicalLineNumber, rawLine, rejected_.reasonCode(), rejected_.detail(),
                        rejected_.extractedAmountMinor()));
            }

            if (pendingValid.size() + pendingRejected.size() >= properties.batchSize()) {
                BatchLineResult batchResult = transactionalSteps.processLineBatch(pendingValid, pendingRejected);
                accepted += batchResult.insertedCount();
                alreadyExisting += batchResult.alreadyExistingCount();
                rejectedCount += batchResult.rejectedCount();
                pendingValid = new ArrayList<>();
                pendingRejected = new ArrayList<>();
            }
        }

        if (!pendingValid.isEmpty() || !pendingRejected.isEmpty()) {
            BatchLineResult batchResult = transactionalSteps.processLineBatch(pendingValid, pendingRejected);
            accepted += batchResult.insertedCount();
            alreadyExisting += batchResult.alreadyExistingCount();
            rejectedCount += batchResult.rejectedCount();
        }

        if (totalDataLines == 0) {
            // Cabeçalho válido, zero linhas de dado — mesma família de EMPTY_FILE (Domain §9.3).
            transactionalSteps.rejectStructurally(batchId, EMPTY_FILE, clock.instant(), actor);
            return;
        }

        transactionalSteps.completeSuccessfully(
                batchId, totalDataLines, accepted, rejectedCount, alreadyExisting, clock.instant(), actor);

        // Varredura de integridade intra-fonte (Implementation Plan M7, TDS 9.6): só faz
        // sentido quando o lote produziu ao menos um FinancialRecord novo — REJECTED
        // (accepted == 0) não tem o que a varredura examine.
        if (accepted > 0) {
            transactionalSteps.runIntegrityScan(sourceId, batchId, clock.instant());
        }
    }

    private static FinancialRecord toFinancialRecord(
            UUID sourceId, UUID importBatchId, int lineNumber, String rawLine, ParseResult.Accepted accepted, Instant createdAt) {
        var draft = accepted.draft();
        return new FinancialRecord(
                sourceId, importBatchId, lineNumber, draft.externalId(), draft.correlationKey(),
                draft.direction(), draft.recordType(), draft.grossAmount(), draft.declaredFeeAmount(),
                draft.netAmount(), draft.businessDate(), draft.sourceTimestamp(), draft.counterpartyDocument(),
                draft.paymentMethod(), draft.description(), draft.descriptionNormalized(), rawLine,
                draft.fingerprint(), createdAt);
    }
}
