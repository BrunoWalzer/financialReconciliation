package dev.fincore.evidence.application;

import dev.fincore.evidence.domain.RecordIntegrityFlag;
import dev.fincore.evidence.infrastructure.RecordIntegrityFlagRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Flags de um registro, para embutir em {@code GET /records/{id}} (Implementation Plan M7:
 * "Flags aparecem em GET /records/{id}") — leitura para todos os papéis autenticados, mesmo
 * padrão de {@link GetFinancialRecordUseCase}. Não reverifica a existência do
 * {@code financial_record}: o controller já chamou {@code GetFinancialRecordUseCase} antes,
 * que lança 404 se não existir.
 */
@Service
public class ListRecordIntegrityFlagsUseCase {

    private final RecordIntegrityFlagRepository repository;

    public ListRecordIntegrityFlagsUseCase(RecordIntegrityFlagRepository repository) {
        this.repository = repository;
    }

    @PreAuthorize("isAuthenticated()")
    public List<RecordIntegrityFlag> execute(UUID financialRecordId) {
        return repository.findByFinancialRecordIdOrderByDetectedAtAsc(financialRecordId);
    }
}
