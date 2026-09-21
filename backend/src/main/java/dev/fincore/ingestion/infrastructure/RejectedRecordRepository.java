package dev.fincore.ingestion.infrastructure;

import dev.fincore.ingestion.domain.RejectedRecord;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

public interface RejectedRecordRepository extends Repository<RejectedRecord, UUID> {

    RejectedRecord save(RejectedRecord record);

    Page<RejectedRecord> findByImportBatchId(UUID importBatchId, Pageable pageable);

    long countByImportBatchId(UUID importBatchId);
}
