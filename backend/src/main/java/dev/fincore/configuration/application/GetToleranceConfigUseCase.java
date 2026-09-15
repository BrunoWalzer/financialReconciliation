package dev.fincore.configuration.application;

import dev.fincore.configuration.domain.ToleranceConfig;
import dev.fincore.configuration.infrastructure.ToleranceConfigRepository;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/** {@code GET /config/tolerances/{sourcePairId}} — só {@code ADMINISTRATOR} (Implementation Plan M3). */
@Service
public class GetToleranceConfigUseCase {

    private final ToleranceConfigRepository repository;

    public GetToleranceConfigUseCase(ToleranceConfigRepository repository) {
        this.repository = repository;
    }

    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ToleranceConfig execute(UUID sourcePairId) {
        return repository.findBySourcePairId(sourcePairId)
                .orElseThrow(() -> new NoSuchElementException("tolerance_config não encontrado para " + sourcePairId));
    }
}
