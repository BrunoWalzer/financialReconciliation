package dev.fincore.matching.domain.predicate;

import static dev.fincore.matching.domain.EvaluationContextFixture.context;
import static dev.fincore.matching.domain.FinancialRecordFixture.aRecord;
import static org.assertj.core.api.Assertions.assertThat;

import dev.fincore.evidence.domain.FinancialRecord;
import dev.fincore.evidence.domain.RecordIntegrityFlagType;
import dev.fincore.matching.domain.RecordPair;
import org.junit.jupiter.api.Test;

class NoUnresolvedIntegrityFlagPredicateTest {

    private final NoUnresolvedIntegrityFlagPredicate predicate = new NoUnresolvedIntegrityFlagPredicate();

    @Test
    void devePassarSemFlags() {
        FinancialRecord left = aRecord().build();
        FinancialRecord right = aRecord().build();

        assertThat(predicate.test(new RecordPair(left, right), context().build()).passed()).isTrue();
    }

    @Test
    void deveFalharQuandoQualquerLadoTemFlagAberta() {
        FinancialRecord left = aRecord().build();
        FinancialRecord right = aRecord().build();
        var ctx = context().flagged(right.id(), RecordIntegrityFlagType.POSSIBLE_DUPLICATE).build();

        assertThat(predicate.test(new RecordPair(left, right), ctx).passed()).isFalse();
    }
}
