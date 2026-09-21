package dev.fincore.evidence.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * As quatro detecções da varredura de integridade intra-fonte (TDS 9.6), restritas aos
 * registros novos de um {@code import_batch} e aos grupos de chave que eles tocam — nunca à
 * fonte inteira, exatamente como o SQL conceitual do TDS descreve.
 *
 * <p>{@link #findExternalIdDuplicates}: sob o único parcial
 * {@code uq_financial_record_source_external_id} (V4), duas linhas de {@code financial_record}
 * nunca podem compartilhar {@code (source_id, external_id)} com {@code external_id} não nulo —
 * a segunda simplesmente não é inserida (vira {@code already_existing_count} no M5/M6). Esta
 * consulta é, portanto, estruturalmente inalcançável sob o schema atual; existe por simetria
 * com as outras três e como rede de segurança caso essa restrição seja um dia relaxada. Ver
 * o relatório do M7, seção Desvios, para a análise completa dessa tensão entre documentos.
 */
@Component
public class IntegrityScanQueries {

    private final JdbcTemplate jdbc;

    public IntegrityScanQueries(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<UUID> findCorrelationKeyDuplicates(UUID sourceId, UUID importBatchId) {
        return jdbc.queryForList(
                """
                SELECT fr.id FROM financial_record fr
                WHERE fr.source_id = ? AND fr.correlation_key IN (
                    SELECT correlation_key FROM financial_record
                    WHERE source_id = ? AND correlation_key IN (
                        SELECT DISTINCT correlation_key FROM financial_record
                        WHERE import_batch_id = ? AND correlation_key IS NOT NULL
                    )
                    GROUP BY correlation_key HAVING count(*) > 1
                )
                """,
                UUID.class, sourceId, sourceId, importBatchId);
    }

    public List<UUID> findExternalIdDuplicates(UUID sourceId, UUID importBatchId) {
        return jdbc.queryForList(
                """
                SELECT fr.id FROM financial_record fr
                WHERE fr.source_id = ? AND fr.external_id IN (
                    SELECT external_id FROM financial_record
                    WHERE source_id = ? AND external_id IN (
                        SELECT DISTINCT external_id FROM financial_record
                        WHERE import_batch_id = ? AND external_id IS NOT NULL
                    )
                    GROUP BY external_id HAVING count(*) > 1
                )
                """,
                UUID.class, sourceId, sourceId, importBatchId);
    }

    public List<UUID> findFingerprintDuplicatesWithoutExternalId(UUID sourceId, UUID importBatchId) {
        return jdbc.queryForList(
                """
                SELECT fr.id FROM financial_record fr
                WHERE fr.source_id = ? AND fr.external_id IS NULL AND fr.fingerprint IN (
                    SELECT fingerprint FROM financial_record
                    WHERE source_id = ? AND external_id IS NULL AND fingerprint IN (
                        SELECT DISTINCT fingerprint FROM financial_record
                        WHERE import_batch_id = ? AND external_id IS NULL
                    )
                    GROUP BY fingerprint HAVING count(*) > 1
                )
                """,
                UUID.class, sourceId, sourceId, importBatchId);
    }

    public List<UUID> findSourceInternalInconsistencies(UUID importBatchId, long toleranceMinor) {
        return jdbc.queryForList(
                """
                SELECT id FROM financial_record
                WHERE import_batch_id = ?
                  AND declared_fee_amount_minor IS NOT NULL
                  AND net_amount_minor IS NOT NULL
                  AND abs(gross_amount_minor - declared_fee_amount_minor - net_amount_minor) > ?
                """,
                UUID.class, importBatchId, toleranceMinor);
    }
}
