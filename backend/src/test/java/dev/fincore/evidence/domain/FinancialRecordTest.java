package dev.fincore.evidence.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.fincore.shared.identifier.Uuid7;
import dev.fincore.shared.money.Currency;
import dev.fincore.shared.money.Money;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Invariantes puras de {@link FinancialRecord} — sem banco (Implementation Plan M4). */
class FinancialRecordTest {

    private static final Instant NOW = Instant.parse("2026-09-11T12:00:00Z");
    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 9, 10);

    @Test
    void deveCriarComIdentificadorGerado() {
        FinancialRecord record = newRecord(new Money(50_000, Currency.BRL), null, null);

        assertThat(record.id()).isNotNull();
        assertThat(record.id().version()).isEqualTo(7);
        assertThat(record.grossAmount()).isEqualTo(new Money(50_000, Currency.BRL));
    }

    @Test
    void deveRejeitarGrossAmountZero() {
        assertThatThrownBy(() -> newRecord(new Money(0, Currency.BRL), null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("zero");
    }

    @Test
    void deveRejeitarMoedaDiferenteDeBrl() {
        assertThatThrownBy(() -> newRecord(new Money(500, Currency.USD), null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BRL");
    }

    @Test
    void deveRejeitarDeclaredFeeAmountComMoedaDiferente() {
        assertThatThrownBy(() -> newRecord(
                        new Money(50_000, Currency.BRL), new Money(1_280, Currency.USD), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mesma moeda");
    }

    @Test
    void deveRejeitarNetAmountSemDeclaredFeeAmount() {
        // CHECK (net_amount_minor IS NULL OR declared_fee_amount_minor IS NOT NULL) — C-6.
        assertThatThrownBy(() -> newRecord(
                        new Money(50_000, Currency.BRL), null, new Money(48_720, Currency.BRL)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("declaredFeeAmount");
    }

    @Test
    void devePermitirGrossDeclaredFeeENetCoerentes() {
        FinancialRecord record = newRecord(
                new Money(50_000, Currency.BRL), new Money(1_280, Currency.BRL), new Money(48_720, Currency.BRL));

        assertThat(record.grossAmount()).isEqualTo(new Money(50_000, Currency.BRL));
        assertThat(record.declaredFeeAmount()).isEqualTo(new Money(1_280, Currency.BRL));
        assertThat(record.netAmount()).isEqualTo(new Money(48_720, Currency.BRL));
    }

    @Test
    void devePermitirDeclaredFeeSemNetAmount() {
        FinancialRecord record = newRecord(new Money(50_000, Currency.BRL), new Money(1_280, Currency.BRL), null);

        assertThat(record.declaredFeeAmount()).isEqualTo(new Money(1_280, Currency.BRL));
        assertThat(record.netAmount()).isNull();
    }

    @Test
    void devePermitirDeclaredFeeENetAmountAmbosAusentes() {
        FinancialRecord record = newRecord(new Money(50_000, Currency.BRL), null, null);

        assertThat(record.declaredFeeAmount()).isNull();
        assertThat(record.netAmount()).isNull();
    }

    @Test
    void deveRejeitarSourceIdNulo() {
        assertThatThrownBy(() -> new FinancialRecord(
                        null, Uuid7.generate(), 1, "PED-1", null, Direction.CREDIT, RecordType.SALE,
                        new Money(500, Currency.BRL), null, null, BUSINESS_DATE, null, null, null, null, null,
                        "linha bruta", fingerprint(), NOW))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void deveRejeitarRawLineEmBranco() {
        assertThatThrownBy(() -> new FinancialRecord(
                        Uuid7.generate(), Uuid7.generate(), 1, "PED-1", null, Direction.CREDIT, RecordType.SALE,
                        new Money(500, Currency.BRL), null, null, BUSINESS_DATE, null, null, null, null, null,
                        " ", fingerprint(), NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rawLine");
    }

    @Test
    void deveRejeitarFingerprintEmBranco() {
        assertThatThrownBy(() -> new FinancialRecord(
                        Uuid7.generate(), Uuid7.generate(), 1, "PED-1", null, Direction.CREDIT, RecordType.SALE,
                        new Money(500, Currency.BRL), null, null, BUSINESS_DATE, null, null, null, null, null,
                        "linha bruta", " ", NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fingerprint");
    }

    @Test
    void devePermitirExternalIdECorrelationKeyNulos() {
        FinancialRecord record = new FinancialRecord(
                Uuid7.generate(), Uuid7.generate(), 1, null, null, Direction.CREDIT, RecordType.SALE,
                new Money(500, Currency.BRL), null, null, BUSINESS_DATE, null, null, null, null, null,
                "linha bruta", fingerprint(), NOW);

        assertThat(record.externalId()).isNull();
        assertThat(record.correlationKey()).isNull();
    }

    @Test
    void naoTemNenhumCampoDeStatusDeConciliacao() {
        // Domain §5.2/§5.6: FinancialRecord não tem status de conciliação — nenhum
        // método/campo chamado status, reconciliationStatus, matched, etc.
        for (var method : FinancialRecord.class.getDeclaredMethods()) {
            String name = method.getName().toLowerCase();
            assertThat(name)
                    .as("FinancialRecord não deve ter membro relacionado a status de conciliação: " + name)
                    .doesNotContain("status")
                    .doesNotContain("reconcil")
                    .doesNotContain("matched")
                    .doesNotContain("pending");
        }
    }

    private static FinancialRecord newRecord(Money gross, Money declaredFee, Money net) {
        return new FinancialRecord(
                Uuid7.generate(), Uuid7.generate(), 1, "PED-1", "NSU1", Direction.CREDIT, RecordType.SALE,
                gross, declaredFee, net, BUSINESS_DATE, NOW, "12345678900", "CREDIT_CARD", "descrição", "DESCRICAO",
                "linha bruta original", fingerprint(), NOW);
    }

    private static String fingerprint() {
        return "a".repeat(64);
    }
}
