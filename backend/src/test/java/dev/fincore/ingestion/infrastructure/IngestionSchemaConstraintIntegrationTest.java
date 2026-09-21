package dev.fincore.ingestion.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.fincore.AbstractIntegrationTest;
import dev.fincore.shared.identifier.Uuid7;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/** {@code import_batch} e {@code rejected_record} ao nível do banco (TDS 7.4, I-6, I-3). */
class IngestionSchemaConstraintIntegrationTest extends AbstractIntegrationTest {

    private static final UUID INTERNAL_SALES_ID = UUID.fromString("00000000-0000-7000-8000-000000000101");

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void deveInserirUmLoteValido() {
        UUID id = UUID.randomUUID();
        insertBatch(id, INTERNAL_SALES_ID, "hash-" + id, LocalDate.of(2026, 9, 10), null, null);

        Integer count = jdbc.queryForObject("select count(*) from import_batch where id = ?", Integer.class, id);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void deveRejeitarSegundoLoteComMesmoConteudoFonteEData() {
        String hash = "h".repeat(64);
        LocalDate referenceDate = LocalDate.of(2026, 9, 10);
        insertBatch(UUID.randomUUID(), INTERNAL_SALES_ID, hash, referenceDate, null, null);

        assertThatThrownBy(() -> insertBatch(UUID.randomUUID(), INTERNAL_SALES_ID, hash, referenceDate, null, null))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("uq_import_batch_content");
    }

    @Test
    void devePermitirReimportacaoComMesmoConteudoQuandoReimportOfIdPreenchido() {
        String hash = "i".repeat(64);
        LocalDate referenceDate = LocalDate.of(2026, 9, 11);
        UUID original = UUID.randomUUID();
        insertBatch(original, INTERNAL_SALES_ID, hash, referenceDate, null, null);

        // Isento do único quando reimport_of_id é preenchido (I-6).
        insertBatch(UUID.randomUUID(), INTERNAL_SALES_ID, hash, referenceDate, original, "corrigido após erro de digitação");
    }

    @Test
    void deveRejeitarReimportacaoComMotivoCurto() {
        UUID original = UUID.randomUUID();
        insertBatch(original, INTERNAL_SALES_ID, "j".repeat(64), LocalDate.of(2026, 9, 12), null, null);

        assertThatThrownBy(() -> insertBatch(
                        UUID.randomUUID(), INTERNAL_SALES_ID, "k".repeat(64), LocalDate.of(2026, 9, 12), original, "curto"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_import_batch_reimport_reason");
    }

    @Test
    void devePermitirMesmoConteudoEmDatasDeReferenciaDiferentes() {
        String hash = "l".repeat(64);
        insertBatch(UUID.randomUUID(), INTERNAL_SALES_ID, hash, LocalDate.of(2026, 9, 13), null, null);
        insertBatch(UUID.randomUUID(), INTERNAL_SALES_ID, hash, LocalDate.of(2026, 9, 14), null, null);
    }

    @Test
    void deveRejeitarStatusForaDoConjuntoPermitido() {
        assertThatThrownBy(() -> jdbc.update(
                        """
                        insert into import_batch (id, source_id, original_filename, content_sha256, byte_size,
                            reference_date, status, storage_key, uploaded_by, uploaded_at, version)
                        values (?, ?, 'arquivo.csv', ?, 100, ?, 'UNKNOWN_STATUS', 'key', ?, ?, 0)
                        """,
                        UUID.randomUUID(), INTERNAL_SALES_ID, "m".repeat(64), LocalDate.of(2026, 9, 15),
                        UUID.randomUUID(), Timestamp.from(Instant.now())))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_import_batch_status");
    }

    @Test
    void deveInserirEDepoisRejeitarMutacaoDeRejectedRecord() {
        UUID batchId = UUID.randomUUID();
        insertBatch(batchId, INTERNAL_SALES_ID, "n".repeat(64), LocalDate.of(2026, 9, 16), null, null);

        UUID rejectedId = UUID.randomUUID();
        jdbc.update(
                "insert into rejected_record (id, import_batch_id, line_number, raw_line, reason_code) values (?, ?, 2, 'linha ruim', 'ZERO_AMOUNT')",
                rejectedId, batchId);

        assertThatThrownBy(() -> jdbc.update("update rejected_record set reason_code = 'INVALID_DATE' where id = ?", rejectedId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("imutável");

        assertThatThrownBy(() -> jdbc.update("delete from rejected_record where id = ?", rejectedId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("imutável");
    }

    @Test
    void deveRejeitarDoisRejectedRecordsComMesmaLinhaNoMesmoLote() {
        UUID batchId = UUID.randomUUID();
        insertBatch(batchId, INTERNAL_SALES_ID, "o".repeat(64), LocalDate.of(2026, 9, 17), null, null);
        jdbc.update(
                "insert into rejected_record (id, import_batch_id, line_number, raw_line, reason_code) values (?, ?, 5, 'linha', 'ZERO_AMOUNT')",
                UUID.randomUUID(), batchId);

        assertThatThrownBy(() -> jdbc.update(
                        "insert into rejected_record (id, import_batch_id, line_number, raw_line, reason_code) values (?, ?, 5, 'outra', 'INVALID_DATE')",
                        UUID.randomUUID(), batchId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("uq_rejected_record_batch_line");
    }

    @Test
    void deveRejeitarLoteComSourceIdInexistente() {
        assertThatThrownBy(() -> insertBatch(UUID.randomUUID(), UUID.randomUUID(), "p".repeat(64), LocalDate.of(2026, 9, 18), null, null))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("fk_import_batch_source");
    }

    private void insertBatch(
            UUID id, UUID sourceId, String contentSha256, LocalDate referenceDate, UUID reimportOfId, String reimportReason) {
        jdbc.update(
                """
                insert into import_batch (id, source_id, original_filename, content_sha256, byte_size,
                    reference_date, status, storage_key, reimport_of_id, reimport_reason, uploaded_by, uploaded_at, version)
                values (?, ?, 'arquivo.csv', ?, 100, ?, 'RECEIVED', ?, ?, ?, ?, ?, 0)
                """,
                id, sourceId, contentSha256, referenceDate, "key-" + id, reimportOfId, reimportReason,
                Uuid7.generate(), Timestamp.from(Instant.now()));
    }
}
