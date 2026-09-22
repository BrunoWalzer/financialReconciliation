package dev.fincore.matching.domain;

import java.util.List;
import java.util.UUID;

/**
 * O que explica uma decisão automática, no formato do TDS 11.8 — {@code rule = "LEVEL_A"}
 * sozinho não seria suficiente. Serializado como JSON (JSONB em {@code match.evidence}) pela
 * camada de persistência (matching.infrastructure); este tipo não sabe nada de Jackson nem
 * de banco.
 *
 * <p>Sem {@code runId}: este milestone não implementa {@code ReconciliationRun} (M11) — o
 * campo do exemplo do TDS pressupõe uma execução que ainda não existe. Omitido de propósito,
 * não esquecido; ver relatório do M9, seção Decisões.
 */
public record MatchEvidence(
        String ruleId,
        int ruleVersion,
        String ruleSetVersion,
        List<PredicateResult> predicates,
        AmountEvaluationEvidence amountEvaluation,
        List<UUID> components) {

    public MatchEvidence {
        predicates = List.copyOf(predicates);
        components = List.copyOf(components);
    }

    public record AmountEvaluationEvidence(
            String currency,
            long grossExpectedMinor,
            String feeSource,
            long feeAppliedMinor,
            Long expectedFeeByRuleMinor,
            Long feeRuleDeviationMinor,
            long netExpectedMinor,
            long observedMinor,
            long residualMinor,
            long toleranceLimitMinor,
            long toleranceAbsorbedMinor,
            String outcome) {

        public static AmountEvaluationEvidence from(AmountEvaluation evaluation) {
            return new AmountEvaluationEvidence(
                    evaluation.currency().name(),
                    evaluation.grossExpected().amountMinor(),
                    evaluation.feeSource().name(),
                    evaluation.feeApplied().amountMinor(),
                    evaluation.expectedFeeByRule() == null ? null : evaluation.expectedFeeByRule().amountMinor(),
                    evaluation.feeRuleDeviation() == null ? null : evaluation.feeRuleDeviation().amountMinor(),
                    evaluation.netExpected().amountMinor(),
                    evaluation.observed().amountMinor(),
                    evaluation.residual().amountMinor(),
                    evaluation.toleranceLimit().amountMinor(),
                    evaluation.toleranceAbsorbed().amountMinor(),
                    evaluation.outcome().name());
        }
    }
}
