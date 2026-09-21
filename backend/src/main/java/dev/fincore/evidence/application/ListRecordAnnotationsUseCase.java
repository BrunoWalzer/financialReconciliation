package dev.fincore.evidence.application;

import dev.fincore.evidence.domain.RecordAnnotation;
import dev.fincore.evidence.infrastructure.FinancialRecordRepository;
import dev.fincore.evidence.infrastructure.RecordAnnotationRepository;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/** {@code GET /records/{id}/annotations} — leitura para todos os papéis autenticados. */
@Service
public class ListRecordAnnotationsUseCase {

    private final RecordAnnotationRepository repository;
    private final FinancialRecordRepository financialRecordRepository;

    public ListRecordAnnotationsUseCase(
            RecordAnnotationRepository repository, FinancialRecordRepository financialRecordRepository) {
        this.repository = repository;
        this.financialRecordRepository = financialRecordRepository;
    }

    @PreAuthorize("isAuthenticated()")
    public List<RecordAnnotation> execute(UUID financialRecordId) {
        financialRecordRepository.findById(financialRecordId)
                .orElseThrow(() -> new NoSuchElementException("financial_record não encontrado: " + financialRecordId));
        return repository.findByFinancialRecordIdOrderByCreatedAtAsc(financialRecordId);
    }
}
