package dev.fincore.configuration.application;

import dev.fincore.configuration.domain.CoverageExpectation;
import dev.fincore.configuration.infrastructure.CoverageExpectationRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class ListCoverageExpectationsUseCase {

    private final CoverageExpectationRepository repository;

    public ListCoverageExpectationsUseCase(CoverageExpectationRepository repository) {
        this.repository = repository;
    }

    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public Optional<CoverageExpectation> execute(UUID sourceId) {
        return repository.findBySourceId(sourceId);
    }
}
