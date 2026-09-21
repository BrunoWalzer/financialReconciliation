package dev.fincore.evidence.api;

import dev.fincore.evidence.domain.RecordAnnotation;
import java.time.Instant;
import java.util.UUID;

public record RecordAnnotationResponse(UUID id, UUID financialRecordId, UUID authorId, String text, Instant createdAt) {

    public static RecordAnnotationResponse from(RecordAnnotation annotation) {
        return new RecordAnnotationResponse(
                annotation.id(), annotation.financialRecordId(), annotation.authorId(),
                annotation.text(), annotation.createdAt());
    }
}
