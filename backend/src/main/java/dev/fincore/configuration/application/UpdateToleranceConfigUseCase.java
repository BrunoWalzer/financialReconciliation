package dev.fincore.configuration.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.fincore.audit.application.AuditEventRequest;
import dev.fincore.audit.application.AuditService;
import dev.fincore.audit.domain.ActorRef;
import dev.fincore.configuration.domain.ToleranceConfig;
import dev.fincore.configuration.infrastructure.ToleranceConfigRepository;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Altera a tolerância de um par de fontes — a operação mais sensível do sistema
 * (Domain §27.2): muda o que o sistema considera aceitável em todas as execuções
 * futuras. Auditada com antes e depois (Implementation Plan M3, critério de aceite 2).
 *
 * <p><b>Nunca encerra divergência aberta</b> (FD-6): este caso de uso não sabe o que é
 * uma divergência — {@code configuration} não depende de {@code divergence} nem de
 * {@code reconciliation} (TDS 4.2). A garantia não vem de uma checagem aqui; vem da
 * ausência de qualquer caminho de código entre os dois módulos.
 */
@Service
public class UpdateToleranceConfigUseCase {

    private final ToleranceConfigRepository repository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public UpdateToleranceConfigUseCase(
            ToleranceConfigRepository repository, AuditService auditService, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    @Transactional
    public ToleranceConfig execute(
            UUID sourcePairId, long ifMatchVersion, UpdateToleranceConfigCommand command, UUID actorUserId, String actorLabel) {
        ToleranceConfig config = repository.findBySourcePairId(sourcePairId)
                .orElseThrow(() -> new NoSuchElementException("tolerance_config não encontrado para " + sourcePairId));

        if (config.version() != ifMatchVersion) {
            throw new StaleConfigurationVersionException();
        }

        Map<String, Object> before = toMap(config);
        config.update(
                command.absoluteAmountMinor(), command.currency(), command.aggregateAlertThresholdMinor(),
                actorUserId, clock.instant());

        ToleranceConfig saved;
        try {
            saved = repository.save(config);
        } catch (OptimisticLockingFailureException e) {
            // Outra transação venceu a corrida entre a leitura acima e este save, apesar
            // do If-Match ter batido no momento da leitura — o JPA detecta na escrita.
            throw new StaleConfigurationVersionException();
        }

        auditService.record(new AuditEventRequest(
                ActorRef.user(actorUserId, actorLabel),
                "TOLERANCE_CONFIG_UPDATED",
                "ToleranceConfig",
                saved.id(),
                writeJson(before),
                writeJson(toMap(saved)),
                null,
                null,
                null));

        return saved;
    }

    private static Map<String, Object> toMap(ToleranceConfig config) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("absoluteAmountMinor", config.absoluteAmountMinor());
        map.put("currency", config.currency());
        map.put("aggregateAlertThresholdMinor", config.aggregateAlertThresholdMinor());
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
