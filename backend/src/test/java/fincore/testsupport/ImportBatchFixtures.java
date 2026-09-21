package fincore.testsupport;

import dev.fincore.shared.identifier.Uuid7;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Um {@code import_batch} mínimo, só para satisfazer a FK
 * {@code fk_financial_record_import_batch} (V6) em testes de {@code evidence} que
 * precisam de um {@code FinancialRecord} mas não testam importação em si.
 *
 * <p>Antes do M5 (V6), {@code financial_record.import_batch_id} não tinha FK (FD-9) e
 * qualquer UUID aleatório servia. Isso mudou quando {@code import_batch} passou a existir
 * — este fixture existe para que os testes de M4 continuem válidos sem inventar
 * comportamento de importação que não é o que eles pretendem exercitar.
 */
public final class ImportBatchFixtures {

    private ImportBatchFixtures() {
    }

    public static UUID insertMinimal(JdbcTemplate jdbc, UUID sourceId) {
        UUID id = Uuid7.generate();
        String hash = id.toString().replace("-", "").repeat(2).substring(0, 64);
        jdbc.update(
                """
                insert into import_batch (id, source_id, original_filename, content_sha256, byte_size,
                    reference_date, status, storage_key, uploaded_by, uploaded_at, version)
                values (?, ?, 'fixture.csv', ?, 1, ?, 'RECEIVED', ?, ?, ?, 0)
                """,
                id, sourceId, hash, LocalDate.now(), "fixture-key-" + id, Uuid7.generate(), Timestamp.from(Instant.now()));
        return id;
    }
}
