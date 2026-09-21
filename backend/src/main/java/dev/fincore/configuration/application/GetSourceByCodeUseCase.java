package dev.fincore.configuration.application;

import dev.fincore.configuration.domain.Source;
import dev.fincore.configuration.infrastructure.SourceRepository;
import java.util.Optional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Resolve {@code sourceCode} para a fonte, para qualquer autenticado — diferente de
 * {@link ListSourcesUseCase} (só {@code ADMINISTRATOR}, Implementation Plan M3).
 *
 * <p>Existe para o filtro {@code sourceCode} de {@code GET /records} (Implementation Plan
 * M4): esse endpoint é "leitura para todos" (TDS 20.2), e usar {@link ListSourcesUseCase}
 * ali quebraria isso para {@code AUDITOR}/{@code RECONCILIATION_ANALYST}.
 */
@Service
public class GetSourceByCodeUseCase {

    private final SourceRepository repository;

    public GetSourceByCodeUseCase(SourceRepository repository) {
        this.repository = repository;
    }

    @PreAuthorize("isAuthenticated()")
    public Optional<Source> execute(String code) {
        return repository.findByCode(code);
    }
}
