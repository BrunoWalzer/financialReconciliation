package dev.fincore.ingestion.application;

import dev.fincore.ingestion.domain.ImportBatch;
import dev.fincore.ingestion.infrastructure.ImportBatchRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/** {@code GET /imports} — leitura para todos os papéis autenticados (TDS 20.2). */
@Service
public class ListImportBatchesUseCase {

    private final ImportBatchRepository repository;

    public ListImportBatchesUseCase(ImportBatchRepository repository) {
        this.repository = repository;
    }

    @PreAuthorize("isAuthenticated()")
    public Page<ImportBatch> execute(Pageable pageable) {
        return repository.findAll(pageable);
    }
}
