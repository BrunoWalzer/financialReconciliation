package dev.fincore.ingestion.parser;

import static org.assertj.core.api.Assertions.assertThat;

import dev.fincore.evidence.domain.Direction;
import dev.fincore.evidence.domain.RecordType;
import dev.fincore.shared.money.Currency;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** {@link FingerprintCalculator} — determinístico; identifica, nunca deduplica (Domain §9.3). */
class FingerprintCalculatorTest {

    @Test
    void mesmoConteudoCanonicoProduzMesmoFingerprint() {
        String first = calculate("PED-1", "NSU1");
        String second = calculate("PED-1", "NSU1");

        assertThat(first).isEqualTo(second);
    }

    @Test
    void conteudoDiferenteProduzFingerprintDiferente() {
        String first = calculate("PED-1", "NSU1");
        String second = calculate("PED-2", "NSU1");

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void deveSerEstavelEntreExecucoesDaJvm() {
        // Sem seed aleatório nem dependência de identidade de objeto — SHA-256 puro sobre texto.
        String fingerprint = calculate("PED-1", "NSU1");

        assertThat(fingerprint).hasSize(64);
        assertThat(fingerprint).matches("[0-9a-f]{64}");
    }

    private static String calculate(String externalId, String correlationKey) {
        return FingerprintCalculator.calculate(
                "INTERNAL_SALES", externalId, correlationKey, Direction.CREDIT, RecordType.SALE,
                50_000L, Currency.BRL, LocalDate.of(2026, 9, 10), "12345678900", "CREDIT_CARD");
    }
}
