package dev.fincore.evidence.application;

import dev.fincore.evidence.domain.FinancialRecord;
import dev.fincore.evidence.infrastructure.FinancialRecordRepository;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/** {@code GET /records/{id}} — leitura para todos os papéis autenticados (TDS 20.2). */
@Service
public class GetFinancialRecordUseCase {

    private final FinancialRecordRepository repository;

    public GetFinancialRecordUseCase(FinancialRecordRepository repository) {
        this.repository = repository;
    }

    @PreAuthorize("isAuthenticated()")
    public FinancialRecord execute(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("financial_record não encontrado: " + id));
    }
}
