package dev.fincore.audit.domain;

import dev.fincore.shared.identifier.Uuid7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Registro imutável de uma ação relevante (Domain glossário; TDS 22.1).
 *
 * <p>Aggregate root do módulo {@code audit}. É escrito uma única vez, na mesma transação
 * da mudança que registra (TDS 16.1) — nunca atualizado, nunca apagado. A barreira
 * definitiva é o trigger {@code fincore_reject_mutation()} em {@code V1__audit.sql}
 * (I-2); esta classe reforça a mesma garantia em código, não expondo nenhum método capaz
 * de alterar um campo depois da construção.
 *
 * <p>{@code id} e {@code occurredAt} nascem aqui, não no banco: o PostgreSQL não gera
 * UUID v7 nativamente (ver {@link Uuid7}), e a auditoria registra o instante em que a
 * aplicação decidiu o fato, não o instante em que a linha chegou ao disco.
 */
@Entity
@Table(name = "audit_event")
public class AuditEvent {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, updatable = false, length = 10)
    private ActorType actorType;

    @Column(name = "actor_user_id", updatable = false)
    private UUID actorUserId;

    @Column(name = "actor_label", nullable = false, updatable = false)
    private String actorLabel;

    @Column(name = "actor_run_id", updatable = false)
    private UUID actorRunId;

    @Column(name = "action", nullable = false, updatable = false)
    private String action;

    @Column(name = "entity_type", updatable = false)
    private String entityType;

    @Column(name = "entity_id", updatable = false)
    private UUID entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_state", updatable = false)
    private String beforeState;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_state", updatable = false)
    private String afterState;

    @Column(name = "justification", updatable = false)
    private String justification;

    @Column(name = "correlation_id", updatable = false)
    private String correlationId;

    // O driver PostgreSQL não tem um tipo Java nativo para `inet`. Testado e confirmado:
    // sem o cast explícito abaixo, até um valor NULL chega ao driver tipado como bytea e o
    // Postgres recusa a atribuição a uma coluna inet. @ColumnTransformer aplica o cast só
    // na escrita; a leitura já converte inet → text implicitamente.
    @ColumnTransformer(write = "?::inet")
    @Column(name = "ip_address", updatable = false, columnDefinition = "inet")
    private String ipAddress;

    /** Exigido pelo JPA para reconstruir a entidade ao ler do banco. Nunca chamado pela aplicação. */
    protected AuditEvent() {
    }

    public AuditEvent(
            ActorRef actor,
            String action,
            String entityType,
            UUID entityId,
            String beforeState,
            String afterState,
            String justification,
            String correlationId,
            String ipAddress) {
        Objects.requireNonNull(actor, "actor é obrigatório");
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("action é obrigatório");
        }

        this.id = Uuid7.generate();
        this.occurredAt = Instant.now();
        this.actorType = actor.actorType();
        this.actorUserId = actor.actorUserId();
        this.actorLabel = actor.actorLabel();
        this.actorRunId = actor.actorRunId();
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.beforeState = beforeState;
        this.afterState = afterState;
        this.justification = justification;
        this.correlationId = correlationId;
        this.ipAddress = ipAddress;
    }

    public UUID id() {
        return id;
    }

    public Instant occurredAt() {
        return occurredAt;
    }

    /** Reconstrói o VO de ator a partir dos campos denormalizados. */
    public ActorRef actor() {
        return new ActorRef(actorType, actorUserId, actorLabel, actorRunId);
    }

    public String action() {
        return action;
    }

    public String entityType() {
        return entityType;
    }

    public UUID entityId() {
        return entityId;
    }

    public String beforeState() {
        return beforeState;
    }

    public String afterState() {
        return afterState;
    }

    public String justification() {
        return justification;
    }

    public String correlationId() {
        return correlationId;
    }

    public String ipAddress() {
        return ipAddress;
    }
}
