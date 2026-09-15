package dev.fincore.architecture.violation;

/** Tipo com "Score" no nome, usado por {@code ScoreDrivenRule}. */
public class ConfidenceScore {

    private final int value;

    public ConfidenceScore(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }
}
