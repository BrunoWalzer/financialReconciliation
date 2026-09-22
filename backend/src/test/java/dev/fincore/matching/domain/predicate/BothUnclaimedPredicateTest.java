package dev.fincore.matching.domain.predicate;

import static dev.fincore.matching.domain.EvaluationContextFixture.context;
import static dev.fincore.matching.domain.FinancialRecordFixture.aRecord;
import static org.assertj.core.api.Assertions.assertThat;

import dev.fincore.evidence.domain.FinancialRecord;
import dev.fincore.matching.domain.RecordPair;
import org.junit.jupiter.api.Test;

class BothUnclaimedPredicateTest {

    private final BothUnclaimedPredicate predicate = new BothUnclaimedPredicate();

    @Test
    void devePassarQuandoNenhumReivindicado() {
        FinancialRecord left = aRecord().build();
        FinancialRecord right = aRecord().build();

        assertThat(predicate.test(new RecordPair(left, right), context().build()).passed()).isTrue();
    }

    @Test
    void deveFalharQuandoEsquerdoJaReivindicado() {
        FinancialRecord left = aRecord().build();
        FinancialRecord right = aRecord().build();

        boolean passed = predicate.test(new RecordPair(left, right), context().claimed(left.id()).build()).passed();

        assertThat(passed).isFalse();
    }

    @Test
    void deveFalharQuandoDireitoJaReivindicado() {
        FinancialRecord left = aRecord().build();
        FinancialRecord right = aRecord().build();

        boolean passed = predicate.test(new RecordPair(left, right), context().claimed(right.id()).build()).passed();

        assertThat(passed).isFalse();
    }
}
