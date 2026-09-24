package dev.fincore.matching.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * A garantia de exclusividade do sistema inteiro (I-5, TDS 7.6): {@code financialRecordId} é
 * a {@code PRIMARY KEY} da tabela, não um índice único sobre outra PK. Duas transações
 * concorrentes tentando reivindicar o mesmo {@code FinancialRecord} colidem na violação
 * dessa chave primária — nunca num {@code SELECT} antes do {@code INSERT} na aplicação
 * (Implementation Plan M10: "não mockar a constraint").
 *
 * <p>Entidade de persistência pura — vive em {@code matching.infrastructure}, não em
 * {@code matching.domain} (ao contrário de {@code evidence.domain.FinancialRecord}, que o
 * motor de matching referencia diretamente): nada em {@code matching.domain} conhece esta
 * classe, então ela não precisa respeitar a regra ArchUnit de domínio livre de Spring.
 *
 * <p>Implementa {@link Persistable} de propósito: sem {@code @Version} e com
 * {@code financialRecordId} sempre não nulo na construção (é o id de um
 * {@code FinancialRecord} já existente, nunca gerado aqui), a heurística padrão do Spring
 * Data ("id nulo → nova entidade") sempre veria esta entidade como já existente e chamaria
 * {@code entityManager.merge()} em vez de {@code persist()}. {@code merge()} faz um
 * {@code SELECT} interno antes de decidir inserir ou atualizar — sob concorrência real, duas
 * transações podem ver "linha ausente" ao mesmo tempo (antes de qualquer commit) e as duas
 * seguirem adiante, escapando exatamente da checagem atômica de PK que a I-5 exige. Descoberto
 * por um teste de concorrência real que falhava de forma intermitente (ver relatório do M9,
 * seção Desvios) — as duas transações "venciam" porque nenhuma das duas de fato executava um
 * {@code INSERT} puro sujeito à constraint no momento em que a decisão importava.
 */
@Entity
@Table(name = "match_claim")
public class MatchClaim implements Persistable<UUID> {

    @Id
    @Column(name = "financial_record_id", updatable = false)
    private UUID financialRecordId;

    @Column(name = "match_id", nullable = false, updatable = false)
    private UUID matchId;

    @Column(name = "claimed_at", nullable = false, updatable = false)
    private Instant claimedAt;

    @Transient
    private boolean isNew = true;

    /** Exigido pelo JPA. Nunca chamado pela aplicação. */
    protected MatchClaim() {
    }

    public MatchClaim(UUID financialRecordId, UUID matchId, Instant claimedAt) {
        this.financialRecordId = Objects.requireNonNull(financialRecordId, "financialRecordId é obrigatório");
        this.matchId = Objects.requireNonNull(matchId, "matchId é obrigatório");
        this.claimedAt = Objects.requireNonNull(claimedAt, "claimedAt é obrigatório");
        this.isNew = true;
    }

    @Override
    public UUID getId() {
        return financialRecordId;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
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
