package dev.fincore.configuration.application;

import dev.fincore.configuration.domain.SettlementWindow;
import dev.fincore.configuration.infrastructure.SettlementWindowRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class ListSettlementWindowsUseCase {

    private final SettlementWindowRepository repository;

    public ListSettlementWindowsUseCase(SettlementWindowRepository repository) {
        this.repository = repository;
    }

    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public List<SettlementWindow> execute(UUID sourcePairId) {
        return repository.findBySourcePairId(sourcePairId);
    }
}
