package dev.fincore.configuration.infrastructure;

import dev.fincore.configuration.domain.FeeRule;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface FeeRuleRepository extends Repository<FeeRule, UUID> {

    FeeRule save(FeeRule feeRule);

    Optional<FeeRule> findById(UUID id);

    List<FeeRule> findBySourceId(UUID sourceId);

    List<FeeRule> findBySourceIdAndActiveTrue(UUID sourceId);
}
