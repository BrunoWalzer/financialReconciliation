package dev.fincore.audit.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Quem agiu, no momento em que agiu (Implementation Plan M1, TDS 7.2, FD-7).
 *
 * <p>{@code actorLabel} denormaliza o rótulo do ator — e-mail do usuário, ou
 * {@code "SYSTEM"} — para que o evento continue legível mesmo que o usuário mude de
 * e-mail depois. É por isso que {@code actorUserId} não tem chave estrangeira para
 * {@code app_user} (FD-7): a legibilidade do rastro nunca pode depender da integridade
 * referencial com a tabela de usuários, e essa ausência de FK é o que permite este módulo
 * existir antes de {@code identity}.
 *
 * <p>{@code actorRunId} identifica a execução quando o ator é o sistema; não se aplica a
 * um ator humano.
 */
public record ActorRef(ActorType actorType, UUID actorUserId, String actorLabel, UUID actorRunId) {

    public ActorRef {
        Objects.requireNonNull(actorType, "actorType é obrigatório");
        if (actorLabel == null || actorLabel.isBlank()) {
            throw new IllegalArgumentException("actorLabel é obrigatório (FD-7)");
        }
        if (actorType == ActorType.USER && actorUserId == null) {
            throw new IllegalArgumentException("ator USER exige actorUserId (CHECK ck_audit_event_actor_user_id_required)");
        }
    }

    /** Um usuário autenticado. {@code label} é o e-mail, denormalizado no momento do evento. */
    public static ActorRef user(UUID userId, String label) {
        return new ActorRef(ActorType.USER, userId, label, null);
    }

    /** O próprio sistema, sem execução identificável (ex.: inicialização). */
    public static ActorRef system() {
        return system(null);
    }

    /** O próprio sistema, atuando dentro de uma execução (ex.: encerramento automático). */
    public static ActorRef system(UUID runId) {
        return new ActorRef(ActorType.SYSTEM, null, "SYSTEM", runId);
    }
}
