package dev.fincore.matching.domain.predicate;

import static dev.fincore.matching.domain.EvaluationContextFixture.context;
import static dev.fincore.matching.domain.FinancialRecordFixture.aRecord;
import static org.assertj.core.api.Assertions.assertThat;

import dev.fincore.evidence.domain.FinancialRecord;
import dev.fincore.matching.domain.EvaluationContext;
import dev.fincore.matching.domain.PredicateResult;
import dev.fincore.matching.domain.RecordPair;
import org.junit.jupiter.api.Test;

class SameCurrencyPredicateTest {

    private final SameCurrencyPredicate predicate = new SameCurrencyPredicate();
    private final EvaluationContext context = context().build();

    @Test
    void devePassarQuandoMesmaMoeda() {
        FinancialRecord left = aRecord().build();
        FinancialRecord right = aRecord().build();

        PredicateResult result = predicate.test(new RecordPair(left, right), context);

        assertThat(result.passed()).isTrue();
        assertThat(result.name()).isEqualTo("SAME_CURRENCY");
    }

    // BRL é a única moeda funcional do MVP (Currency.USD só existe para provar
    // CurrencyMismatchException em Money) — não há como construir um FinancialRecord com
    // moeda diferente de BRL sem violar a constraint do próprio domínio (M4), então este
    // predicado é, na prática, sempre verdadeiro no MVP. O teste documenta essa realidade em
    // vez de fabricar uma moeda que o sistema nunca aceitaria de verdade.
}
