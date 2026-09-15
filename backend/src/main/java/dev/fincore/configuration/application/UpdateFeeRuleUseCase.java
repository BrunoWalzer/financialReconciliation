package dev.fincore.configuration.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.fincore.audit.application.AuditEventRequest;
import dev.fincore.audit.application.AuditService;
import dev.fincore.audit.domain.ActorRef;
import dev.fincore.configuration.domain.FeeRule;
import dev.fincore.configuration.infrastructure.FeeRuleRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Altera os termos de uma regra de taxa existente (mesma linha, {@code If-Match} obrigatório). */
@Service
public class UpdateFeeRuleUseCase {

    private final FeeRuleRepository repository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public UpdateFeeRuleUseCase(FeeRuleRepository repository, AuditService auditService, ObjectMapper objectMapper) {
        this.repository = repository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    @Transactional
    public FeeRule execute(UUID feeRuleId, long ifMatchVersion, UpdateFeeRuleCommand command, UUID actorUserId, String actorLabel) {
        FeeRule rule = repository.findById(feeRuleId)
                .orElseThrow(() -> new NoSuchElementException("fee_rule não encontrada: " + feeRuleId));

        if (rule.version() != ifMatchVersion) {
            throw new StaleConfigurationVersionException();
        }

        Map<String, Object> before = toMap(rule);
        rule.update(command.percentageBp(), command.fixedAmountMinor(), command.roundingMode());

        FeeRule saved;
        try {
            saved = repository.save(rule);
        } catch (OptimisticLockingFailureException e) {
            throw new StaleConfigurationVersionException();
        }

        auditService.record(new AuditEventRequest(
                ActorRef.user(actorUserId, actorLabel),
                "FEE_RULE_UPDATED",
                "FeeRule",
                saved.id(),
                writeJson(before),
                writeJson(toMap(saved)),
                null,
                null,
                null));

        return saved;
    }

    private static Map<String, Object> toMap(FeeRule rule) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("percentageBp", rule.percentageBp());
        map.put("fixedAmountMinor", rule.fixedAmountMinor());
        map.put("roundingMode", rule.roundingMode().name());
        return map;
    }

    private String writeJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("falha ao serializar estado de auditoria", e);
        }
    }
}
