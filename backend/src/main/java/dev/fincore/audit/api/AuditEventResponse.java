package dev.fincore.audit.api;

import dev.fincore.audit.domain.AuditEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * Tudo — não há {@code GET /audit-events/{id}} separado, e "auditor pode ler tudo,
 * inclusive o log de auditoria" (Domain §4.2) não tem uma tela de detalhe para
 * complementar esta.
 */
public record AuditEventResponse(
        UUID id,
        Instant occurredAt,
        String actorType,
        UUID actorUserId,
        String actorLabel,
        UUID actorRunId,
        String action,
        String entityType,
        UUID entityId,
        String beforeState,
        String afterState,
        String justification,
        String correlationId,
        String ipAddress) {

    public static AuditEventResponse from(AuditEvent event) {
        return new AuditEventResponse(
                event.id(),
                event.occurredAt(),
                event.actor().actorType().name(),
                event.actor().actorUserId(),
                event.actor().actorLabel(),
                event.actor().actorRunId(),
                event.action(),
                event.entityType(),
                event.entityId(),
                event.beforeState(),
                event.afterState(),
                event.justification(),
                event.correlationId(),
                event.ipAddress());
    }
}
