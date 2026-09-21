package dev.fincore.evidence.infrastructure;

import dev.fincore.evidence.domain.RecordIntegrityFlag;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Insere um lote de {@link RecordIntegrityFlag} com {@code ON CONFLICT DO NOTHING} sobre o
 * único parcial {@code uq_record_integrity_flag_open} (V4) — é isto, e não uma checagem
 * {@code existsBy} antes do insert, que torna uma segunda varredura sobre o mesmo grupo
 * idempotente (critério de aceite do M7). JPA não expressa {@code ON CONFLICT}; daí o JDBC
 * direto, mesmo padrão de {@link FinancialRecordBulkInsert}.
 */
@Component
public class RecordIntegrityFlagBulkInsert {

    private static final String INSERT_SQL = """
            INSERT INTO record_integrity_flag (id, financial_record_id, flag_type, detected_at, detected_by_batch_id)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (financial_record_id, flag_type) WHERE resolved_at IS NULL DO NOTHING
            """;

    private final JdbcTemplate jdbc;

    public RecordIntegrityFlagBulkInsert(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Devolve quantas linhas foram efetivamente inseridas (as demais já existiam como flag aberta). */
    public int insertIgnoringConflicts(List<RecordIntegrityFlag> flags) {
        if (flags.isEmpty()) {
            return 0;
        }
        int[] affectedRows = jdbc.batchUpdate(INSERT_SQL, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                RecordIntegrityFlag flag = flags.get(i);
                ps.setObject(1, flag.id());
                ps.setObject(2, flag.financialRecordId());
                ps.setString(3, flag.flagType().name());
                ps.setTimestamp(4, Timestamp.from(flag.detectedAt()));
                ps.setObject(5, flag.detectedByBatchId());
            }

            @Override
            public int getBatchSize() {
                return flags.size();
            }
        });

        return (int) java.util.stream.IntStream.of(affectedRows).filter(count -> count > 0).count();
    }
}
