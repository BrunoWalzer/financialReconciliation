package dev.fincore.configuration.application;

import dev.fincore.audit.application.AuditEventRequest;
import dev.fincore.audit.application.AuditService;
import dev.fincore.audit.domain.ActorRef;
import dev.fincore.configuration.domain.FeeRule;
import dev.fincore.configuration.infrastructure.FeeRuleRepository;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cria uma nova regra de taxa. Verificação prévia (aplicação) mais a constraint
 * {@code uq_fee_rule_active_source_payment_method} (banco) — a checagem prévia evita o
 * caminho comum de rejeição chegar como uma exceção de banco crua; a constraint continua
 * sendo a garantia definitiva contra uma corrida genuína entre duas criações
 * simultâneas.
 */
@Service
public class CreateFeeRuleUseCase {

    private final FeeRuleRepository repository;
    private final AuditService auditService;

    public CreateFeeRuleUseCase(FeeRuleRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    @Transactional
    public FeeRule execute(CreateFeeRuleCommand command, UUID actorUserId, String actorLabel) {
        boolean alreadyActive = findActive(command.sourceId(), command.paymentMethod()).isPresent();
        if (alreadyActive) {
            throw new FeeRuleAlreadyActiveException();
        }

        FeeRule rule = new FeeRule(
                command.sourceId(), command.paymentMethod(), command.percentageBp(),
                command.fixedAmountMinor(), command.roundingMode());

        FeeRule saved;
        try {
            saved = repository.save(rule);
        } catch (DataIntegrityViolationException e) {
            // Corrida genuína: outra criação simultânea venceu entre a checagem acima e
            // este insert. A constraint de banco é quem realmente barra.
            throw new FeeRuleAlreadyActiveException();
        }

        auditService.record(AuditEventRequest.of(
                ActorRef.user(actorUserId, actorLabel), "FEE_RULE_CREATED", "FeeRule", saved.id()));

        return saved;
    }

    private Optional<FeeRule> findActive(UUID sourceId, String paymentMethod) {
        List<FeeRule> active = repository.findBySourceIdAndActiveTrue(sourceId);
        return active.stream()
                .filter(rule -> Objects.equals(rule.paymentMethod(), paymentMethod))
                .findFirst();
    }
}
