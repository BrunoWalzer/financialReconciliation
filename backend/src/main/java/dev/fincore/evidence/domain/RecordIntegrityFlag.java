package dev.fincore.evidence.domain;

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

/**
 * O que o sistema aprendeu sobre um {@link FinancialRecord}, fora dele (Domain §5.2, TDS
 * 7.5). Nasce sempre com {@code resolvedAt} nulo — só a resolução manual de divergência
 * (M14) preenche {@code resolvedAt}/{@code resolvedByDivergenceId}; esta entidade não tem
 * método para isso porque {@code Divergence} não existe ainda neste milestone (M7).
 *
 * <p>Não é protegida por trigger de imutabilidade como {@link FinancialRecord}: ao contrário
 * dele, esta linha É mutável mais tarde (M14 resolve a flag). Neste milestone só é criada,
 * nunca atualizada — a idempotência da varredura vem do único parcial
 * {@code uq_record_integrity_flag_open} (V4), não de uma trigger.
 */
@Entity
@Table(name = "record_integrity_flag")
public class RecordIntegrityFlag {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "financial_record_id", nullable = false, updatable = false)
    private UUID financialRecordId;

    @Enumerated(EnumType.STRING)
    @Column(name = "flag_type", nullable = false, updatable = false)
    private RecordIntegrityFlagType flagType;

    @Column(name = "detected_at", nullable = false, updatable = false)
    private Instant detectedAt;

    @Column(name = "detected_by_batch_id", nullable = false, updatable = false)
    private UUID detectedByBatchId;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by_divergence_id")
    private UUID resolvedByDivergenceId;

    /** Exigido pelo JPA. Nunca chamado pela aplicação. */
    protected RecordIntegrityFlag() {
    }

    public RecordIntegrityFlag(
            UUID financialRecordId, RecordIntegrityFlagType flagType, UUID detectedByBatchId, Instant detectedAt) {
        this.id = Uuid7.generate();
        this.financialRecordId = Objects.requireNonNull(financialRecordId, "financialRecordId é obrigatório");
        this.flagType = Objects.requireNonNull(flagType, "flagType é obrigatório");
        this.detectedByBatchId = Objects.requireNonNull(detectedByBatchId, "detectedByBatchId é obrigatório");
        this.detectedAt = Objects.requireNonNull(detectedAt, "detectedAt é obrigatório");
        this.resolvedAt = null;
        this.resolvedByDivergenceId = null;
    }

    public UUID id() {
        return id;
    }

    public UUID financialRecordId() {
        return financialRecordId;
    }

    public RecordIntegrityFlagType flagType() {
        return flagType;
    }

    public Instant detectedAt() {
        return detectedAt;
    }

    public UUID detectedByBatchId() {
        return detectedByBatchId;
    }

    public Instant resolvedAt() {
        return resolvedAt;
    }

    public UUID resolvedByDivergenceId() {
        return resolvedByDivergenceId;
    }
}
