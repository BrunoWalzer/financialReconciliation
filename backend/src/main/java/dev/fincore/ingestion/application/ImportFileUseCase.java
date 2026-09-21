package dev.fincore.ingestion.application;

import dev.fincore.audit.domain.ActorRef;
import dev.fincore.configuration.application.GetSourceByCodeUseCase;
import dev.fincore.configuration.domain.Source;
import dev.fincore.evidence.domain.FinancialRecord;
import dev.fincore.ingestion.domain.ImportBatch;
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
import dev.fincore.shared.identifier.Uuid7;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * {@code POST /imports} — o pipeline inteiro, síncrono (Implementation Plan M6/TDS 9.2,
 * adaptado para rodar dentro da própria requisição neste milestone: RabbitMQ e {@code 202}
 * são M8). Ver o relatório do M5, seção Decisões, sobre por que este milestone entrega o
 * que o Implementation Plan chamava de M5 e M6 juntos.
 *
 * <p>Deliberadamente <b>não</b> {@code @Transactional}: a fronteira de transação é por
 * lote de 1000 linhas (TDS 9.5), não a operação inteira. Cada passo transacional vive em
 * {@link ImportBatchTransactionalSteps}, um bean diferente — chamar {@code this.algo()}
 * aqui dentro não passaria pelo proxy do Spring.
 */
@Service
public class ImportFileUseCase {

    private static final String EMPTY_FILE = "EMPTY_FILE";
    private static final String LAYOUT_MISMATCH = "LAYOUT_MISMATCH";

    private final GetSourceByCodeUseCase getSourceByCodeUseCase;
    private final ImportBatchRepository importBatchRepository;
    private final ImportBatchTransactionalSteps transactionalSteps;
    private final FileStorage fileStorage;
    private final IngestionProperties properties;
    private final Clock clock;
    private final Map<String, RecordParser> parsersBySourceCode;

    public ImportFileUseCase(
            GetSourceByCodeUseCase getSourceByCodeUseCase,
            ImportBatchRepository importBatchRepository,
            ImportBatchTransactionalSteps transactionalSteps,
            FileStorage fileStorage,
            IngestionProperties properties,
            Clock clock,
            List<RecordParser> parsers) {
        this.getSourceByCodeUseCase = getSourceByCodeUseCase;
        this.importBatchRepository = importBatchRepository;
        this.transactionalSteps = transactionalSteps;
        this.fileStorage = fileStorage;
        this.properties = properties;
        this.clock = clock;
        this.parsersBySourceCode = parsers.stream().collect(Collectors.toMap(RecordParser::sourceCode, p -> p));
    }

    // TDS 20.2 (v1.0, mantido na v1.1): "Importações — ANALYST escreve, todos leem" —
    // segregação de função consistente com "ADMINISTRATOR não resolve divergência"
    // (Implementation Plan §2.1): administrar configuração e operar dados são papéis
    // distintos neste projeto.
    @PreAuthorize("hasAuthority('RECONCILIATION_ANALYST')")
    public ImportBatch execute(ImportFileCommand command, UUID actorUserId, String actorLabel) {
        Source source = getSourceByCodeUseCase.execute(command.sourceCode())
                .orElseThrow(() -> new IllegalArgumentException("sourceCode desconhecido: " + command.sourceCode()));

        RecordParser parser = parsersBySourceCode.get(source.code());
        if (parser == null) {
            throw new IllegalStateException("nenhum RecordParser registrado para " + source.code());
        }

        if (!command.originalFilename().toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw new IllegalArgumentException("apenas arquivos .csv são aceitos");
        }

        byte[] content = command.content();

        // Redundante com spring.servlet.multipart.max-file-size de propósito (TDS 9.1:
        // "rejeitados antes de qualquer parsing"): o limite do Spring depende do
        // resolvedor de multipart do contêiner servlet real, que o MockMvc não reproduz
        // fielmente — esta checagem é a que garante o limite de forma testável.
        if (content.length > properties.maxUploadBytes()) {
            throw new UploadTooLargeException("arquivo excede o limite de " + properties.maxUploadBytes() + " bytes");
        }

        long lineCount = countLines(content);
        if (lineCount > properties.maxUploadLines()) {
            throw new IllegalArgumentException(
                    "arquivo excede o limite de " + properties.maxUploadLines() + " linhas");
        }

        String sha256 = sha256Hex(content);
        ActorRef actor = ActorRef.user(actorUserId, actorLabel);

        if (command.reimportOfId() == null) {
            importBatchRepository
                    .findBySourceIdAndContentSha256AndReferenceDateAndReimportOfIdIsNull(
                            source.id(), sha256, command.referenceDate())
                    .ifPresent(existing -> {
                        throw new DuplicateFileException(existing.id());
                    });
        }

        String storageKey = fileStorage.store(Uuid7.generate().toString(), content);

        ImportBatch batch = new ImportBatch(
                source.id(), command.originalFilename(), sha256, content.length, command.referenceDate(),
                storageKey, actorUserId, clock.instant(), command.reimportOfId(), command.reimportReason(), null);
        try {
            batch = transactionalSteps.createReceived(batch, actor);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // Corrida genuína: outro upload venceu entre a checagem acima e este insert
            // (TDS 9.5 — sem verificação prévia que evitasse a janela, "teria corrida").
            ImportBatch winner = command.reimportOfId() == null
                    ? importBatchRepository
                            .findBySourceIdAndContentSha256AndReferenceDateAndReimportOfIdIsNull(
                                    source.id(), sha256, command.referenceDate())
                            .orElseThrow(() -> e)
                    : null;
            if (winner == null) {
                throw e;
            }
            throw new DuplicateFileException(winner.id());
        }

        return processFile(batch.id(), content, source, parser, actor);
    }

