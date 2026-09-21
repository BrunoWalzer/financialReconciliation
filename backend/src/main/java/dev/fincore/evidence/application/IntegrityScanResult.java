package dev.fincore.evidence.application;

/** Quantas flags cada uma das quatro detecções (TDS 9.6) efetivamente criou nesta varredura. */
public record IntegrityScanResult(
        int duplicateExternalIdCount,
        int duplicateCorrelationKeyCount,
        int possibleDuplicateCount,
        int sourceInternalInconsistencyCount) {

    public int totalFlagsCreated() {
        return duplicateExternalIdCount + duplicateCorrelationKeyCount
                + possibleDuplicateCount + sourceInternalInconsistencyCount;
    }
}
