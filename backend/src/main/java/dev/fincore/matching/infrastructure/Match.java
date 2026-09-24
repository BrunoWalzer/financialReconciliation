package dev.fincore.matching.infrastructure;

import dev.fincore.matching.domain.AmountEvaluation;
import dev.fincore.matching.domain.MatchEvidence;
import dev.fincore.matching.domain.MatchProposal;
import dev.fincore.shared.identifier.Uuid7;
import dev.fincore.shared.money.Currency;
import dev.fincore.shared.money.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A afirmação persistida de que dois (ou mais) {@code FinancialRecord} representam o mesmo
 * evento financeiro (Domain §5.2, TDS 7.6). {@code residualMinor}/{@code toleranceAbsorbedMinor}
 * são {@code NOT NULL} mesmo quando zero — as duas colunas que impedem valor de "evaporar"
 * silenciosamente (TDS 8.3).
 *
 * <p>Entidade de persistência pura — vive em {@code matching.infrastructure}, não em
 * {@code matching.domain}: nada no motor de matching (que trabalha só com
 * {@link MatchProposal}, {@link AmountEvaluation} etc., VOs puros) referencia esta classe.
 *
 * <p>Só o caminho {@link #automatic} existe neste milestone: criação manual (origin
 * {@code MANUAL}) é responsabilidade da resolução manual (M14), que ainda não existe. A
 * restrição {@code ck_match_manual_requires_justification} (V7) já protege a forma da
 * tabela mesmo sem esse caminho de código.
 *
 * <p>{@code evidence} chega já serializado (JSON de texto) — quem monta o JSON é
 * {@code matching.application}, nunca esta entidade: {@link MatchEvidence} é um record puro
 * de domínio, sem dependência de Jackson, e {@code matching.domain} não deveria carregar uma
 * biblioteca de serialização só para isto (mesmo padrão de {@code AuditEvent.beforeState}).
 *
 * <p>Tem {@code @Version}: para entidades com essa anotação, o Spring Data trata
 * {@code version == 0} como "nova" e chama {@code entityManager.persist()} corretamente por
 * si só — diferente de {@link MatchClaim}/{@link MatchParticipant}, que não têm
 * {@code @Version} e precisam de {@code Persistable} explícito pelo mesmo motivo.
 */
@Entity
@Table(name = "match")
public class Match {

    public enum Origin {
        AUTOMATIC, MANUAL
    }

