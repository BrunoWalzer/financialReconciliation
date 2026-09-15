package dev.fincore.configuration.infrastructure;

import dev.fincore.configuration.domain.SettlementWindow;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface SettlementWindowRepository extends Repository<SettlementWindow, UUID> {

    SettlementWindow save(SettlementWindow window);

    Optional<SettlementWindow> findById(UUID id);

    List<SettlementWindow> findBySourcePairId(UUID sourcePairId);
}
