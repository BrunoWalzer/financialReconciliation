package dev.fincore.ingestion.api;

import dev.fincore.ingestion.domain.ImportBatch;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ImportBatchResponse(
        UUID id,
        String sourceCode,
        String originalFilename,
        String status,
        LocalDate referenceDate,
        Integer totalLines,
        Integer acceptedCount,
        Integer rejectedCount,
        Integer alreadyExistingCount,
        String rejectionReason,
        UUID reimportOfId,
        String reimportReason,
        UUID uploadedBy,
        Instant uploadedAt,
        Instant startedAt,
        Instant finishedAt,
        long version) {

    public static ImportBatchResponse from(ImportBatch batch, String sourceCode) {
        return new ImportBatchResponse(
                batch.id(), sourceCode, batch.originalFilename(), batch.status().name(), batch.referenceDate(),
                batch.totalLines(), batch.acceptedCount(), batch.rejectedCount(), batch.alreadyExistingCount(),
                batch.rejectionReason(), batch.reimportOfId(), batch.reimportReason(), batch.uploadedBy(),
                batch.uploadedAt(), batch.startedAt(), batch.finishedAt(), batch.version());
    }
}
