package dev.fincore.matching.domain.predicate;

import static dev.fincore.matching.domain.EvaluationContextFixture.context;
import static dev.fincore.matching.domain.FinancialRecordFixture.aRecord;
import static org.assertj.core.api.Assertions.assertThat;

import dev.fincore.evidence.domain.FinancialRecord;
import dev.fincore.matching.domain.EvaluationContext;
import dev.fincore.matching.domain.RecordPair;
import dev.fincore.matching.domain.RunConfigSnapshotFixture;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Janela padrão do fixture: 1–3 dias (qualquer meio), CREDIT_CARD 1–31 dias. */
class WithinSettlementWindowPredicateTest {

    private final WithinSettlementWindowPredicate predicate = new WithinSettlementWindowPredicate();

    @Test
    void devePassarNoLimiteExatoDeMinDays() {
        FinancialRecord left = aRecord().businessDate(LocalDate.of(2026, 9, 10)).paymentMethod("CREDIT_CARD").build();
        FinancialRecord right = aRecord().businessDate(LocalDate.of(2026, 9, 11)).build(); // +1 dia

        assertThat(predicate.test(new RecordPair(left, right), context().build()).passed()).isTrue();
    }

    @Test
    void devePassarNoLimiteExatoDeMaxDays() {
        FinancialRecord left = aRecord().businessDate(LocalDate.of(2026, 9, 10)).paymentMethod("CREDIT_CARD").build();
        FinancialRecord right = aRecord().businessDate(LocalDate.of(2026, 10, 11)).build(); // +31 dias

        assertThat(predicate.test(new RecordPair(left, right), context().build()).passed()).isTrue();
    }

    @Test
    void deveFalharUmDiaAntesDoMinDays() {
        FinancialRecord left = aRecord().businessDate(LocalDate.of(2026, 9, 10)).paymentMethod("CREDIT_CARD").build();
        FinancialRecord right = aRecord().businessDate(LocalDate.of(2026, 9, 10)).build(); // mesmo dia, +0

        assertThat(predicate.test(new RecordPair(left, right), context().build()).passed()).isFalse();
    }

    @Test
    void deveFalharUmDiaDepoisDoMaxDays() {
        FinancialRecord left = aRecord().businessDate(LocalDate.of(2026, 9, 10)).paymentMethod("CREDIT_CARD").build();
        FinancialRecord right = aRecord().businessDate(LocalDate.of(2026, 10, 12)).build(); // +32 dias

        assertThat(predicate.test(new RecordPair(left, right), context().build()).passed()).isFalse();
    }

    @Test
    void deveUsarJanelaPadraoQuandoMeioDePagamentoNaoTemJanelaEspecifica() {
        FinancialRecord left = aRecord().businessDate(LocalDate.of(2026, 9, 10)).paymentMethod("PIX").build();
        FinancialRecord right = aRecord().businessDate(LocalDate.of(2026, 9, 13)).build(); // +3 dias, limite da janela padrão

        assertThat(predicate.test(new RecordPair(left, right), context().build()).passed()).isTrue();
    }

    @Test
    void deveFalharQuandoNenhumaJanelaEstaConfigurada() {
        FinancialRecord left = aRecord().businessDate(LocalDate.of(2026, 9, 10)).paymentMethod("PIX").build();
        FinancialRecord right = aRecord().businessDate(LocalDate.of(2026, 9, 11)).build();
        EvaluationContext ctx = context()
                .config(RunConfigSnapshotFixture.config().settlementWindows(List.of()).build())
                .build();

        assertThat(predicate.test(new RecordPair(left, right), ctx).passed()).isFalse();
    }

    @Test
    void naoDeveDependerDeDistanciaAbsolutaSimetrica() {
        // A janela é sempre "right vem depois de left" — right ANTES de left nunca é válido,
        // mesmo que a distância absoluta caiba na janela.
        FinancialRecord left = aRecord().businessDate(LocalDate.of(2026, 9, 10)).paymentMethod("CREDIT_CARD").build();
        FinancialRecord right = aRecord().businessDate(LocalDate.of(2026, 9, 9)).build(); // 1 dia ANTES

        assertThat(predicate.test(new RecordPair(left, right), context().build()).passed()).isFalse();
    }
}
