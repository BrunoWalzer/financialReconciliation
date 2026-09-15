package dev.fincore.architecture.violation.matching;

import dev.fincore.architecture.violation.ConfidenceScore;

/** Viola MATCHING_DOES_NOT_REFERENCE_SCORE_TYPES: decide por score dentro de matching. */
public class ScoreDrivenRule {

    public boolean matches(ConfidenceScore score) {
        return score.value() > 80;
    }
}
