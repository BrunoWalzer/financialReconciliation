package dev.fincore.audit.application;

import dev.fincore.audit.domain.ActorRef;
import java.util.Objects;
import java.util.UUID;

/**
 * O que um caso de uso pede para registrar. Um único tipo de parâmetro, não nove
 * argumentos posicionais, é o que torna a assinatura de {@link AuditService#record}
 * estável: um milestone futuro que precise de mais um campo estende este record sem
 * quebrar nenhuma chamada existente.
 *
 * <p>Todos os campos além de {@code actor} e {@code action} são opcionais — nem todo
 * evento tem entidade, estado, justificativa ou IP (TDS 22.2 prevê eventos de acesso e de
 * execução que não referenciam entidade alguma).
 *
 * @param beforeState JSON de texto com apenas os campos relevantes à ação (TDS 22.4) — nunca a entidade inteira
 * @param afterState idem
 * @param correlationId quando omitido, {@link AuditService} usa o da requisição em curso
 */
public record AuditEventRequest(
        ActorRef actor,
        String action,
        String entityType,
        UUID entityId,
        String beforeState,
        String afterState,
        String justification,
        String correlationId,
        String ipAddress) {

    public AuditEventRequest {
        Objects.requireNonNull(actor, "actor é obrigatório");
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("action é obrigatório");
        }
    }

    /** O caso comum: ator, ação e a entidade afetada, sem estado, justificativa ou IP. */
    public static AuditEventRequest of(ActorRef actor, String action, String entityType, UUID entityId) {
        return new AuditEventRequest(actor, action, entityType, entityId, null, null, null, null, null);
    }
}
