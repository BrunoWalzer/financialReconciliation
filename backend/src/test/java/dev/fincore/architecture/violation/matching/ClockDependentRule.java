package dev.fincore.architecture.violation.matching;

import java.time.LocalDate;

/** Viola MATCHING_DOES_NOT_READ_THE_CLOCK: le o relogio dentro de matching. */
public class ClockDependentRule {

    public LocalDate evaluationDate() {
        return LocalDate.now();
    }
}
