package dev.fincore.ingestion.application;

import dev.fincore.audit.application.AuditEventRequest;
import dev.fincore.audit.application.AuditService;
import dev.fincore.audit.domain.ActorRef;
import dev.fincore.evidence.application.BulkInsertFinancialRecordsUseCase;
import dev.fincore.evidence.application.BulkInsertResult;
import dev.fincore.evidence.application.IntegrityScanResult;
import dev.fincore.evidence.application.RunIntegrityScanUseCase;
import dev.fincore.evidence.domain.FinancialRecord;
import dev.fincore.ingestion.domain.ImportBatch;
import dev.fincore.ingestion.domain.ImportStatus;
import dev.fincore.ingestion.domain.RejectedRecord;
import dev.fincore.ingestion.infrastructure.ImportBatchRepository;
import dev.fincore.ingestion.infrastructure.RejectedRecordRepository;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cada método é sua própria transação (TDS 9.5: RECEIVED curta, um lote de 1000 linhas por
 * vez, encerramento final). {@link ImportFileUseCase} não é {@code @Transactional} — se
 * fosse, chamar estes métodos por {@code this} não passaria pelo proxy do Spring e a
 * fronteira de transação seria só decoração. Por isso vivem num bean separado: cada
 * chamada, vinda de fora, abre (via propagação padrão {@code REQUIRED}, sem
 * {@code REQUIRES_NEW}) sua própria transação porque o chamador não tem nenhuma em curso.
 */
@Service
class ImportBatchTransactionalSteps {

    private final ImportBatchRepository importBatchRepository;
    private final RejectedRecordRepository rejectedRecordRepository;
    private final BulkInsertFinancialRecordsUseCase bulkInsertFinancialRecordsUseCase;
    private final RunIntegrityScanUseCase runIntegrityScanUseCase;
    private final AuditService auditService;

    ImportBatchTransactionalSteps(
            ImportBatchRepository importBatchRepository,
            RejectedRecordRepository rejectedRecordRepository,
            BulkInsertFinancialRecordsUseCase bulkInsertFinancialRecordsUseCase,
            RunIntegrityScanUseCase runIntegrityScanUseCase,
            AuditService auditService) {
        this.importBatchRepository = importBatchRepository;
        this.rejectedRecordRepository = rejectedRecordRepository;
        this.bulkInsertFinancialRecordsUseCase = bulkInsertFinancialRecordsUseCase;
        this.runIntegrityScanUseCase = runIntegrityScanUseCase;
        this.auditService = auditService;
    }

    @Transactional
    ImportBatch createReceived(ImportBatch batch, ActorRef actor) {
        ImportBatch saved = importBatchRepository.save(batch);
        auditService.record(AuditEventRequest.of(actor, "IMPORT_BATCH_CREATED", "ImportBatch", saved.id()));
        return saved;
    }

    @Transactional
    ImportBatch startProcessing(UUID batchId, Instant now) {
        ImportBatch batch = requireBatch(batchId);
        batch.startProcessing(now);
        return importBatchRepository.save(batch);
    }

    @Transactional
    BatchLineResult processLineBatch(List<FinancialRecord> validRecords, List<RejectedRecord> rejectedRecords) {
        BulkInsertResult insertResult = bulkInsertFinancialRecordsUseCase.execute(validRecords);
        for (RejectedRecord rejectedRecord : rejectedRecords) {
            rejectedRecordRepository.save(rejectedRecord);
        }
        return new BatchLineResult(insertResult.insertedCount(), insertResult.alreadyExistingCount(), rejectedRecords.size());
    }

    @Transactional
    ImportBatch completeSuccessfully(
            UUID batchId, int totalLines, int accepted, int rejectedCount, int alreadyExisting, Instant now, ActorRef actor) {
        ImportBatch batch = requireBatch(batchId);
        batch.complete(totalLines, accepted, rejectedCount, alreadyExisting, now);
        ImportBatch saved = importBatchRepository.save(batch);
        String action = saved.status() == ImportStatus.REJECTED ? "IMPORT_BATCH_REJECTED" : "IMPORT_BATCH_COMPLETED";
        auditService.record(AuditEventRequest.of(actor, action, "ImportBatch", saved.id()));
        return saved;
    }

    @Transactional
    ImportBatch rejectStructurally(UUID batchId, String reasonCode, Instant now, ActorRef actor) {
        ImportBatch batch = requireBatch(batchId);
        batch.rejectStructurally(reasonCode, now);
        ImportBatch saved = importBatchRepository.save(batch);
        auditService.record(AuditEventRequest.of(actor, "IMPORT_BATCH_REJECTED", "ImportBatch", saved.id()));
        return saved;
    }

    @Transactional
    ImportBatch markFailed(UUID batchId, String reason, Instant now, ActorRef actor) {
        ImportBatch batch = requireBatch(batchId);
        batch.fail(reason, now);
        ImportBatch saved = importBatchRepository.save(batch);
        auditService.record(AuditEventRequest.of(actor, "IMPORT_BATCH_FAILED", "ImportBatch", saved.id()));
        return saved;
    }

    @Transactional
    IntegrityScanResult runIntegrityScan(UUID sourceId, UUID batchId, Instant now) {
        return runIntegrityScanUseCase.execute(sourceId, batchId, now);
    }

    private ImportBatch requireBatch(UUID batchId) {
        return importBatchRepository.findById(batchId)
                .orElseThrow(() -> new NoSuchElementException("import_batch não encontrado: " + batchId));
    }
}
