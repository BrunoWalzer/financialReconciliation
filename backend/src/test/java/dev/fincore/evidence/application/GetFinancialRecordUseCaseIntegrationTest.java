package dev.fincore.evidence.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import java.util.NoSuchElementException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;

class GetFinancialRecordUseCaseIntegrationTest extends AbstractIntegrationTest {

    private static final UUID INTERNAL_SALES_ID = UUID.fromString("00000000-0000-7000-8000-000000000101");

    @Autowired
    private GetFinancialRecordUseCase getFinancialRecordUseCase;

    @Autowired
    private FinancialRecordRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @WithMockUser(authorities = "RECONCILIATION_ANALYST")
    void qualquerPapelAutenticadoDeveLer() {
        UUID importBatchId = ImportBatchFixtures.insertMinimal(jdbc, INTERNAL_SALES_ID);
        FinancialRecord saved = repository.save(new FinancialRecord(
                INTERNAL_SALES_ID, importBatchId, 1, "PED-GET", null, Direction.CREDIT, RecordType.SALE,
                new Money(500, Currency.BRL), null, null, LocalDate.of(2026, 9, 10), null, null, null, null, null,
                "linha", "d4".repeat(32), Instant.now()));

        FinancialRecord found = getFinancialRecordUseCase.execute(saved.id());

        assertThat(found.id()).isEqualTo(saved.id());
    }

    @Test
    @WithMockUser(authorities = "AUDITOR")
    void deveLancarParaIdInexistente() {
        assertThatThrownBy(() -> getFinancialRecordUseCase.execute(UUID.randomUUID()))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void semAutenticacaoNaoDeveConseguirLer() {
        assertThatThrownBy(() -> getFinancialRecordUseCase.execute(UUID.randomUUID()))
                .isInstanceOf(RuntimeException.class);
    }
}
