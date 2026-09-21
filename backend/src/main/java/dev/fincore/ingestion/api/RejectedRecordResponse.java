package dev.fincore.ingestion.api;

import dev.fincore.ingestion.domain.RejectedRecord;
import java.util.UUID;

public record RejectedRecordResponse(
        UUID id, int lineNumber, String rawLine, String reasonCode, String reasonDetail, Long extractedAmountMinor) {

    public static RejectedRecordResponse from(RejectedRecord record) {
        return new RejectedRecordResponse(
                record.id(), record.lineNumber(), record.rawLine(), record.reasonCode().name(),
                record.reasonDetail(), record.extractedAmountMinor());
    }
}
