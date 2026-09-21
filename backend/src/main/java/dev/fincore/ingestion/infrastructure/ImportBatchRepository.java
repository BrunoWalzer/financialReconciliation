package dev.fincore.ingestion.infrastructure;

import dev.fincore.ingestion.domain.ImportBatch;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

public interface ImportBatchRepository extends Repository<ImportBatch, UUID> {

    ImportBatch save(ImportBatch batch);

    Optional<ImportBatch> findById(UUID id);

    Optional<ImportBatch> findBySourceIdAndContentSha256AndReferenceDateAndReimportOfIdIsNull(
            UUID sourceId, String contentSha256, LocalDate referenceDate);

    Page<ImportBatch> findAll(Pageable pageable);
}
