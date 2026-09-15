package dev.fincore.configuration.infrastructure;

import dev.fincore.configuration.domain.ToleranceConfig;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface ToleranceConfigRepository extends Repository<ToleranceConfig, UUID> {

    ToleranceConfig save(ToleranceConfig config);

    Optional<ToleranceConfig> findById(UUID id);

    Optional<ToleranceConfig> findBySourcePairId(UUID sourcePairId);
}
