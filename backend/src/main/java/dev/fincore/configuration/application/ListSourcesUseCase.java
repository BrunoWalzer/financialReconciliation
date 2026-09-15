package dev.fincore.configuration.application;

import dev.fincore.configuration.domain.Source;
import dev.fincore.configuration.infrastructure.SourceRepository;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/** {@code GET /config/sources} — só {@code ADMINISTRATOR} (Implementation Plan M3: "todas ADMINISTRATOR"). */
@Service
public class ListSourcesUseCase {

    private final SourceRepository repository;

    public ListSourcesUseCase(SourceRepository repository) {
        this.repository = repository;
    }

    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public List<Source> execute() {
        return repository.findAll();
    }
}
