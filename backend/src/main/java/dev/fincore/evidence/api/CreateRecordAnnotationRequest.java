package dev.fincore.evidence.api;

import jakarta.validation.constraints.NotBlank;

public record CreateRecordAnnotationRequest(@NotBlank String text) {
}
