package dev.fincore.evidence.api;

import dev.fincore.evidence.domain.RecordIntegrityFlag;
import java.time.Instant;
import java.util.UUID;

public record RecordIntegrityFlagResponse(
        UUID id,
        String flagType,
        Instant detectedAt,
        UUID detectedByBatchId,
        Instant resolvedAt,
        UUID resolvedByDivergenceId) {

    public static RecordIntegrityFlagResponse from(RecordIntegrityFlag flag) {
        return new RecordIntegrityFlagResponse(
                flag.id(),
                flag.flagType().name(),
                flag.detectedAt(),
                flag.detectedByBatchId(),
                flag.resolvedAt(),
                flag.resolvedByDivergenceId());
    }
}
