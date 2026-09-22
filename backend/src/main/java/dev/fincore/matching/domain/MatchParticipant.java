package dev.fincore.matching.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Quem compõe um {@link Match} (Domain §5.2, TDS 7.6): exatamente dois {@code PRINCIPAL} (um
 * por {@link Side}) e zero ou mais {@code COMPONENT}. "Pelo menos um por lado" não é
 * expressável em {@code CHECK} de linha (I-14/FD-5) — só o índice único parcial
 * {@code uq_match_participant_principal_per_side} (no máximo um) vem do banco; "pelo menos
 * um" é responsabilidade de quem monta o match (TDS 7.6, Implementation Plan FD-5).
 *
 * <p>Guarda {@code financialRecordId} como UUID cru, não uma referência JPA a
 * {@code FinancialRecord}: {@code matching} não precisa navegar o grafo de objeto do
 * registro para persistir participação, só a chave — mesmo padrão de
 * {@code FinancialRecord.sourceId} (UUID cru, não uma relação para {@code Source}).
 */
@Entity
@Table(name = "match_participant")
@IdClass(MatchParticipant.ParticipantId.class)
public class MatchParticipant {

    public enum Side {
        LEFT, RIGHT
    }

    public enum Role {
        PRINCIPAL, COMPONENT
    }

    @Id
    @Column(name = "match_id", updatable = false)
    private UUID matchId;

    @Id
    @Column(name = "financial_record_id", updatable = false)
    private UUID financialRecordId;

    @Enumerated(EnumType.STRING)
    @Column(name = "side", nullable = false, updatable = false)
    private Side side;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, updatable = false)
    private Role role;

    /** Exigido pelo JPA. Nunca chamado pela aplicação. */
    protected MatchParticipant() {
    }

    public MatchParticipant(UUID matchId, UUID financialRecordId, Side side, Role role) {
        this.matchId = Objects.requireNonNull(matchId, "matchId é obrigatório");
        this.financialRecordId = Objects.requireNonNull(financialRecordId, "financialRecordId é obrigatório");
        this.side = Objects.requireNonNull(side, "side é obrigatório");
        this.role = Objects.requireNonNull(role, "role é obrigatório");
    }

    public UUID matchId() {
        return matchId;
    }

    public UUID financialRecordId() {
        return financialRecordId;
    }

    public Side side() {
        return side;
    }

    public Role role() {
        return role;
    }

    /** Chave composta exigida pelo JPA para {@code @IdClass} — nunca usada fora do provedor. */
    public static final class ParticipantId implements Serializable {
        private static final long serialVersionUID = 1L;

        private UUID matchId;
        private UUID financialRecordId;

        public ParticipantId() {
        }

        public ParticipantId(UUID matchId, UUID financialRecordId) {
            this.matchId = matchId;
            this.financialRecordId = financialRecordId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof ParticipantId that)) {
                return false;
            }
            return Objects.equals(matchId, that.matchId) && Objects.equals(financialRecordId, that.financialRecordId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(matchId, financialRecordId);
        }
    }
}
