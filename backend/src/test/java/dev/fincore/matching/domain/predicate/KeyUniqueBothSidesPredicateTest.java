package dev.fincore.matching.domain.predicate;

import static dev.fincore.matching.domain.EvaluationContextFixture.context;
import static dev.fincore.matching.domain.FinancialRecordFixture.aRecord;
import static org.assertj.core.api.Assertions.assertThat;

import dev.fincore.evidence.domain.FinancialRecord;
import dev.fincore.evidence.domain.RecordIntegrityFlagType;
import dev.fincore.matching.domain.RecordPair;
import org.junit.jupiter.api.Test;

class KeyUniqueBothSidesPredicateTest {

    private final KeyUniqueBothSidesPredicate predicate = new KeyUniqueBothSidesPredicate();

    @Test
    void devePassarSemFlagDeChaveDuplicada() {
        FinancialRecord left = aRecord().correlationKey("NSU1").build();
        FinancialRecord right = aRecord().correlationKey("NSU1").build();

        assertThat(predicate.test(new RecordPair(left, right), context().build()).passed()).isTrue();
    }

    @Test
    void deveFalharQuandoLadoEsquerdoTemFlagDeChaveDuplicada() {
        FinancialRecord left = aRecord().correlationKey("NSU1").build();
        FinancialRecord right = aRecord().correlationKey("NSU1").build();
        var ctx = context().flagged(left.id(), RecordIntegrityFlagType.DUPLICATE_CORRELATION_KEY).build();

        assertThat(predicate.test(new RecordPair(left, right), ctx).passed()).isFalse();
    }

    @Test
    void naoDeveConfundirComOutrasFlags() {
        FinancialRecord left = aRecord().correlationKey("NSU1").build();
        FinancialRecord right = aRecord().correlationKey("NSU1").build();
        var ctx = context().flagged(left.id(), RecordIntegrityFlagType.SOURCE_INTERNAL_INCONSISTENCY).build();

        assertThat(predicate.test(new RecordPair(left, right), ctx).passed()).isTrue();
    }
}
