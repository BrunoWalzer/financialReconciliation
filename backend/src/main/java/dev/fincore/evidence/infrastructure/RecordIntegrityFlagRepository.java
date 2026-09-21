package dev.fincore.evidence.infrastructure;

import dev.fincore.evidence.domain.RecordIntegrityFlag;
import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/**
 * Leitura de {@link RecordIntegrityFlag} para {@code GET /records/{id}}. A escrita não passa
 * por aqui — vai por {@link RecordIntegrityFlagBulkInsert}, que sabe ignorar conflito contra
 * o único parcial {@code uq_record_integrity_flag_open} (V4); JPA {@code save} abortaria a
 * transação inteira na segunda varredura do mesmo grupo.
 */
public interface RecordIntegrityFlagRepository extends Repository<RecordIntegrityFlag, UUID> {

    List<RecordIntegrityFlag> findByFinancialRecordIdOrderByDetectedAtAsc(UUID financialRecordId);
}
