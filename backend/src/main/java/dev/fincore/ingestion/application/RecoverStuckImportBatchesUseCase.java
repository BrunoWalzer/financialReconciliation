package dev.fincore.ingestion.application;

import dev.fincore.audit.application.AuditEventRequest;
import dev.fincore.audit.application.AuditService;
import dev.fincore.audit.domain.ActorRef;
import dev.fincore.ingestion.domain.ImportBatch;
import dev.fincore.ingestion.infrastructure.ImportBatchRepository;
import dev.fincore.ingestion.infrastructure.IngestionProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * O sweep de trabalhos travados (Implementation Plan M8, TDS 17.5): substitui outbox
 * transacional — uma mensagem perdida entre o commit de {@code createReceived} e a
 * publicação real (ex.: RabbitMQ indisponível naquele instante) é recuperada porque a
 * entidade ficou em estado não-terminal ({@code RECEIVED}/{@code PROCESSING}) além do
 * prazo. Reusa {@link ImportBatchReceivedEvent} + o mesmo listener {@code AFTER_COMMIT}
 * que {@link ImportBatchTransactionalSteps#createReceived} usa — republicar não é um
 * segundo mecanismo de publicação.
 *
 * <p>Não muda o {@code status} do lote: só reenfileira. Quem de fato o processa de novo é
 * {@link ProcessImportBatchUseCase}, cuja guarda de idempotência (TDS 17.3) já sabe lidar
 * com um lote em {@code PROCESSING} recuperado (reentra em {@code startProcessing}).
 */
@Service
public class RecoverStuckImportBatchesUseCase {

    private final ImportBatchRepository importBatchRepository;
    private final IngestionProperties properties;
    private final Clock clock;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;

    public RecoverStuckImportBatchesUseCase(
            ImportBatchRepository importBatchRepository,
            IngestionProperties properties,
            Clock clock,
            AuditService auditService,
            ApplicationEventPublisher eventPublisher) {
        this.importBatchRepository = importBatchRepository;
        this.properties = properties;
        this.clock = clock;
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
    }

    /** Devolve quantos lotes travados foram encontrados e reenfileirados. */
    @Transactional
    public int execute() {
        Instant threshold = clock.instant().minus(properties.stuckThreshold());
        List<ImportBatch> stuck = importBatchRepository.findStuckBatches(threshold);
        for (ImportBatch batch : stuck) {
            auditService.record(AuditEventRequest.of(
                    ActorRef.system(batch.id()), "IMPORT_BATCH_RECOVERED", "ImportBatch", batch.id()));
            eventPublisher.publishEvent(new ImportBatchReceivedEvent(batch.id(), batch.correlationId()));
        }
        return stuck.size();
    }
}
