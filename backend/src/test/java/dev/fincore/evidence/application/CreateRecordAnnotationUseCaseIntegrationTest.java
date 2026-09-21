package dev.fincore.evidence.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.fincore.AbstractIntegrationTest;
import dev.fincore.evidence.domain.Direction;
import dev.fincore.evidence.domain.FinancialRecord;
import dev.fincore.evidence.domain.RecordAnnotation;
import dev.fincore.evidence.domain.RecordType;
import dev.fincore.evidence.infrastructure.FinancialRecordRepository;
import dev.fincore.shared.identifier.Uuid7;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * {@link CreateRecordAnnotationUseCase} contra PostgreSQL real — exige
 * {@code RECONCILIATION_ANALYST} (Implementation Plan M4) e audita na mesma transação
 * (TDS 22.2: "anotação criada").
 */
class CreateRecordAnnotationUseCaseIntegrationTest extends AbstractIntegrationTest {

    private static final UUID INTERNAL_SALES_ID = UUID.fromString("00000000-0000-7000-8000-000000000101");

    @Autowired
    private CreateRecordAnnotationUseCase createRecordAnnotationUseCase;

    @Autowired
    private FinancialRecordRepository financialRecordRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @WithMockUser(authorities = "RECONCILIATION_ANALYST")
    void analistaDeveCriarAnotacaoERegistrarAuditoria() {
        FinancialRecord record = saveRecord();
        UUID actorId = Uuid7.generate();

        RecordAnnotation created = createRecordAnnotationUseCase.execute(
                record.id(), "confirmado com o adquirente", actorId, "analista@fincore.dev");

        assertThat(created.financialRecordId()).isEqualTo(record.id());
        assertThat(created.authorId()).isEqualTo(actorId);

        Integer auditCount = jdbc.queryForObject(
                "select count(*) from audit_event where action = 'RECORD_ANNOTATION_CREATED' and entity_id = ?",
                Integer.class,
                created.id());
        assertThat(auditCount).isEqualTo(1);
    }

    @Test
    @WithMockUser(authorities = "RECONCILIATION_ANALYST")
    void deveLancarParaFinancialRecordInexistente() {
        assertThatThrownBy(() -> createRecordAnnotationUseCase.execute(
                        UUID.randomUUID(), "texto", Uuid7.generate(), "analista@fincore.dev"))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @WithMockUser(authorities = "AUDITOR")
    void auditorNaoDeveConseguirCriar() {
        FinancialRecord record = saveRecord();

        assertThatThrownBy(() -> createRecordAnnotationUseCase.execute(
                        record.id(), "texto", Uuid7.generate(), "auditor@fincore.dev"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(authorities = "ADMINISTRATOR")
    void administradorNaoDeveConseguirCriar() {
        FinancialRecord record = saveRecord();

        assertThatThrownBy(() -> createRecordAnnotationUseCase.execute(
                        record.id(), "texto", Uuid7.generate(), "admin@fincore.dev"))
                .isInstanceOf(AccessDeniedException.class);
    }

    private FinancialRecord saveRecord() {
        UUID importBatchId = ImportBatchFixtures.insertMinimal(jdbc, INTERNAL_SALES_ID);
        return financialRecordRepository.save(new FinancialRecord(
                INTERNAL_SALES_ID, importBatchId, 1, null, null, Direction.CREDIT, RecordType.SALE,
                new Money(500, Currency.BRL), null, null, LocalDate.of(2026, 9, 10), null, null, null, null, null,
                "linha", "e5".repeat(32), Instant.now()));
    }
}
