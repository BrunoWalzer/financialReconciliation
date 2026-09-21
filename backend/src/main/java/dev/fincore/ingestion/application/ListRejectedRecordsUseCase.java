package dev.fincore.ingestion.application;

import dev.fincore.ingestion.domain.RejectedRecord;
import dev.fincore.ingestion.infrastructure.ImportBatchRepository;
import dev.fincore.ingestion.infrastructure.RejectedRecordRepository;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/** {@code GET /imports/{id}/rejected-records} — leitura para todos os papéis autenticados. */
@Service
public class ListRejectedRecordsUseCase {

    private final RejectedRecordRepository rejectedRecordRepository;
    private final ImportBatchRepository importBatchRepository;

    public ListRejectedRecordsUseCase(
            RejectedRecordRepository rejectedRecordRepository, ImportBatchRepository importBatchRepository) {
        this.rejectedRecordRepository = rejectedRecordRepository;
        this.importBatchRepository = importBatchRepository;
    }

    @PreAuthorize("isAuthenticated()")
    public Page<RejectedRecord> execute(UUID importBatchId, Pageable pageable) {
        importBatchRepository.findById(importBatchId)
                .orElseThrow(() -> new NoSuchElementException("import_batch não encontrado: " + importBatchId));
        return rejectedRecordRepository.findByImportBatchId(importBatchId, pageable);
    }
}
