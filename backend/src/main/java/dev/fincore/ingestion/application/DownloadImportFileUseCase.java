package dev.fincore.ingestion.application;

import dev.fincore.audit.application.AuditEventRequest;
import dev.fincore.audit.application.AuditService;
import dev.fincore.audit.domain.ActorRef;
import dev.fincore.ingestion.domain.ImportBatch;
import dev.fincore.ingestion.infrastructure.FileStorage;
import dev.fincore.ingestion.infrastructure.ImportBatchRepository;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@code GET /imports/{id}/file} — audita o download (TDS 22.2: "arquivo baixado"). */
@Service
public class DownloadImportFileUseCase {

    private final ImportBatchRepository importBatchRepository;
    private final FileStorage fileStorage;
    private final AuditService auditService;

    public DownloadImportFileUseCase(
            ImportBatchRepository importBatchRepository, FileStorage fileStorage, AuditService auditService) {
        this.importBatchRepository = importBatchRepository;
        this.fileStorage = fileStorage;
        this.auditService = auditService;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional
    public DownloadedFile execute(UUID importBatchId, UUID actorUserId, String actorLabel) {
        ImportBatch batch = importBatchRepository.findById(importBatchId)
                .orElseThrow(() -> new NoSuchElementException("import_batch não encontrado: " + importBatchId));

        byte[] content = fileStorage.read(batch.storageKey());

        auditService.record(AuditEventRequest.of(
                ActorRef.user(actorUserId, actorLabel), "IMPORT_FILE_DOWNLOADED", "ImportBatch", batch.id()));

        return new DownloadedFile(batch.originalFilename(), content);
    }
}
