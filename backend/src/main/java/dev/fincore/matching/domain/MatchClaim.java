package dev.fincore.matching.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A garantia de exclusividade do sistema inteiro (I-5, TDS 7.6): {@code financialRecordId} é
 * a {@code PRIMARY KEY} da tabela, não um índice único sobre outra PK. Duas transações
 * concorrentes tentando reivindicar o mesmo {@code FinancialRecord} colidem na violação
 * dessa chave primária — nunca num {@code SELECT} antes do {@code INSERT} na aplicação
 * (Implementation Plan M10: "não mockar a constraint").
 */
@Entity
@Table(name = "match_claim")
public class MatchClaim {

    @Id
    @Column(name = "financial_record_id", updatable = false)
    private UUID financialRecordId;

    @Column(name = "match_id", nullable = false, updatable = false)
    private UUID matchId;

    @Column(name = "claimed_at", nullable = false, updatable = false)
    private Instant claimedAt;

    /** Exigido pelo JPA. Nunca chamado pela aplicação. */
    protected MatchClaim() {
    }

    public MatchClaim(UUID financialRecordId, UUID matchId, Instant claimedAt) {
        this.financialRecordId = Objects.requireNonNull(financialRecordId, "financialRecordId é obrigatório");
        this.matchId = Objects.requireNonNull(matchId, "matchId é obrigatório");
        this.claimedAt = Objects.requireNonNull(claimedAt, "claimedAt é obrigatório");
    }

    public UUID financialRecordId() {
        return financialRecordId;
    }

    public UUID matchId() {
        return matchId;
    }

    public Instant claimedAt() {
        return claimedAt;
    }
}
