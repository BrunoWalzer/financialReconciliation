package dev.fincore.matching.domain.predicate;

import static dev.fincore.matching.domain.EvaluationContextFixture.context;
import static dev.fincore.matching.domain.FinancialRecordFixture.aRecord;
import static org.assertj.core.api.Assertions.assertThat;

import dev.fincore.evidence.domain.FinancialRecord;
import dev.fincore.matching.domain.RecordPair;
import org.junit.jupiter.api.Test;

/**
 * Predicado obrigatório e não negociável do Nível B (Domain §12.4, novo na v1.1). Testado
 * explicitamente nos três casos que o Implementation Plan M9 pede: documento nos dois lados,
 * ausente à esquerda, ausente à direita — nenhum dos dois últimos produz candidato B.
 */
class BothHaveCounterpartyDocumentPredicateTest {

    private final BothHaveCounterpartyDocumentPredicate predicate = new BothHaveCounterpartyDocumentPredicate();

    @Test
    void devePassarQuandoAmbosTemDocumentoCompativel() {
        FinancialRecord left = aRecord().counterpartyDocument("11144477735").build();
        FinancialRecord right = aRecord().counterpartyDocument("11144477735").build();

        assertThat(predicate.test(new RecordPair(left, right), context().build()).passed()).isTrue();
    }

    @Test
    void naoDeveProduzirCandidatoQuandoDocumentoAusenteADireita() {
        FinancialRecord left = aRecord().counterpartyDocument("11144477735").build();
        FinancialRecord right = aRecord().counterpartyDocument(null).build();

        assertThat(predicate.test(new RecordPair(left, right), context().build()).passed()).isFalse();
    }

    @Test
    void naoDeveProduzirCandidatoQuandoDocumentoAusenteAEsquerda() {
        FinancialRecord left = aRecord().counterpartyDocument(null).build();
        FinancialRecord right = aRecord().counterpartyDocument("11144477735").build();

        assertThat(predicate.test(new RecordPair(left, right), context().build()).passed()).isFalse();
    }

    @Test
    void naoDeveProduzirCandidatoQuandoDocumentosDiferentes() {
        FinancialRecord left = aRecord().counterpartyDocument("11144477735").build();
        FinancialRecord right = aRecord().counterpartyDocument("11222333000181").build();

        assertThat(predicate.test(new RecordPair(left, right), context().build()).passed()).isFalse();
    }
}
