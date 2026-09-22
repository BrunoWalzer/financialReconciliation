package dev.fincore.matching.domain;

import dev.fincore.shared.identifier.Uuid7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Um par humano recusado como correspondência (I-10, TDS 7.6) — o que
 * {@code NOT_PREVIOUSLY_REJECTED} (M9) lê. Sem escrita nenhuma até a resolução manual (M14);
 * a tabela nasce agora porque o predicado já a consulta. {@code recordAId}/{@code recordBId}
 * seguem a ordem canônica (menor UUID primeiro) da própria constraint do banco — ver
 * {@link RejectedPair}, que já normaliza a ordem no lado do domínio puro.
 */
@Entity
@Table(name = "match_rejection")
public class MatchRejection {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "record_a_id", nullable = false, updatable = false)
    private UUID recordAId;

    @Column(name = "record_b_id", nullable = false, updatable = false)
    private UUID recordBId;

    @Column(name = "rejected_by", nullable = false, updatable = false)
    private UUID rejectedBy;

    @Column(name = "rejected_at", nullable = false, updatable = false)
    private Instant rejectedAt;

    @Column(name = "reason", nullable = false, updatable = false)
    private String reason;

    @Column(name = "divergence_id", updatable = false)
    private UUID divergenceId;

    /** Exigido pelo JPA. Nunca chamado pela aplicação. */
    protected MatchRejection() {
    }

    public MatchRejection(UUID recordA, UUID recordB, UUID rejectedBy, Instant rejectedAt, String reason) {
        RejectedPair canonical = RejectedPair.of(recordA, recordB);
        this.id = Uuid7.generate();
        this.recordAId = canonical.a();
        this.recordBId = canonical.b();
        this.rejectedBy = Objects.requireNonNull(rejectedBy, "rejectedBy é obrigatório");
        this.rejectedAt = Objects.requireNonNull(rejectedAt, "rejectedAt é obrigatório");
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason é obrigatório");
        }
        this.reason = reason;
    }

    public UUID id() {
        return id;
    }

    public UUID recordAId() {
        return recordAId;
    }

    public UUID recordBId() {
        return recordBId;
    }

    public UUID rejectedBy() {
        return rejectedBy;
    }

    public Instant rejectedAt() {
        return rejectedAt;
    }

    public String reason() {
        return reason;
    }

    public UUID divergenceId() {
        return divergenceId;
    }
}
