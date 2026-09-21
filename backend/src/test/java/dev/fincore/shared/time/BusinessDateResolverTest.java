package dev.fincore.shared.time;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/**
 * {@link BusinessDateResolver} — a única base de comparação temporal do motor (Domain
 * §5.5; TDS 8.4). Função pura, sem relógio.
 */
class BusinessDateResolverTest {

    @Test
    void deveConverterInstanteParaDataDeNegocioNoFusoDaFonte() {
        Instant sourceTimestamp = Instant.parse("2026-09-11T02:50:00Z"); // 2026-09-10T23:50-03:00
        ZoneId saoPaulo = ZoneId.of("America/Sao_Paulo");

        LocalDate businessDate = BusinessDateResolver.resolve(sourceTimestamp, saoPaulo);

        assertThat(businessDate).isEqualTo(LocalDate.of(2026, 9, 10));
    }

    @Test
    void oMesmoInstanteComFusoDiferenteProduzDataDiferente() {
        Instant sourceTimestamp = Instant.parse("2026-09-11T02:50:00Z");

        LocalDate emSaoPaulo = BusinessDateResolver.resolve(sourceTimestamp, ZoneId.of("America/Sao_Paulo"));
        LocalDate emTokyo = BusinessDateResolver.resolve(sourceTimestamp, ZoneId.of("Asia/Tokyo"));

        assertThat(emSaoPaulo).isEqualTo(LocalDate.of(2026, 9, 10));
        assertThat(emTokyo).isEqualTo(LocalDate.of(2026, 9, 11));
        assertThat(emSaoPaulo).isNotEqualTo(emTokyo);
    }

    @Test
    void entradaSoComDataPreservaAData() {
        LocalDate dateOnly = LocalDate.of(2026, 9, 11);

        assertThat(BusinessDateResolver.resolve(dateOnly)).isEqualTo(dateOnly);
    }

    @Test
    void deveRejeitarInstanteNulo() {
        assertThatThrownBy(() -> BusinessDateResolver.resolve(null, ZoneId.of("America/Sao_Paulo")))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void deveRejeitarFusoNulo() {
        assertThatThrownBy(() -> BusinessDateResolver.resolve(Instant.now(), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void deveRejeitarDataOnlyNula() {
        assertThatThrownBy(() -> BusinessDateResolver.resolve((LocalDate) null))
                .isInstanceOf(NullPointerException.class);
    }
}
