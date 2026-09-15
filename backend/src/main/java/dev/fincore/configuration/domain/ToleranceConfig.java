package dev.fincore.configuration.domain;

import dev.fincore.shared.identifier.Uuid7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Quanto de diferença um par de fontes tolera antes de virar divergência (Domain §14;
 * TDS 7.3) — "a operação mais sensível do sistema" (Domain §27.2): muda o que o sistema
 * considera aceitável em <em>todas</em> as execuções futuras.
 *
 * <p><b>Nunca encerra divergência aberta</b> (FD-6, Domain §14.4) — essa regra não é
 * imposta por este agregado; é imposta pela ausência de qualquer caminho, em qualquer
 * módulo, de configuração para divergência. Este módulo não conhece {@code divergence}
 * (TDS 4.2 não lista essa aresta).
 *
 * <p>Mutável, com optimistic locking ({@code @Version}) — TDS 15.1: "Configuração:
 * Optimistic, baixa frequência, alta sensibilidade". {@code updatedBy} não tem FK para
 * {@code app_user}: {@code configuration} não depende de {@code identity} (mesmo
 * raciocínio de FD-7).
 */
@Entity
@Table(name = "tolerance_config")
public class ToleranceConfig {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "source_pair_id", nullable = false, updatable = false)
    private UUID sourcePairId;

    @Column(name = "absolute_amount_minor", nullable = false)
    private long absoluteAmountMinor;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "aggregate_alert_threshold_minor")
    private Long aggregateAlertThresholdMinor;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    /** Exigido pelo JPA. Nunca chamado pela aplicação. */
    protected ToleranceConfig() {
    }

    public ToleranceConfig(
            UUID sourcePairId,
            long absoluteAmountMinor,
            String currency,
            Long aggregateAlertThresholdMinor,
            UUID updatedBy,
            Instant now) {
        this.id = Uuid7.generate();
        this.sourcePairId = Objects.requireNonNull(sourcePairId, "sourcePairId é obrigatório");
        applyValues(absoluteAmountMinor, currency, aggregateAlertThresholdMinor, updatedBy, now);
    }

    /** Altera os parâmetros. Quem chama audita antes/depois (TDS 16.1) — este método não audita. */
    public void update(long absoluteAmountMinor, String currency, Long aggregateAlertThresholdMinor, UUID updatedBy, Instant now) {
        applyValues(absoluteAmountMinor, currency, aggregateAlertThresholdMinor, updatedBy, now);
    }

    private void applyValues(
            long absoluteAmountMinor, String currency, Long aggregateAlertThresholdMinor, UUID updatedBy, Instant now) {
        if (absoluteAmountMinor < 0) {
            throw new IllegalArgumentException("absoluteAmountMinor não pode ser negativo");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("currency é obrigatório");
        }
        this.absoluteAmountMinor = absoluteAmountMinor;
        this.currency = currency;
        this.aggregateAlertThresholdMinor = aggregateAlertThresholdMinor;
        this.updatedBy = updatedBy;
        this.updatedAt = Objects.requireNonNull(now, "now é obrigatório");
    }

    public UUID id() {
        return id;
    }

    public UUID sourcePairId() {
        return sourcePairId;
    }

    public long absoluteAmountMinor() {
        return absoluteAmountMinor;
    }

    public String currency() {
        return currency;
    }

    public Long aggregateAlertThresholdMinor() {
        return aggregateAlertThresholdMinor;
    }

    public UUID updatedBy() {
        return updatedBy;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public long version() {
        return version;
    }
}
