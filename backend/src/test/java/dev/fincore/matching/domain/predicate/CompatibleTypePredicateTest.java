package dev.fincore.matching.domain.predicate;

import static dev.fincore.matching.domain.EvaluationContextFixture.context;
import static dev.fincore.matching.domain.FinancialRecordFixture.aRecord;
import static org.assertj.core.api.Assertions.assertThat;

import dev.fincore.evidence.domain.FinancialRecord;
import dev.fincore.evidence.domain.RecordType;
import dev.fincore.matching.domain.EvaluationContext;
import dev.fincore.matching.domain.RecordPair;
import org.junit.jupiter.api.Test;

/** Venda↔liquidação, estorno↔estorno. Nunca venda↔estorno (TDS 11.3). */
class CompatibleTypePredicateTest {

    private final CompatibleTypePredicate predicate = new CompatibleTypePredicate();
    private final EvaluationContext context = context().build();

    @Test
    void devePassarParaVendaELiquidacao() {
        FinancialRecord left = aRecord().recordType(RecordType.SALE).build();
        FinancialRecord right = aRecord().recordType(RecordType.SETTLEMENT).build();

        assertThat(predicate.test(new RecordPair(left, right), context).passed()).isTrue();
    }

    @Test
    void devePassarParaEstornoEEstorno() {
        FinancialRecord left = aRecord().recordType(RecordType.REFUND).build();
        FinancialRecord right = aRecord().recordType(RecordType.REFUND).build();

        assertThat(predicate.test(new RecordPair(left, right), context).passed()).isTrue();
    }

    @Test
    void deveFalharParaVendaEEstorno() {
        // C11 — estorno nunca casa com venda; duas defesas (direção + tipo) barram o mesmo erro.
        FinancialRecord left = aRecord().recordType(RecordType.SALE).build();
        FinancialRecord right = aRecord().recordType(RecordType.REFUND).build();

        assertThat(predicate.test(new RecordPair(left, right), context).passed()).isFalse();
    }

    @Test
    void deveFalharParaDoisTiposIguaisNaoListadosComoCompativeis() {
        FinancialRecord left = aRecord().recordType(RecordType.SALE).build();
        FinancialRecord right = aRecord().recordType(RecordType.SALE).build();

        assertThat(predicate.test(new RecordPair(left, right), context).passed()).isFalse();
    }
}