    private ImportBatch processFile(UUID batchId, byte[] content, Source source, RecordParser parser, ActorRef actor) {
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
                return transactionalSteps.rejectStructurally(batchId, EMPTY_FILE, clock.instant(), actor);
            }
            List<String> header = CsvLineSplitter.split(headerLine);
            if (!parser.layout().matchesHeader(header)) {
                return transactionalSteps.rejectStructurally(batchId, LAYOUT_MISMATCH, clock.instant(), actor);
            }

            return processDataLines(batchId, reader, parser, context, source.id(), actor);

        } catch (IOException e) {
            return transactionalSteps.markFailed(batchId, "falha de leitura: " + e.getMessage(), clock.instant(), actor);
        } catch (RuntimeException e) {
            return transactionalSteps.markFailed(batchId, "falha de processamento: " + e.getMessage(), clock.instant(), actor);
        }
    }

    private ImportBatch processDataLines(
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
            return transactionalSteps.rejectStructurally(batchId, EMPTY_FILE, clock.instant(), actor);
        }

        ImportBatch completed = transactionalSteps.completeSuccessfully(
                batchId, totalDataLines, accepted, rejectedCount, alreadyExisting, clock.instant(), actor);

        // Varredura de integridade intra-fonte (Implementation Plan M7, TDS 9.6): só faz
        // sentido quando o lote produziu ao menos um FinancialRecord novo — REJECTED
        // (accepted == 0) não tem o que a varredura examine.
        if (accepted > 0) {
            transactionalSteps.runIntegrityScan(sourceId, batchId, clock.instant());
        }

        return completed;
    }

    private static FinancialRecord toFinancialRecord(
            UUID sourceId, UUID importBatchId, int lineNumber, String rawLine, ParseResult.Accepted accepted,
            java.time.Instant createdAt) {
        var draft = accepted.draft();
        return new FinancialRecord(
                sourceId, importBatchId, lineNumber, draft.externalId(), draft.correlationKey(),
                draft.direction(), draft.recordType(), draft.grossAmount(), draft.declaredFeeAmount(),
                draft.netAmount(), draft.businessDate(), draft.sourceTimestamp(), draft.counterpartyDocument(),
                draft.paymentMethod(), draft.description(), draft.descriptionNormalized(), rawLine,
                draft.fingerprint(), createdAt);
    }

    private static long countLines(byte[] content) {
        if (content.length == 0) {
            return 0;
        }
        long lines = 0;
        for (byte b : content) {
            if (b == '\n') {
                lines++;
            }
        }
        // Última linha sem quebra final ainda conta.
        if (content[content.length - 1] != '\n') {
            lines++;
        }
        return lines;
    }

    private static String sha256Hex(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível na JVM", e);
        }
    }
}
