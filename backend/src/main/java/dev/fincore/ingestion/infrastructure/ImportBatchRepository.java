package dev.fincore.ingestion.infrastructure;

import dev.fincore.ingestion.domain.ImportBatch;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface ImportBatchRepository extends Repository<ImportBatch, UUID> {

    ImportBatch save(ImportBatch batch);

    Optional<ImportBatch> findById(UUID id);

    Optional<ImportBatch> findBySourceIdAndContentSha256AndReferenceDateAndReimportOfIdIsNull(
            UUID sourceId, String contentSha256, LocalDate referenceDate);

    Page<ImportBatch> findAll(Pageable pageable);

    /**
     * Lotes em {@code RECEIVED}/{@code PROCESSING} (índice parcial {@code idx_import_batch_in_progress},
     * V5) travados além do prazo — o sweep de M8 (TDS 17.5) os republica em vez de outbox
     * transacional. "Travado" usa {@code started_at} quando existe (já entrou em
     * processamento) e cai para {@code uploaded_at} quando ainda está em {@code RECEIVED}
     * (mensagem original nunca chegou a ser consumida).
     */
    @Query("""
            SELECT b FROM ImportBatch b
             WHERE b.status IN (dev.fincore.ingestion.domain.ImportStatus.RECEIVED, dev.fincore.ingestion.domain.ImportStatus.PROCESSING)
               AND COALESCE(b.startedAt, b.uploadedAt) < :threshold
            """)
    List<ImportBatch> findStuckBatches(@Param("threshold") Instant threshold);
}
