package dev.fincore.evidence.infrastructure;

import dev.fincore.evidence.domain.FinancialRecord;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Insere um lote de {@link FinancialRecord} com {@code ON CONFLICT DO NOTHING} sobre o
 * único parcial {@code uq_financial_record_source_external_id} (TDS 9.5) — o mecanismo que
 * torna uma reimportação inofensiva sem duplicar identificador de origem já existente.
 * JPA não expressa {@code ON CONFLICT}; daí o JDBC direto, só aqui.
 *
 * <p>Linhas sem {@code external_id} (ex.: {@code ACQUIRER_SETTLEMENT}) nunca colidem por
 * este índice — a proteção delas contra reimportação é o hash do arquivo
 * ({@code uq_import_batch_content}), não este mecanismo.
 */
@Component
public class FinancialRecordBulkInsert {

    private static final String INSERT_SQL = """
            INSERT INTO financial_record (
                id, source_id, import_batch_id, line_number, external_id, correlation_key,
                direction, record_type, gross_amount_minor, declared_fee_amount_minor, net_amount_minor,
                currency, business_date, source_timestamp, counterparty_document, payment_method,
                description, description_normalized, raw_line, fingerprint, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (source_id, external_id) WHERE external_id IS NOT NULL DO NOTHING
            """;

    private final JdbcTemplate jdbc;

    public FinancialRecordBulkInsert(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Devolve, na ordem de {@code records}, {@code true} se a linha foi inserida e {@code false} se já existia. */
    public List<Boolean> insertIgnoringConflicts(List<FinancialRecord> records) {
        int[] affectedRows = jdbc.batchUpdate(INSERT_SQL, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                FinancialRecord record = records.get(i);
                ps.setObject(1, record.id());
                ps.setObject(2, record.sourceId());
                ps.setObject(3, record.importBatchId());
                ps.setInt(4, record.lineNumber());
                ps.setString(5, record.externalId());
                ps.setString(6, record.correlationKey());
                ps.setString(7, record.direction().name());
                ps.setString(8, record.recordType().name());
                ps.setLong(9, record.grossAmount().amountMinor());
                setNullableLong(ps, 10, record.declaredFeeAmount());
                setNullableLong(ps, 11, record.netAmount());
                ps.setString(12, record.currency().name());
                ps.setObject(13, record.businessDate());
                if (record.sourceTimestamp() != null) {
                    ps.setTimestamp(14, Timestamp.from(record.sourceTimestamp()));
                } else {
                    ps.setNull(14, Types.TIMESTAMP_WITH_TIMEZONE);
                }
                ps.setString(15, record.counterpartyDocument());
                ps.setString(16, record.paymentMethod());
                ps.setString(17, record.description());
                ps.setString(18, record.descriptionNormalized());
                ps.setString(19, record.rawLine());
                ps.setString(20, record.fingerprint());
                ps.setTimestamp(21, Timestamp.from(record.createdAt()));
            }

            @Override
            public int getBatchSize() {
                return records.size();
            }
        });

        return java.util.stream.IntStream.of(affectedRows)
                .mapToObj(count -> count > 0)
                .toList();
    }

    private static void setNullableLong(PreparedStatement ps, int index, dev.fincore.shared.money.Money money) throws SQLException {
        if (money == null) {
            ps.setNull(index, Types.BIGINT);
        } else {
            ps.setLong(index, money.amountMinor());
        }
    }
}
