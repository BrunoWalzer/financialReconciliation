package dev.fincore.evidence.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import dev.fincore.AbstractIntegrationTest;
import dev.fincore.evidence.domain.Direction;
import dev.fincore.evidence.domain.FinancialRecord;
import dev.fincore.evidence.domain.RecordType;
import dev.fincore.shared.money.Currency;
import dev.fincore.shared.money.Money;
import fincore.testsupport.ImportBatchFixtures;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * {@link FinancialRecordRepository} contra PostgreSQL real: persistir e reler preserva
 * {@link Money} exatamente (Implementation Plan M4, critério de aceite 3) e o relacionamento
 * com {@code source} é respeitado. Os filtros de {@code GET /records} são cobertos em
 * {@code SearchFinancialRecordsUseCaseIntegrationTest}, onde a Specification é montada.
 */
class FinancialRecordRepositoryIntegrationTest extends AbstractIntegrationTest {

    // Fonte semeada por V3.
    private static final UUID INTERNAL_SALES_ID = UUID.fromString("00000000-0000-7000-8000-000000000101");

    @Autowired
    private FinancialRecordRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void devePersistirERelerPreservandoMoneyExatamente() {
        FinancialRecord record = newRecord(
                INTERNAL_SALES_ID, "PED-REREAD", new Money(1_234_567, Currency.BRL),
                new Money(1_280, Currency.BRL), new Money(1_233_287, Currency.BRL));

        FinancialRecord saved = repository.save(record);
        FinancialRecord reloaded = repository.findById(saved.id()).orElseThrow();

        assertThat(reloaded.grossAmount()).isEqualTo(new Money(1_234_567, Currency.BRL));
        assertThat(reloaded.declaredFeeAmount()).isEqualTo(new Money(1_280, Currency.BRL));
        assertThat(reloaded.netAmount()).isEqualTo(new Money(1_233_287, Currency.BRL));
        assertThat(reloaded.sourceId()).isEqualTo(INTERNAL_SALES_ID);
    }

    @Test
    void devePersistirComValoresNegativosExatamente() {
        FinancialRecord record = newRecord(
                INTERNAL_SALES_ID, "PED-NEG", new Money(-5_000, Currency.BRL), null, null);

        FinancialRecord saved = repository.save(record);
        FinancialRecord reloaded = repository.findById(saved.id()).orElseThrow();

        assertThat(reloaded.grossAmount()).isEqualTo(new Money(-5_000, Currency.BRL));
    }

    @Test
    void deveRelerDeFormaEstavelEmLeiturasSucessivas() {
        FinancialRecord record = newRecord(INTERNAL_SALES_ID, "PED-STABLE", new Money(700, Currency.BRL), null, null);
        FinancialRecord saved = repository.save(record);

        FinancialRecord first = repository.findById(saved.id()).orElseThrow();
        FinancialRecord second = repository.findById(saved.id()).orElseThrow();

        assertThat(first.grossAmount()).isEqualTo(second.grossAmount());
        assertThat(first.rawLine()).isEqualTo(second.rawLine());
    }

    private FinancialRecord newRecord(
            UUID sourceId, String externalId, Money gross, Money declaredFee, Money net) {
        UUID importBatchId = ImportBatchFixtures.insertMinimal(jdbc, sourceId);
        return new FinancialRecord(
                sourceId, importBatchId, 1, externalId, null, Direction.CREDIT, RecordType.SALE,
                gross, declaredFee, net, LocalDate.of(2026, 9, 10), Instant.parse("2026-09-10T12:00:00Z"),
                "12345678900", "CREDIT_CARD", "descrição", "DESCRICAO", "linha bruta original",
                "f".repeat(64), Instant.now());
    }
}