    public enum Status {
        ACTIVE, SUPERSEDED
    }

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "reconciliation_run_id", updatable = false)
    private UUID reconciliationRunId;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin", nullable = false, updatable = false)
    private Origin origin;

    @Column(name = "rule_id", updatable = false)
    private String ruleId;

    @Column(name = "rule_version", updatable = false)
    private Integer ruleVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, updatable = false)
    private AmountEvaluation.Outcome outcome;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency", nullable = false, length = 3, updatable = false)
    private String currency;

    @Column(name = "gross_expected_minor", nullable = false, updatable = false)
    private long grossExpectedMinor;

    @Column(name = "fee_applied_minor", nullable = false, updatable = false)
    private long feeAppliedMinor;

    @Column(name = "net_expected_minor", nullable = false, updatable = false)
    private long netExpectedMinor;

    @Column(name = "observed_minor", nullable = false, updatable = false)
    private long observedMinor;

    @Enumerated(EnumType.STRING)
    @Column(name = "fee_source", nullable = false, updatable = false)
    private AmountEvaluation.FeeSource feeSource;

    @Column(name = "residual_minor", nullable = false, updatable = false)
    private long residualMinor;

    @Column(name = "tolerance_limit_minor", nullable = false, updatable = false)
    private long toleranceLimitMinor;

    @Column(name = "tolerance_absorbed_minor", nullable = false, updatable = false)
    private long toleranceAbsorbedMinor;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence", updatable = false)
    private String evidence;

    @Column(name = "justification", updatable = false)
    private String justification;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "superseded_at")
    private Instant supersededAt;

    @Column(name = "superseded_by_id")
    private UUID supersededById;

    @Column(name = "recomposition_count", nullable = false)
    private int recompositionCount;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    /** Exigido pelo JPA. Nunca chamado pela aplicação. */
    protected Match() {
    }

    private Match(
            Origin origin, String ruleId, Integer ruleVersion, MatchEvidence.AmountEvaluationEvidence amounts,
            String evidence, Instant createdAt) {
        this.id = Uuid7.generate();
        this.origin = Objects.requireNonNull(origin, "origin é obrigatório");
        this.ruleId = ruleId;
        this.ruleVersion = ruleVersion;
        this.outcome = AmountEvaluation.Outcome.valueOf(amounts.outcome());
        this.status = Status.ACTIVE;
        this.currency = amounts.currency();
        this.grossExpectedMinor = amounts.grossExpectedMinor();
        this.feeAppliedMinor = amounts.feeAppliedMinor();
        this.netExpectedMinor = amounts.netExpectedMinor();
        this.observedMinor = amounts.observedMinor();
        this.feeSource = AmountEvaluation.FeeSource.valueOf(amounts.feeSource());
        this.residualMinor = amounts.residualMinor();
        this.toleranceLimitMinor = amounts.toleranceLimitMinor();
        this.toleranceAbsorbedMinor = amounts.toleranceAbsorbedMinor();
        this.evidence = evidence;
        this.recompositionCount = 0;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt é obrigatório");
    }

    /**
     * O único caminho de criação neste milestone (TDS 11, I-11: automático exige regra e
     * evidência — ambos obrigatórios aqui, nunca nulos). Recebe os valores já no formato
     * plano de {@link MatchEvidence.AmountEvaluationEvidence} (long/String), não
     * {@link AmountEvaluation}: é exatamente o que {@link MatchProposal#evidence()} já
     * carrega, sem reconstruir {@code Money} para depois desmontá-lo de novo.
     */
    public static Match automatic(
            String ruleId, int ruleVersion, MatchEvidence.AmountEvaluationEvidence amounts,
            String evidenceJson, Instant createdAt) {
        Objects.requireNonNull(ruleId, "ruleId é obrigatório para um match automático");
        Objects.requireNonNull(evidenceJson, "evidence é obrigatório para um match automático");
        return new Match(Origin.AUTOMATIC, ruleId, ruleVersion, amounts, evidenceJson, createdAt);
    }

    public UUID id() {
        return id;
    }

    public UUID reconciliationRunId() {
        return reconciliationRunId;
    }

    public Origin origin() {
        return origin;
    }

    public String ruleId() {
        return ruleId;
    }

    public Integer ruleVersion() {
        return ruleVersion;
    }

    public AmountEvaluation.Outcome outcome() {
        return outcome;
    }

    public Status status() {
        return status;
    }

    public Currency currency() {
        return Currency.valueOf(currency);
    }

    public Money grossExpected() {
        return new Money(grossExpectedMinor, currency());
    }

    public Money feeApplied() {
        return new Money(feeAppliedMinor, currency());
    }

    public Money netExpected() {
        return new Money(netExpectedMinor, currency());
    }

    public Money observed() {
        return new Money(observedMinor, currency());
    }

    public AmountEvaluation.FeeSource feeSource() {
        return feeSource;
    }

    public Money residual() {
        return new Money(residualMinor, currency());
    }

    public Money toleranceLimit() {
        return new Money(toleranceLimitMinor, currency());
    }

    public Money toleranceAbsorbed() {
        return new Money(toleranceAbsorbedMinor, currency());
    }

    public String evidence() {
        return evidence;
    }

    public String justification() {
        return justification;
    }

    public UUID createdBy() {
        return createdBy;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant supersededAt() {
        return supersededAt;
    }

    public UUID supersededById() {
        return supersededById;
    }

    public int recompositionCount() {
        return recompositionCount;
    }

    public long version() {
        return version;
    }
}
