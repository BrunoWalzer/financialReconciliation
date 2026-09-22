package dev.fincore.matching.domain.predicate;

import static dev.fincore.matching.domain.EvaluationContextFixture.context;
import static dev.fincore.matching.domain.FinancialRecordFixture.aRecord;
import static org.assertj.core.api.Assertions.assertThat;

import dev.fincore.evidence.domain.Direction;
import dev.fincore.evidence.domain.FinancialRecord;
import dev.fincore.matching.domain.EvaluationContext;
import dev.fincore.matching.domain.PredicateResult;
import dev.fincore.matching.domain.RecordPair;
import org.junit.jupiter.api.Test;

class CompatibleDirectionPredicateTest {

    private final CompatibleDirectionPredicate predicate = new CompatibleDirectionPredicate();
    private final EvaluationContext context = context().build();

    @Test
    void devePassarQuandoMesmaDirecao() {
        FinancialRecord left = aRecord().direction(Direction.CREDIT).build();
        FinancialRecord right = aRecord().direction(Direction.CREDIT).build();

        assertThat(predicate.test(new RecordPair(left, right), context).passed()).isTrue();
    }

    @Test
    void deveFalharQuandoDirecoesDiferentes() {
        FinancialRecord left = aRecord().direction(Direction.CREDIT).build();
        FinancialRecord right = aRecord().direction(Direction.DEBIT).build();

        PredicateResult result = predicate.test(new RecordPair(left, right), context);

        assertThat(result.passed()).isFalse();
    }
}
