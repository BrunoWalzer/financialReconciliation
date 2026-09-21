package dev.fincore.evidence.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.fincore.AbstractIntegrationTest;
import fincore.testsupport.ImportBatchFixtures;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * {@code financial_record}, {@code record_integrity_flag}, {@code record_annotation} ao
 * nível do banco — constraints e, sobretudo, a trigger de imutabilidade (I-1), provada com
 * SQL real e não apenas contra a entidade Java (Implementation Plan M4, seção 5).
 *
 * <p>Usa {@link ImportBatchFixtures} para satisfazer {@code fk_financial_record_import_batch}
 * (V6, M5) — antes dessa FK existir, qualquer UUID servia; agora precisa apontar para um
 * {@code import_batch} real, ainda que estes testes não exercitem importação em si.
 */
class EvidenceSchemaConstraintIntegrationTest extends AbstractIntegrationTest {

    // Fontes semeadas por V3 (ver V3__configuration.sql).
    private static final UUID INTERNAL_SALES_ID = UUID.fromString("00000000-0000-7000-8000-000000000101");

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void deveInserirUmRegistroValido() {
        UUID id = UUID.randomUUID();
        insertFinancialRecord(id, INTERNAL_SALES_ID, "PED-1", 50_000, null, null);

        Integer count = jdbc.queryForObject("select count(*) from financial_record where id = ?", Integer.class, id);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void deveRejeitarUpdate() {
        UUID id = UUID.randomUUID();
        insertFinancialRecord(id, INTERNAL_SALES_ID, "PED-UPDATE", 50_000, null, null);

        assertThatThrownBy(() -> jdbc.update("update financial_record set gross_amount_minor = 1 where id = ?", id))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("imutável");
    }

    @Test
    void deveRejeitarDelete() {
        UUID id = UUID.randomUUID();
        insertFinancialRecord(id, INTERNAL_SALES_ID, "PED-DELETE", 50_000, null, null);

        assertThatThrownBy(() -> jdbc.update("delete from financial_record where id = ?", id))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("imutável");
    }

    @Test
    void deveRejeitarSegundoRegistroComMesmoExternalIdNaMesmaFonte() {
        insertFinancialRecord(UUID.randomUUID(), INTERNAL_SALES_ID, "PED-DUP", 50_000, null, null);

        assertThatThrownBy(() -> insertFinancialRecord(UUID.randomUUID(), INTERNAL_SALES_ID, "PED-DUP", 1, null, null))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("uq_financial_record_source_external_id");
    }

    @Test
    void naoDeveDeduplicarAutomaticamentePorFingerprintRepetido() {
        // Domain §9.3: "a impressão digital nunca é motivo suficiente para eliminar
        // evidência". Duas linhas distintas (external_id diferente) com fingerprint IDÊNTICO
        // — ex.: duas vendas de R$10, mesmo segundo, sem documento — precisam coexistir.
        String sharedFingerprint = "j".repeat(64);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID importBatchId = ImportBatchFixtures.insertMinimal(jdbc, INTERNAL_SALES_ID);
        jdbc.update(
                """
                insert into financial_record (id, source_id, import_batch_id, line_number, external_id,
                    direction, record_type, gross_amount_minor, currency, business_date, raw_line,
                    fingerprint, created_at)
                values (?, ?, ?, 1, 'PED-FP-1', 'CREDIT', 'SALE', 1000, 'BRL', ?, 'linha 1', ?, ?)
                """,
                first, INTERNAL_SALES_ID, importBatchId,
                Timestamp.valueOf("2026-09-10 00:00:00"), sharedFingerprint, Timestamp.from(Instant.now()));
        jdbc.update(
                """
                insert into financial_record (id, source_id, import_batch_id, line_number, external_id,
                    direction, record_type, gross_amount_minor, currency, business_date, raw_line,
                    fingerprint, created_at)
                values (?, ?, ?, 1, 'PED-FP-2', 'CREDIT', 'SALE', 1000, 'BRL', ?, 'linha 2', ?, ?)
                """,
                second, INTERNAL_SALES_ID, importBatchId,
                Timestamp.valueOf("2026-09-10 00:00:00"), sharedFingerprint, Timestamp.from(Instant.now()));

        Integer count = jdbc.queryForObject(
                "select count(*) from financial_record where fingerprint = ?", Integer.class, sharedFingerprint);
        assertThat(count).isEqualTo(2);
    }

    @Test
    void devePermitirMultiplosRegistrosComExternalIdNulo() {
        insertFinancialRecord(UUID.randomUUID(), INTERNAL_SALES_ID, null, 100, null, null);
        insertFinancialRecord(UUID.randomUUID(), INTERNAL_SALES_ID, null, 200, null, null);

        Integer count = jdbc.queryForObject(
                "select count(*) from financial_record where source_id = ? and external_id is null",
                Integer.class, INTERNAL_SALES_ID);
        assertThat(count).isGreaterThanOrEqualTo(2);
    }

    @Test
    void deveRejeitarGrossAmountZero() {
        assertThatThrownBy(() -> insertFinancialRecord(UUID.randomUUID(), INTERNAL_SALES_ID, "PED-ZERO", 0, null, null))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_financial_record_gross_amount_nonzero");
    }

    @Test
    void deveRejeitarMoedaForaDoConjuntoPermitido() {
        UUID importBatchId = ImportBatchFixtures.insertMinimal(jdbc, INTERNAL_SALES_ID);
        assertThatThrownBy(() -> jdbc.update(
                        """
                        insert into financial_record (id, source_id, import_batch_id, line_number, external_id,
                            direction, record_type, gross_amount_minor, currency, business_date, raw_line,
                            fingerprint, created_at)
                        values (?, ?, ?, 1, 'PED-USD', 'CREDIT', 'SALE', 500, 'USD', ?, 'linha', ?, ?)
                        """,
                        UUID.randomUUID(), INTERNAL_SALES_ID, importBatchId,
                        Timestamp.valueOf("2026-09-10 00:00:00"), "b".repeat(64), Timestamp.from(Instant.now())))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_financial_record_currency");
    }

    @Test
    void deveRejeitarDirectionForaDoConjuntoPermitido() {
        UUID importBatchId = ImportBatchFixtures.insertMinimal(jdbc, INTERNAL_SALES_ID);
        assertThatThrownBy(() -> jdbc.update(
                        """
                        insert into financial_record (id, source_id, import_batch_id, line_number, external_id,
                            direction, record_type, gross_amount_minor, currency, business_date, raw_line,
                            fingerprint, created_at)
                        values (?, ?, ?, 1, 'PED-DIR', 'SIDEWAYS', 'SALE', 500, 'BRL', ?, 'linha', ?, ?)
                        """,
                        UUID.randomUUID(), INTERNAL_SALES_ID, importBatchId,
                        Timestamp.valueOf("2026-09-10 00:00:00"), "c".repeat(64), Timestamp.from(Instant.now())))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_financial_record_direction");
    }

    @Test
    void deveRejeitarRecordTypeForaDoConjuntoPermitido() {
        UUID importBatchId = ImportBatchFixtures.insertMinimal(jdbc, INTERNAL_SALES_ID);
        assertThatThrownBy(() -> jdbc.update(
                        """
                        insert into financial_record (id, source_id, import_batch_id, line_number, external_id,
                            direction, record_type, gross_amount_minor, currency, business_date, raw_line,
                            fingerprint, created_at)
                        values (?, ?, ?, 1, 'PED-TYPE', 'CREDIT', 'DONATION', 500, 'BRL', ?, 'linha', ?, ?)
                        """,
                        UUID.randomUUID(), INTERNAL_SALES_ID, importBatchId,
                        Timestamp.valueOf("2026-09-10 00:00:00"), "d".repeat(64), Timestamp.from(Instant.now())))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_financial_record_record_type");
    }

    @Test
    void deveRejeitarNetAmountSemDeclaredFeeAmount() {
        assertThatThrownBy(() -> insertFinancialRecord(UUID.randomUUID(), INTERNAL_SALES_ID, "PED-NET", 50_000, null, 48_000))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_financial_record_net_requires_fee");
    }

    @Test
    void devePermitirDeclaredFeeENetAmountJuntos() {
        insertFinancialRecord(UUID.randomUUID(), INTERNAL_SALES_ID, "PED-FEE-NET", 50_000, 1_280, 48_720);
    }

    @Test
    void deveRejeitarSourceIdInexistente() {
        // import_batch precisa apontar para uma fonte real (sua própria FK), então o
        // batch é criado sob INTERNAL_SALES_ID — é o source_id do REGISTRO que é inválido.
        UUID importBatchId = ImportBatchFixtures.insertMinimal(jdbc, INTERNAL_SALES_ID);
        assertThatThrownBy(() -> insertFinancialRecordWithBatch(
                        UUID.randomUUID(), UUID.randomUUID(), importBatchId, "PED-ORFAO", 500, null, null))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("fk_financial_record_source");
    }

    @Test
    void deveInserirEDepoisRejeitarMutacaoDeAnotacao() {
        UUID recordId = UUID.randomUUID();
        insertFinancialRecord(recordId, INTERNAL_SALES_ID, "PED-ANNOTATION", 50_000, null, null);

        UUID annotationId = UUID.randomUUID();
        jdbc.update(
                "insert into record_annotation (id, financial_record_id, author_id, created_at, text) values (?, ?, ?, ?, ?)",
                annotationId, recordId, UUID.randomUUID(), Timestamp.from(Instant.now()), "confirmado");

        assertThatThrownBy(() -> jdbc.update("update record_annotation set text = 'outro' where id = ?", annotationId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("imutável");

        assertThatThrownBy(() -> jdbc.update("delete from record_annotation where id = ?", annotationId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("imutável");
    }

    @Test
    void deveRejeitarFlagTypeForaDoConjuntoPermitidoEmRecordIntegrityFlag() {
        UUID recordId = UUID.randomUUID();
        insertFinancialRecord(recordId, INTERNAL_SALES_ID, "PED-FLAG", 50_000, null, null);

        assertThatThrownBy(() -> jdbc.update(
                        "insert into record_integrity_flag (id, financial_record_id, flag_type, detected_at, detected_by_batch_id) "
                                + "values (?, ?, 'UNKNOWN_FLAG', ?, ?)",
                        UUID.randomUUID(), recordId, Timestamp.from(Instant.now()), UUID.randomUUID()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_record_integrity_flag_type");
    }

    private void insertFinancialRecord(
            UUID id, UUID sourceId, String externalId, long grossAmountMinor, Integer declaredFeeAmountMinor, Integer netAmountMinor) {
        // O import_batch precisa apontar para uma fonte real (fk_import_batch_source), por
        // isso é sempre criado sob INTERNAL_SALES_ID aqui — os testes desta classe não
        // exercitam a relação entre source_id do batch e source_id do registro.
        UUID importBatchId = ImportBatchFixtures.insertMinimal(jdbc, INTERNAL_SALES_ID);
        insertFinancialRecordWithBatch(id, sourceId, importBatchId, externalId, grossAmountMinor, declaredFeeAmountMinor, netAmountMinor);
    }

    private void insertFinancialRecordWithBatch(
            UUID id, UUID sourceId, UUID importBatchId, String externalId, long grossAmountMinor,
            Integer declaredFeeAmountMinor, Integer netAmountMinor) {
        jdbc.update(
                """
                insert into financial_record (id, source_id, import_batch_id, line_number, external_id,
                    correlation_key, direction, record_type, gross_amount_minor, declared_fee_amount_minor,
                    net_amount_minor, currency, business_date, source_timestamp, counterparty_document,
                    payment_method, description, description_normalized, raw_line, fingerprint, created_at)
                values (?, ?, ?, 1, ?, null, 'CREDIT', 'SALE', ?, ?, ?, 'BRL', ?, ?, null, 'CREDIT_CARD',
                    'descrição', 'DESCRICAO', 'linha bruta', ?, ?)
                """,
                id, sourceId, importBatchId, externalId, grossAmountMinor, declaredFeeAmountMinor, netAmountMinor,
                Timestamp.valueOf(LocalDate.of(2026, 9, 10).atStartOfDay()), Timestamp.from(Instant.now()),
                "e".repeat(64), Timestamp.from(Instant.now()));
    }
}
