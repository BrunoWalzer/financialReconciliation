package dev.fincore.matching.domain;

import dev.fincore.evidence.domain.FinancialRecord;
import java.util.Objects;

/**
 * Um candidato: um {@link FinancialRecord} de cada lado do par de fontes (Domain §5.2).
 * {@code left}/{@code right} espelham {@code source_pair.left_source_id}/{@code right_source_id}
 * (TDS 7.3) — nunca "esquerda" e "direita" no sentido geográfico.
 */
public record RecordPair(FinancialRecord left, FinancialRecord right) {

    public RecordPair {
        Objects.requireNonNull(left, "left é obrigatório");
        Objects.requireNonNull(right, "right é obrigatório");
    }
}
