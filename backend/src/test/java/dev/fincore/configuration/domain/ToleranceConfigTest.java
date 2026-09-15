package dev.fincore.configuration.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.fincore.shared.identifier.Uuid7;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * {@link ToleranceConfig} — "a operação mais sensível do sistema" (Domain §27.2). Sem FK
 * para {@code app_user}: {@code configuration} não depende de {@code identity}.
 */
class ToleranceConfigTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void deveCriarComIdentificadorGeradoEVersaoZero() {
        UUID sourcePairId = Uuid7.generate();
        ToleranceConfig config = new ToleranceConfig(sourcePairId, 2, "BRL", null, null, NOW);

        assertThat(config.id()).isNotNull();
        assertThat(config.sourcePairId()).isEqualTo(sourcePairId);
        assertThat(config.absoluteAmountMinor()).isEqualTo(2);
        assertThat(config.currency()).isEqualTo("BRL");
        assertThat(config.version()).isZero();
    }

    @Test
    void deveRejeitarValorNegativo() {
        assertThatThrownBy(() -> new ToleranceConfig(Uuid7.generate(), -1, "BRL", null, null, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negativo");
    }

    @Test
    void deveRejeitarMoedaEmBranco() {
        assertThatThrownBy(() -> new ToleranceConfig(Uuid7.generate(), 2, " ", null, null, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("currency");
    }

    @Test
    void deveAtualizarValoresEUpdatedAtSemAlterarVersaoEmMemoria() {
        UUID sourcePairId = Uuid7.generate();
        UUID actor = Uuid7.generate();
        ToleranceConfig config = new ToleranceConfig(sourcePairId, 2, "BRL", null, null, NOW);
        Instant later = NOW.plusSeconds(3600);

        config.update(10, "USD", 1000L, actor, later);

        assertThat(config.absoluteAmountMinor()).isEqualTo(10);
        assertThat(config.currency()).isEqualTo("USD");
        assertThat(config.aggregateAlertThresholdMinor()).isEqualTo(1000L);
        assertThat(config.updatedBy()).isEqualTo(actor);
        assertThat(config.updatedAt()).isEqualTo(later);
    }

    @Test
    void deveRejeitarAtualizacaoComValorNegativo() {
        ToleranceConfig config = new ToleranceConfig(Uuid7.generate(), 2, "BRL", null, null, NOW);

        assertThatThrownBy(() -> config.update(-5, "BRL", null, null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
