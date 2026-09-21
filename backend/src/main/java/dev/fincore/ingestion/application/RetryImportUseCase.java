package dev.fincore.ingestion.application;

import dev.fincore.audit.domain.ActorRef;
import dev.fincore.ingestion.domain.ImportBatch;
import dev.fincore.ingestion.domain.ImportStatus;
import dev.fincore.ingestion.infrastructure.ImportBatchRepository;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * {@code POST /imports/{id}/retry} (Implementation Plan M8) — só a partir de {@code FAILED}.
 * Mesma autorização de {@link ImportFileUseCase#execute}: reprocessar é operar dados, não
 * consultá-los (TDS 20.2, "ANALYST escreve, todos leem").
 */
@Service
public class RetryImportUseCase {

    private final ImportBatchRepository importBatchRepository;
    private final ImportBatchTransactionalSteps transactionalSteps;

    public RetryImportUseCase(ImportBatchRepository importBatchRepository, ImportBatchTransactionalSteps transactionalSteps) {
        this.importBatchRepository = importBatchRepository;
        this.transactionalSteps = transactionalSteps;
    }

    @PreAuthorize("hasAuthority('RECONCILIATION_ANALYST')")
    public ImportBatch execute(UUID batchId, UUID actorUserId, String actorLabel) {
        ImportBatch batch = importBatchRepository.findById(batchId)
                .orElseThrow(() -> new NoSuchElementException("import_batch não encontrado: " + batchId));
        if (batch.status() != ImportStatus.FAILED) {
            throw new ImportBatchNotRetryableException(
                    "import_batch " + batchId + " está em " + batch.status() + ", não em FAILED");
        }
        return transactionalSteps.retryFromFailure(batchId, ActorRef.user(actorUserId, actorLabel));
    }
}
