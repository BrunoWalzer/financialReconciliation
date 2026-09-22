package dev.fincore.matching.domain;

import dev.fincore.evidence.domain.FinancialRecord;
import java.util.List;
import java.util.Objects;

/**
 * Uma correspondência automática decidida pelo motor (TDS 11.1). O motor não persiste —
 * isto é um valor devolvido para quem chama (matching.application, M10) gravar.
 */
public record MatchProposal(
        FinancialRecord left,
        FinancialRecord right,
        List<FinancialRecord> components,
        String ruleId,
        int ruleVersion,
        MatchEvidence evidence) {

    public MatchProposal {
        Objects.requireNonNull(left, "left é obrigatório");
        Objects.requireNonNull(right, "right é obrigatório");
        components = List.copyOf(components);
        Objects.requireNonNull(evidence, "evidence é obrigatório");
    }

    public AmountEvaluation.Outcome outcome() {
        return AmountEvaluation.Outcome.valueOf(evidence.amountEvaluation().outcome());
    }
}
