package dev.fincore.configuration.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.fincore.shared.identifier.Uuid7;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** {@link FeeRule} — o que define "cobrança indevida" (Domain §27.2). */
class FeeRuleTest {

    @Test
    void deveCriarAtivaComVersaoZero() {
        UUID sourceId = Uuid7.generate();
        FeeRule rule = new FeeRule(sourceId, "CREDIT_CARD", 250, 0L, RoundingMode.HALF_UP);

        assertThat(rule.id()).isNotNull();
        assertThat(rule.sourceId()).isEqualTo(sourceId);
        assertThat(rule.paymentMethod()).isEqualTo("CREDIT_CARD");
        assertThat(rule.percentageBp()).isEqualTo(250);
        assertThat(rule.active()).isTrue();
        assertThat(rule.version()).isZero();
    }

    @Test
    void devePermitirPaymentMethodNuloComoQualquerMeio() {
        FeeRule rule = new FeeRule(Uuid7.generate(), null, 100, 0L, RoundingMode.HALF_UP);

        assertThat(rule.paymentMethod()).isNull();
    }

    @Test
    void deveRejeitarPercentageBpAbaixoDeZero() {
        assertThatThrownBy(() -> new FeeRule(Uuid7.generate(), null, -1, 0L, RoundingMode.HALF_UP))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("percentageBp");
    }

    @Test
    void deveRejeitarPercentageBpAcimaDoLimite() {
        assertThatThrownBy(() -> new FeeRule(Uuid7.generate(), null, 10_001, 0L, RoundingMode.HALF_UP))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("percentageBp");
    }

    @Test
    void deveAceitarLimitesDoIntervaloDePercentageBp() {
        FeeRule zero = new FeeRule(Uuid7.generate(), null, 0, 0L, RoundingMode.HALF_UP);
        FeeRule max = new FeeRule(Uuid7.generate(), null, 10_000, 0L, RoundingMode.HALF_UP);

        assertThat(zero.percentageBp()).isZero();
        assertThat(max.percentageBp()).isEqualTo(10_000);
    }

    @Test
    void deveRejeitarFixedAmountMinorNegativo() {
        assertThatThrownBy(() -> new FeeRule(Uuid7.generate(), null, 100, -1L, RoundingMode.HALF_UP))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fixedAmountMinor");
    }

    @Test
    void deveDesativar() {
        FeeRule rule = new FeeRule(Uuid7.generate(), null, 100, 0L, RoundingMode.HALF_UP);

        rule.deactivate();

        assertThat(rule.active()).isFalse();
    }

    @Test
    void deveAtualizarTermos() {
        FeeRule rule = new FeeRule(Uuid7.generate(), null, 100, 0L, RoundingMode.HALF_UP);

        rule.update(500, 25L, RoundingMode.DOWN);

        assertThat(rule.percentageBp()).isEqualTo(500);
        assertThat(rule.fixedAmountMinor()).isEqualTo(25L);
        assertThat(rule.roundingMode()).isEqualTo(RoundingMode.DOWN);
    }

    @Test
    void deveRejeitarAtualizacaoComPercentageBpForaDoIntervalo() {
        FeeRule rule = new FeeRule(Uuid7.generate(), null, 100, 0L, RoundingMode.HALF_UP);

        assertThatThrownBy(() -> rule.update(10_001, 0L, RoundingMode.HALF_UP))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
