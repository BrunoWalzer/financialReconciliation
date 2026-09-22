package dev.fincore.matching.domain;

import dev.fincore.evidence.domain.FinancialRecord;
import dev.fincore.evidence.domain.RecordType;
import dev.fincore.shared.configuration.RunConfigSnapshot;
import dev.fincore.shared.configuration.RunConfigSnapshot.FeeRuleSnapshot;
import dev.fincore.shared.money.Currency;
import dev.fincore.shared.money.Money;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/**
 * O único lugar de aritmética de conciliação (TDS 8.3). Toda a aritmética passa por
 * {@link Money} — nenhum {@code double}/{@code float}, nenhum cálculo em SQL.
 *
 * <p>{@code resolveFee} obedece à precedência do Domain §13.2: taxa declarada na linha →
 * registro componente com a mesma chave de correlação → regra configurada (só para
 * <b>verificar</b>) → nenhuma. {@code expectedFeeByRuleMinor} é calculado e registrado
 * <b>mesmo quando a taxa declarada foi usada</b> (TDS 11.8) — é o que permite detectar uma
 * cobrança fora do contrato mesmo quando a fonte declara um valor plausível.
 */
public record AmountEvaluation(
        Currency currency,
        Money grossExpected,
        FeeSource feeSource,
        Money feeApplied,
        Money expectedFeeByRule,
        Money feeRuleDeviation,
        Money netExpected,
        Money observed,
        Money residual,
        Money toleranceLimit,
        Money toleranceAbsorbed,
        Outcome outcome) {

    public enum FeeSource {
        DECLARED, COMPONENT_RECORD, RULE, NONE
    }

    public enum Outcome {
        RECONCILED_EXACT, RECONCILED_WITH_FEE, RECONCILED_WITHIN_TOLERANCE, PAIRED_WITH_DIVERGENCE
    }

    public static AmountEvaluation evaluate(
            FinancialRecord principalLeft, FinancialRecord principalRight,
            List<FinancialRecord> components, RunConfigSnapshot config) {

        Currency currency = principalLeft.currency();
        Money gross = principalLeft.grossAmount();

        Optional<FeeRuleSnapshot> feeRule = findFeeRule(principalLeft, principalRight, config);
        Money expectedFeeByRule = feeRule.map(rule -> expectedFee(gross, rule)).orElse(null);

        FeeResolution feeResolution = resolveFee(principalLeft, principalRight, components, expectedFeeByRule);
        Money fee = feeResolution.fee();
        // Registrado mesmo quando a taxa declarada foi usada (TDS 11.8) — é o que detecta
        // uma cobrança fora do contrato mesmo quando a fonte declara um valor plausível.
        Money feeRuleDeviation = expectedFeeByRule == null ? null : fee.minus(expectedFeeByRule);

        Money netExpected = gross.minus(fee);
        Money observed = principalRight.netAmount() != null ? principalRight.netAmount() : principalRight.grossAmount();
        Money residual = observed.minus(netExpected);
        Money toleranceLimit = new Money(config.tolerance().absoluteMinor(), Currency.valueOf(config.tolerance().currency()));

        Outcome outcome;
        Money toleranceAbsorbed;
        if (residual.isZero() && fee.isZero()) {
            outcome = Outcome.RECONCILED_EXACT;
            toleranceAbsorbed = new Money(0, currency);
        } else if (residual.isZero()) {
            outcome = Outcome.RECONCILED_WITH_FEE;
            toleranceAbsorbed = new Money(0, currency);
        } else if (residual.isWithin(toleranceLimit)) {
            outcome = Outcome.RECONCILED_WITHIN_TOLERANCE;
            toleranceAbsorbed = residual.abs();
        } else {
            outcome = Outcome.PAIRED_WITH_DIVERGENCE;
            toleranceAbsorbed = new Money(0, currency);
        }

        return new AmountEvaluation(
                currency, gross, feeResolution.source(), fee, expectedFeeByRule, feeRuleDeviation,
                netExpected, observed, residual, toleranceLimit, toleranceAbsorbed, outcome);
    }

    private record FeeResolution(Money fee, FeeSource source) {
    }

    private static FeeResolution resolveFee(
            FinancialRecord principalLeft, FinancialRecord principalRight, List<FinancialRecord> components,
            Money expectedFeeByRule) {
        Currency currency = principalLeft.currency();

        if (principalRight.declaredFeeAmount() != null) {
            return new FeeResolution(principalRight.declaredFeeAmount(), FeeSource.DECLARED);
        }
        if (principalLeft.declaredFeeAmount() != null) {
            return new FeeResolution(principalLeft.declaredFeeAmount(), FeeSource.DECLARED);
        }

        String correlationKey = principalLeft.correlationKey() != null
                ? principalLeft.correlationKey() : principalRight.correlationKey();
        if (correlationKey != null) {
            Optional<FinancialRecord> component = components.stream()
                    .filter(r -> r.recordType() == RecordType.FEE)
                    .filter(r -> correlationKey.equals(r.correlationKey()))
                    .findFirst();
            if (component.isPresent()) {
                return new FeeResolution(component.get().grossAmount(), FeeSource.COMPONENT_RECORD);
            }
        }

        // Regra configurada, na ausência de declarada/componente (Domain §13.2): é a última
        // posição da precedência, nunca sobrepõe uma taxa declarada — mas quando é a única
        // fonte disponível, ela é a taxa aplicada, não só uma verificação.
        if (expectedFeeByRule != null) {
            return new FeeResolution(expectedFeeByRule, FeeSource.RULE);
        }

        return new FeeResolution(new Money(0, currency), FeeSource.NONE);
    }

    private static Optional<FeeRuleSnapshot> findFeeRule(
            FinancialRecord principalLeft, FinancialRecord principalRight, RunConfigSnapshot config) {
        String rightCode = sourceCodeOf(principalRight.sourceId(), config);
        String leftCode = sourceCodeOf(principalLeft.sourceId(), config);
        String rightPaymentMethod = principalRight.paymentMethod();
        String leftPaymentMethod = principalLeft.paymentMethod();

        return firstMatch(config, rightCode, rightPaymentMethod)
                .or(() -> firstMatch(config, leftCode, leftPaymentMethod));
    }

    private static Optional<FeeRuleSnapshot> firstMatch(RunConfigSnapshot config, String sourceCode, String paymentMethod) {
        if (sourceCode == null) {
            return Optional.empty();
        }
        Optional<FeeRuleSnapshot> specific = config.feeRules().stream()
                .filter(r -> r.sourceCode().equals(sourceCode))
                .filter(r -> paymentMethod != null && paymentMethod.equals(r.paymentMethod()))
                .findFirst();
        if (specific.isPresent()) {
            return specific;
        }
        return config.feeRules().stream()
                .filter(r -> r.sourceCode().equals(sourceCode))
                .filter(r -> r.paymentMethod() == null)
                .findFirst();
    }

    private static String sourceCodeOf(java.util.UUID sourceId, RunConfigSnapshot config) {
        return config.sources().stream()
                .filter(s -> s.id().equals(sourceId))
                .map(RunConfigSnapshot.SourceSnapshot::code)
                .findFirst()
                .orElse(null);
    }

    /** Formula exata da TDS 8.2 — escala intermediária 6, arredondamento final da regra. */
    private static Money expectedFee(Money gross, FeeRuleSnapshot rule) {
        Money percentagePart = gross.percentageOf(rule.percentageBp(), RoundingMode.valueOf(rule.roundingMode()));
        return new Money(percentagePart.amountMinor() + rule.fixedAmountMinor(), gross.currency());
    }
}
