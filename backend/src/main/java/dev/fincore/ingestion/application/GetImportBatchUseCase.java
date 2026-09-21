package dev.fincore.ingestion.application;

import dev.fincore.ingestion.domain.ImportBatch;
import dev.fincore.ingestion.infrastructure.ImportBatchRepository;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/** {@code GET /imports/{id}} — leitura para todos os papéis autenticados (TDS 20.2). */
@Service
public class GetImportBatchUseCase {

    private final ImportBatchRepository repository;

    public GetImportBatchUseCase(ImportBatchRepository repository) {
        this.repository = repository;
    }

    @PreAuthorize("isAuthenticated()")
    public ImportBatch execute(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("import_batch não encontrado: " + id));
    }
}
