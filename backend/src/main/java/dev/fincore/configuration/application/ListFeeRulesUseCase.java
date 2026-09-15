package dev.fincore.configuration.application;

import dev.fincore.configuration.domain.FeeRule;
import dev.fincore.configuration.infrastructure.FeeRuleRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/** {@code GET /config/fee-rules?sourceId=} — só {@code ADMINISTRATOR}. */
@Service
public class ListFeeRulesUseCase {

    private final FeeRuleRepository repository;

    public ListFeeRulesUseCase(FeeRuleRepository repository) {
        this.repository = repository;
    }

    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public List<FeeRule> execute(UUID sourceId) {
        return repository.findBySourceId(sourceId);
    }
}
