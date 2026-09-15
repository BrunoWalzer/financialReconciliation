package dev.fincore.configuration.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.fincore.audit.application.AuditEventRequest;
import dev.fincore.audit.application.AuditService;
import dev.fincore.audit.domain.ActorRef;
import dev.fincore.configuration.domain.SettlementWindow;
import dev.fincore.configuration.infrastructure.SettlementWindowRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Altera o prazo esperado de liquidação de uma janela existente. */
@Service
public class UpdateSettlementWindowUseCase {

    private final SettlementWindowRepository repository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public UpdateSettlementWindowUseCase(
            SettlementWindowRepository repository, AuditService auditService, ObjectMapper objectMapper) {
        this.repository = repository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    @Transactional
    public SettlementWindow execute(
            UUID windowId, long ifMatchVersion, UpdateSettlementWindowCommand command, UUID actorUserId, String actorLabel) {
        SettlementWindow window = repository.findById(windowId)
                .orElseThrow(() -> new NoSuchElementException("settlement_window não encontrada: " + windowId));

        if (window.version() != ifMatchVersion) {
            throw new StaleConfigurationVersionException();
        }

        Map<String, Object> before = toMap(window);
        window.update(command.minDays(), command.maxDays());

        SettlementWindow saved;
        try {
            saved = repository.save(window);
        } catch (OptimisticLockingFailureException e) {
            throw new StaleConfigurationVersionException();
        }

        auditService.record(new AuditEventRequest(
                ActorRef.user(actorUserId, actorLabel),
                "SETTLEMENT_WINDOW_UPDATED",
                "SettlementWindow",
                saved.id(),
                writeJson(before),
                writeJson(toMap(saved)),
                null,
                null,
                null));

        return saved;
    }

    private static Map<String, Object> toMap(SettlementWindow window) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("minDays", window.minDays());
        map.put("maxDays", window.maxDays());
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
