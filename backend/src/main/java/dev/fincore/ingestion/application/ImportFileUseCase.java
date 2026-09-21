package dev.fincore.ingestion.application;

import dev.fincore.audit.domain.ActorRef;
import dev.fincore.configuration.application.GetSourceByCodeUseCase;
import dev.fincore.configuration.domain.Source;
import dev.fincore.ingestion.domain.ImportBatch;
import dev.fincore.ingestion.infrastructure.FileStorage;
import dev.fincore.ingestion.infrastructure.ImportBatchRepository;
import dev.fincore.ingestion.infrastructure.IngestionProperties;
import dev.fincore.ingestion.parser.RecordParser;
import dev.fincore.shared.correlation.CorrelationId;
import dev.fincore.shared.identifier.Uuid7;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * {@code POST /imports} — só a parte síncrona (Implementation Plan M8, TDS 17.1): valida,
 * calcula hash, grava o arquivo, cria o {@code import_batch} em {@code RECEIVED} e devolve.
 * O processamento em si (parsing, persistência, varredura de integridade) sai do caminho da
 * requisição — {@link ProcessImportBatchUseCase} é quem faz isso agora, disparado pela fila
 * {@code fincore.import.process} depois que {@link ImportBatchTransactionalSteps#createReceived}
 * publica {@link ImportBatchReceivedEvent} (após o commit, nunca dentro da transação).
 *
 * <p>Antes do M8 este método também processava o arquivo inline (M5/M6 absorvidos) — ver o
 * relatório do M5 sobre a fusão de milestones. A partir do M8, {@code POST /imports} devolve
 * {@code 202} com o lote ainda em {@code RECEIVED} (Implementation Plan M8).
 */
@Service
public class ImportFileUseCase {

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
        String correlationId = CorrelationId.current().orElseGet(CorrelationId::generate);

        ImportBatch batch = new ImportBatch(
                source.id(), command.originalFilename(), sha256, content.length, command.referenceDate(),
                storageKey, actorUserId, clock.instant(), command.reimportOfId(), command.reimportReason(), correlationId);
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

        // Processamento real acontece de forma assíncrona (ProcessImportBatchUseCase, via
        // ImportBatchReceivedEvent publicado dentro de createReceived) — este método só cria
        // o lote em RECEIVED e devolve; POST /imports responde 202 (Implementation Plan M8).
        return batch;
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
