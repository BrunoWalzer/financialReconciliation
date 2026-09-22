package dev.fincore.matching.domain;

import static dev.fincore.matching.domain.FinancialRecordFixture.aRecord;
import static org.assertj.core.api.Assertions.assertThat;

import dev.fincore.evidence.domain.FinancialRecord;
import dev.fincore.evidence.domain.RecordType;
import dev.fincore.shared.configuration.RunConfigSnapshot;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * O único lugar de aritmética de conciliação (TDS 8.3) — os quatro {@code outcome} e a
 * precedência de resolução de taxa (Domain §13.2).
 */
class AmountEvaluationTest {

    @Test
    void c1ReconciledExactSemTaxa() {
        FinancialRecord left = aRecord().grossAmount(50_000).build();
        FinancialRecord right = aRecord().netAmount(null).grossAmount(50_000).build();
        RunConfigSnapshot config = RunConfigSnapshotFixture.config().build();

        AmountEvaluation evaluation = AmountEvaluation.evaluate(left, right, List.of(), config);

        assertThat(evaluation.outcome()).isEqualTo(AmountEvaluation.Outcome.RECONCILED_EXACT);
        assertThat(evaluation.feeApplied().amountMinor()).isZero();
        assertThat(evaluation.residual().amountMinor()).isZero();
        assertThat(evaluation.toleranceAbsorbed().amountMinor()).isZero();
    }

    @Test
    void c2ReconciledWithFeeResiduoZero() {
        FinancialRecord left = aRecord().grossAmount(50_000).build();
        FinancialRecord right = aRecord().declaredFeeAmount(1_280L).netAmount(48_720L).build();
        RunConfigSnapshot config = RunConfigSnapshotFixture.config().build();

        AmountEvaluation evaluation = AmountEvaluation.evaluate(left, right, List.of(), config);

        assertThat(evaluation.outcome()).isEqualTo(AmountEvaluation.Outcome.RECONCILED_WITH_FEE);
        assertThat(evaluation.feeSource()).isEqualTo(AmountEvaluation.FeeSource.DECLARED);
        assertThat(evaluation.feeApplied().amountMinor()).isEqualTo(1_280);
        assertThat(evaluation.residual().amountMinor()).isZero();
    }

    @Test
    void c3ReconciledWithinToleranceResiduoIgualAoLimiteAbsorvido() {
        // tolerância padrão do fixture: 2 minor units.
        FinancialRecord left = aRecord().grossAmount(50_000).build();
        FinancialRecord right = aRecord().declaredFeeAmount(1_280L).netAmount(48_719L).build(); // 1 a menos
        RunConfigSnapshot config = RunConfigSnapshotFixture.config().build();

        AmountEvaluation evaluation = AmountEvaluation.evaluate(left, right, List.of(), config);

        assertThat(evaluation.outcome()).isEqualTo(AmountEvaluation.Outcome.RECONCILED_WITHIN_TOLERANCE);
        assertThat(evaluation.residual().amountMinor()).isEqualTo(-1);
        assertThat(evaluation.toleranceAbsorbed().amountMinor()).isEqualTo(1);
    }

    @Test
    void toleranciaMaisUmCentavoForaEhDivergencia() {
        FinancialRecord left = aRecord().grossAmount(50_000).build();
        FinancialRecord right = aRecord().declaredFeeAmount(1_280L).netAmount(48_717L).build(); // 3 a menos, tolerância é 2
        RunConfigSnapshot config = RunConfigSnapshotFixture.config().build();

        AmountEvaluation evaluation = AmountEvaluation.evaluate(left, right, List.of(), config);

        assertThat(evaluation.outcome()).isEqualTo(AmountEvaluation.Outcome.PAIRED_WITH_DIVERGENCE);
        assertThat(evaluation.toleranceAbsorbed().amountMinor()).isZero();
    }

    @Test
    void c4PairedWithDivergenceComDesvioDeTaxaCalculado() {
        FinancialRecord left = aRecord().grossAmount(50_000).paymentMethod("CREDIT_CARD").build();
        // taxa declarada de 5000 (bem acima da esperada pela regra, 1280), e o líquido
        // declarado nem sequer bate com bruto - taxa declarada: 50000 - 5000 = 45000
        // esperado, mas o líquido observado é 44000 -> resíduo de -1000, fora da tolerância.
        FinancialRecord right = aRecord().sourceId(RunConfigSnapshotFixture.ACQUIRER_SETTLEMENT_ID)
                .declaredFeeAmount(5_000L).netAmount(44_000L).paymentMethod("CREDIT_CARD").build();
        RunConfigSnapshot config = RunConfigSnapshotFixture.config()
                .feeRule("ACQUIRER_SETTLEMENT", "CREDIT_CARD", 256, 0, "HALF_UP") // 2,56% de 50000 = 1280
                .build();

        AmountEvaluation evaluation = AmountEvaluation.evaluate(left, right, List.of(), config);

        assertThat(evaluation.outcome()).isEqualTo(AmountEvaluation.Outcome.PAIRED_WITH_DIVERGENCE);
        assertThat(evaluation.expectedFeeByRule().amountMinor()).isEqualTo(1_280);
        assertThat(evaluation.feeRuleDeviation().amountMinor()).isEqualTo(5_000 - 1_280);
    }

