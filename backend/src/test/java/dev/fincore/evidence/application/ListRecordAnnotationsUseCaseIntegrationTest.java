package dev.fincore.evidence.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.fincore.AbstractIntegrationTest;
import dev.fincore.evidence.domain.Direction;
import dev.fincore.evidence.domain.FinancialRecord;
import dev.fincore.evidence.domain.RecordAnnotation;
import dev.fincore.evidence.domain.RecordType;
import dev.fincore.evidence.infrastructure.FinancialRecordRepository;
import dev.fincore.evidence.infrastructure.RecordAnnotationRepository;
import dev.fincore.shared.identifier.Uuid7;
import dev.fincore.shared.money.Currency;
import dev.fincore.shared.money.Money;
import fincore.testsupport.ImportBatchFixtures;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;

class ListRecordAnnotationsUseCaseIntegrationTest extends AbstractIntegrationTest {

    private static final UUID INTERNAL_SALES_ID = UUID.fromString("00000000-0000-7000-8000-000000000101");

    @Autowired
    private ListRecordAnnotationsUseCase listRecordAnnotationsUseCase;

    @Autowired
    private FinancialRecordRepository financialRecordRepository;

    @Autowired
    private RecordAnnotationRepository recordAnnotationRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @WithMockUser(authorities = "AUDITOR")
    void deveListarAnotacoesEmOrdemDeCriacao() {
        UUID importBatchId = ImportBatchFixtures.insertMinimal(jdbc, INTERNAL_SALES_ID);
        FinancialRecord record = financialRecordRepository.save(new FinancialRecord(
                INTERNAL_SALES_ID, importBatchId, 1, null, null, Direction.CREDIT, RecordType.SALE,
                new Money(500, Currency.BRL), null, null, LocalDate.of(2026, 9, 10), null, null, null, null, null,
                "linha", "f6".repeat(32), Instant.now()));

        recordAnnotationRepository.save(new RecordAnnotation(
                record.id(), Uuid7.generate(), "primeira", Instant.parse("2026-09-10T10:00:00Z")));
        recordAnnotationRepository.save(new RecordAnnotation(
                record.id(), Uuid7.generate(), "segunda", Instant.parse("2026-09-10T11:00:00Z")));

        List<RecordAnnotation> annotations = listRecordAnnotationsUseCase.execute(record.id());

        assertThat(annotations).extracting(RecordAnnotation::text).containsExactly("primeira", "segunda");
    }

    @Test
    @WithMockUser(authorities = "AUDITOR")
    void deveLancarParaFinancialRecordInexistente() {
        assertThatThrownBy(() -> listRecordAnnotationsUseCase.execute(UUID.randomUUID()))
                .isInstanceOf(NoSuchElementException.class);
    }
}
