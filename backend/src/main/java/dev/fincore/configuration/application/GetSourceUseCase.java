package dev.fincore.configuration.application;

import dev.fincore.configuration.domain.Source;
import dev.fincore.configuration.infrastructure.SourceRepository;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Resolve uma fonte por id, para qualquer autenticado — mesmo raciocínio de
 * {@link GetSourceByCodeUseCase}: {@code ingestion} precisa ler {@code Source} (fuso,
 * separadores, formatos de data) para montar o contexto de parsing e para exibir
 * {@code sourceCode} em respostas de leitura de importação, que TDS 20.2 abre a todos os
 * papéis — usar {@link ListSourcesUseCase} (só {@code ADMINISTRATOR}) quebraria isso.
 */
@Service
public class GetSourceUseCase {

    private final SourceRepository repository;

    public GetSourceUseCase(SourceRepository repository) {
        this.repository = repository;
    }

    @PreAuthorize("isAuthenticated()")
    public Source execute(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("source não encontrada: " + id));
    }
}