    @Test
    void expectedFeeByRuleApareceMesmoQuandoTaxaDeclaradaFoiUsada() {
        // TDS 11.8: "expectedFeeByRuleMinor aparece mesmo quando a taxa declarada foi usada".
        FinancialRecord left = aRecord().grossAmount(50_000).paymentMethod("CREDIT_CARD").build();
        FinancialRecord right = aRecord().sourceId(RunConfigSnapshotFixture.ACQUIRER_SETTLEMENT_ID)
                .declaredFeeAmount(1_280L).netAmount(48_720L).paymentMethod("CREDIT_CARD").build();
        RunConfigSnapshot config = RunConfigSnapshotFixture.config()
                .feeRule("ACQUIRER_SETTLEMENT", "CREDIT_CARD", 256, 0, "HALF_UP")
                .build();

        AmountEvaluation evaluation = AmountEvaluation.evaluate(left, right, List.of(), config);

        assertThat(evaluation.feeSource()).isEqualTo(AmountEvaluation.FeeSource.DECLARED);
        assertThat(evaluation.expectedFeeByRule()).isNotNull();
        assertThat(evaluation.expectedFeeByRule().amountMinor()).isEqualTo(1_280);
        assertThat(evaluation.feeRuleDeviation().amountMinor()).isZero();
    }

    @Test
    void semTaxaDeclaradaSemComponenteSemRegraFeeSourceNone() {
        FinancialRecord left = aRecord().grossAmount(50_000).build();
        FinancialRecord right = aRecord().netAmount(null).grossAmount(50_000).build();
        RunConfigSnapshot config = RunConfigSnapshotFixture.config().build(); // sem fee_rule seedado, igual ao M3 real

        AmountEvaluation evaluation = AmountEvaluation.evaluate(left, right, List.of(), config);

        assertThat(evaluation.feeSource()).isEqualTo(AmountEvaluation.FeeSource.NONE);
        assertThat(evaluation.expectedFeeByRule()).isNull();
        assertThat(evaluation.feeRuleDeviation()).isNull();
    }

    @Test
    void taxaMaiorQueOBrutoPreservaLiquidoNegativo() {
        // C20 — líquido negativo é preservado, nunca corrigido silenciosamente.
        FinancialRecord left = aRecord().grossAmount(1_000).build();
        FinancialRecord right = aRecord().declaredFeeAmount(1_500L).netAmount(-500L).build();
        RunConfigSnapshot config = RunConfigSnapshotFixture.config().build();

        AmountEvaluation evaluation = AmountEvaluation.evaluate(left, right, List.of(), config);

        assertThat(evaluation.netExpected().amountMinor()).isEqualTo(1_000 - 1_500);
        assertThat(evaluation.observed().amountMinor()).isEqualTo(-500);
        assertThat(evaluation.outcome()).isEqualTo(AmountEvaluation.Outcome.RECONCILED_WITH_FEE);
    }

    @Test
    void taxaDeclaradaZeroContaComoSemTaxaParaOOutcomeExato() {
        FinancialRecord left = aRecord().grossAmount(50_000).build();
        FinancialRecord right = aRecord().declaredFeeAmount(0L).netAmount(50_000L).build();
        RunConfigSnapshot config = RunConfigSnapshotFixture.config().build();

        AmountEvaluation evaluation = AmountEvaluation.evaluate(left, right, List.of(), config);

        assertThat(evaluation.outcome()).isEqualTo(AmountEvaluation.Outcome.RECONCILED_EXACT);
    }

    @Test
    void resolveFeeUsaRegistroComponenteQuandoNaoHaTaxaDeclarada() {
        String correlationKey = "NSU-COMPONENT";
        FinancialRecord left = aRecord().correlationKey(correlationKey).grossAmount(50_000).build();
        FinancialRecord right = aRecord().correlationKey(correlationKey).netAmount(null).declaredFeeAmount(null).grossAmount(49_000).build();
        FinancialRecord feeComponent = aRecord().correlationKey(correlationKey).recordType(RecordType.FEE).grossAmount(1_000).build();
        RunConfigSnapshot config = RunConfigSnapshotFixture.config().build();

        AmountEvaluation evaluation = AmountEvaluation.evaluate(left, right, List.of(feeComponent), config);

        assertThat(evaluation.feeSource()).isEqualTo(AmountEvaluation.FeeSource.COMPONENT_RECORD);
        assertThat(evaluation.feeApplied().amountMinor()).isEqualTo(1_000);
        assertThat(evaluation.outcome()).isEqualTo(AmountEvaluation.Outcome.RECONCILED_WITH_FEE);
    }

    @Test
    void resolveFeeUsaRegraQuandoNaoHaDeclaradaNemComponente() {
        FinancialRecord left = aRecord().grossAmount(50_000).paymentMethod("CREDIT_CARD").build();
        FinancialRecord right = aRecord().sourceId(RunConfigSnapshotFixture.ACQUIRER_SETTLEMENT_ID)
                .declaredFeeAmount(null).netAmount(null).grossAmount(48_720).paymentMethod("CREDIT_CARD").build();
        RunConfigSnapshot config = RunConfigSnapshotFixture.config()
                .feeRule("ACQUIRER_SETTLEMENT", "CREDIT_CARD", 256, 0, "HALF_UP")
                .build();

        AmountEvaluation evaluation = AmountEvaluation.evaluate(left, right, List.of(), config);

        assertThat(evaluation.feeSource()).isEqualTo(AmountEvaluation.FeeSource.RULE);
        assertThat(evaluation.feeApplied().amountMinor()).isEqualTo(1_280);
    }
}
