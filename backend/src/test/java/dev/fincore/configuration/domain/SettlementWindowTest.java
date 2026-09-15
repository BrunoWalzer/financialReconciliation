package dev.fincore.configuration.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.fincore.shared.identifier.Uuid7;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** {@link SettlementWindow} — prazo esperado de liquidação por meio de pagamento (Domain D2; TDS 7.3). */
class SettlementWindowTest {

    @Test
    void deveCriarComVersaoZero() {
        UUID sourcePairId = Uuid7.generate();
        SettlementWindow window = new SettlementWindow(sourcePairId, null, 1, 3);

        assertThat(window.id()).isNotNull();
        assertThat(window.sourcePairId()).isEqualTo(sourcePairId);
        assertThat(window.paymentMethod()).isNull();
        assertThat(window.minDays()).isEqualTo(1);
        assertThat(window.maxDays()).isEqualTo(3);
        assertThat(window.version()).isZero();
    }

    @Test
    void deveRejeitarMinDaysNegativo() {
        assertThatThrownBy(() -> new SettlementWindow(Uuid7.generate(), null, -1, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minDays");
    }

    @Test
    void deveRejeitarMaxDaysMenorQueMinDays() {
        assertThatThrownBy(() -> new SettlementWindow(Uuid7.generate(), null, 5, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxDays");
    }

    @Test
    void deveAceitarMinDaysIgualAMaxDays() {
        SettlementWindow window = new SettlementWindow(Uuid7.generate(), "CREDIT_CARD", 3, 3);

        assertThat(window.minDays()).isEqualTo(3);
        assertThat(window.maxDays()).isEqualTo(3);
    }

    @Test
    void deveAtualizarPrazos() {
        SettlementWindow window = new SettlementWindow(Uuid7.generate(), "CREDIT_CARD", 1, 31);

        window.update(2, 10);

        assertThat(window.minDays()).isEqualTo(2);
        assertThat(window.maxDays()).isEqualTo(10);
    }

    @Test
    void deveRejeitarAtualizacaoInvalida() {
        SettlementWindow window = new SettlementWindow(Uuid7.generate(), "CREDIT_CARD", 1, 31);

        assertThatThrownBy(() -> window.update(10, 5))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
