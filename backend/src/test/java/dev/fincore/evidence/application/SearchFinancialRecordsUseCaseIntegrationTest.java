package dev.fincore.evidence.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.fincore.AbstractIntegrationTest;
import dev.fincore.evidence.domain.Direction;
import dev.fincore.evidence.domain.FinancialRecord;
import dev.fincore.evidence.domain.RecordType;
import dev.fincore.evidence.infrastructure.FinancialRecordRepository;
import dev.fincore.shared.money.Currency;
import dev.fincore.shared.money.Money;
import fincore.testsupport.ImportBatchFixtures;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * {@link SearchFinancialRecordsUseCase} contra PostgreSQL real — os filtros de
 * {@code GET /records} (TDS 20.2), cada um isolado por um marcador único para não colidir
 * com outros testes que compartilham o banco.
 */
class SearchFinancialRecordsUseCaseIntegrationTest extends AbstractIntegrationTest {

    private static final UUID INTERNAL_SALES_ID = UUID.fromString("00000000-0000-7000-8000-000000000101");
    private static final UUID ACQUIRER_SETTLEMENT_ID = UUID.fromString("00000000-0000-7000-8000-000000000102");

    @Autowired
    private SearchFinancialRecordsUseCase searchFinancialRecordsUseCase;

    @Autowired
    private FinancialRecordRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @WithMockUser(authorities = "AUDITOR")
    void deveFiltrarPorSourceIdEExternalId() {
        String externalId = "PED-SEARCH-" + UUID.randomUUID();
        repository.save(newRecord(INTERNAL_SALES_ID, externalId, null, new Money(900, Currency.BRL), null, null));

        Page<FinancialRecord> page = searchFinancialRecordsUseCase.execute(
                new FinancialRecordSearchFilter(INTERNAL_SALES_ID, externalId, null, null, null, null, null, null),
                PageRequest.of(0, 25));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).externalId()).isEqualTo(externalId);
    }

    @Test
    @WithMockUser(authorities = "AUDITOR")
    void deveFiltrarPorFaixaDeBusinessDate() {
        String marker = "NSU-RANGE-" + UUID.randomUUID();
        repository.save(newRecord(
                ACQUIRER_SETTLEMENT_ID, null, marker, new Money(100, Currency.BRL),
                new Money(10, Currency.BRL), new Money(90, Currency.BRL), LocalDate.of(2026, 6, 15)));

        Page<FinancialRecord> found = searchFinancialRecordsUseCase.execute(
                new FinancialRecordSearchFilter(
                        ACQUIRER_SETTLEMENT_ID, null, marker, null, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), null, null),
                PageRequest.of(0, 25));
        Page<FinancialRecord> notFound = searchFinancialRecordsUseCase.execute(
                new FinancialRecordSearchFilter(
                        ACQUIRER_SETTLEMENT_ID, null, marker, null, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), null, null),
                PageRequest.of(0, 25));

        assertThat(found.getContent()).hasSize(1);
        assertThat(notFound.getContent()).isEmpty();
    }

    @Test
    @WithMockUser(authorities = "AUDITOR")
    void deveFiltrarPorGrossAmountMinor() {
        String marker = "NSU-AMOUNT-" + UUID.randomUUID();
        repository.save(newRecord(INTERNAL_SALES_ID, null, marker, new Money(12_345, Currency.BRL), null, null));

        Page<FinancialRecord> matching = searchFinancialRecordsUseCase.execute(
                new FinancialRecordSearchFilter(null, null, marker, 12_345L, null, null, null, null),
                PageRequest.of(0, 25));
        Page<FinancialRecord> nonMatching = searchFinancialRecordsUseCase.execute(
                new FinancialRecordSearchFilter(null, null, marker, 1L, null, null, null, null),
                PageRequest.of(0, 25));

        assertThat(matching.getContent()).hasSize(1);
        assertThat(nonMatching.getContent()).isEmpty();
    }

    @Test
    @WithMockUser(authorities = "AUDITOR")
    void deveFiltrarPorPaymentMethodEDocument() {
        String marker = "NSU-DOC-" + UUID.randomUUID();
        FinancialRecord record = newRecord(INTERNAL_SALES_ID, null, marker, new Money(500, Currency.BRL), null, null);
        repository.save(record);

        Page<FinancialRecord> byPaymentMethod = searchFinancialRecordsUseCase.execute(
                new FinancialRecordSearchFilter(null, null, marker, null, null, null, "CREDIT_CARD", null),
                PageRequest.of(0, 25));
        Page<FinancialRecord> byDocument = searchFinancialRecordsUseCase.execute(
                new FinancialRecordSearchFilter(null, null, marker, null, null, null, null, "12345678900"),
                PageRequest.of(0, 25));

        assertThat(byPaymentMethod.getContent()).hasSize(1);
        assertThat(byDocument.getContent()).hasSize(1);
    }

    @Test
    @WithMockUser(authorities = "AUDITOR")
    void deveIgnorarFiltrosNaoInformados() {
        String marker = "NSU-NOFILTER-" + UUID.randomUUID();
        repository.save(newRecord(INTERNAL_SALES_ID, null, marker, new Money(50, Currency.BRL), null, null));

        Page<FinancialRecord> page = searchFinancialRecordsUseCase.execute(
                new FinancialRecordSearchFilter(null, null, marker, null, null, null, null, null),
                PageRequest.of(0, 25));

        assertThat(page.getContent()).hasSize(1);
    }

    @Test
    void semAutenticacaoNaoDeveConseguirBuscar() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> searchFinancialRecordsUseCase.execute(
                        new FinancialRecordSearchFilter(null, null, null, null, null, null, null, null),
                        PageRequest.of(0, 25)))
                .isInstanceOf(RuntimeException.class);
    }

    private FinancialRecord newRecord(
            UUID sourceId, String externalId, String correlationKey, Money gross, Money declaredFee, Money net) {
        return newRecord(sourceId, externalId, correlationKey, gross, declaredFee, net, LocalDate.of(2026, 9, 10));
    }

    private FinancialRecord newRecord(
            UUID sourceId, String externalId, String correlationKey, Money gross, Money declaredFee, Money net,
            LocalDate businessDate) {
        UUID importBatchId = ImportBatchFixtures.insertMinimal(jdbc, INTERNAL_SALES_ID);
        return new FinancialRecord(
                sourceId, importBatchId, 1, externalId, correlationKey, Direction.CREDIT, RecordType.SALE,
                gross, declaredFee, net, businessDate, Instant.parse("2026-09-10T12:00:00Z"),
                "12345678900", "CREDIT_CARD", "descrição", "DESCRICAO", "linha bruta",
                "c3".repeat(32), Instant.now());
    }
}
