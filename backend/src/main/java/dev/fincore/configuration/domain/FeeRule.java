package dev.fincore.configuration.domain;

import dev.fincore.shared.identifier.Uuid7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.Objects;
import java.util.UUID;

/**
 * Taxa esperada por fonte e meio de pagamento (Domain §14; TDS 7.3) — o que define
 * "cobrança indevida" (Domain §27.2). Percentual em <em>basis points</em> inteiros: 250 =
 * 2,5%. {@code paymentMethod} nulo significa "qualquer meio" — a unicidade de regra
 * ativa trata isso como um valor só (ver {@code V3__configuration.sql}).
 */
@Entity
@Table(name = "fee_rule")
public class FeeRule {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "source_id", nullable = false, updatable = false)
    private UUID sourceId;

    @Column(name = "payment_method", updatable = false)
    private String paymentMethod;

    @Column(name = "percentage_bp", nullable = false)
    private int percentageBp;

    @Column(name = "fixed_amount_minor", nullable = false)
    private long fixedAmountMinor;

    @Enumerated(EnumType.STRING)
    @Column(name = "rounding_mode", nullable = false)
    private RoundingMode roundingMode;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    /** Exigido pelo JPA. Nunca chamado pela aplicação. */
    protected FeeRule() {
    }

    public FeeRule(UUID sourceId, String paymentMethod, int percentageBp, long fixedAmountMinor, RoundingMode roundingMode) {
        this.id = Uuid7.generate();
        this.sourceId = Objects.requireNonNull(sourceId, "sourceId é obrigatório");
        this.paymentMethod = paymentMethod;
        setPercentageBp(percentageBp);
        setFixedAmountMinor(fixedAmountMinor);
        this.roundingMode = Objects.requireNonNull(roundingMode, "roundingMode é obrigatório");
        this.active = true;
    }

    /** Desativa a regra — não some do histórico, só deixa de valer para taxas novas. */
    public void deactivate() {
        this.active = false;
    }

    public void update(int percentageBp, long fixedAmountMinor, RoundingMode roundingMode) {
        setPercentageBp(percentageBp);
        setFixedAmountMinor(fixedAmountMinor);
        this.roundingMode = Objects.requireNonNull(roundingMode, "roundingMode é obrigatório");
    }

    private void setPercentageBp(int percentageBp) {
        if (percentageBp < 0 || percentageBp > 10_000) {
            throw new IllegalArgumentException("percentageBp precisa estar entre 0 e 10000");
        }
        this.percentageBp = percentageBp;
    }

    private void setFixedAmountMinor(long fixedAmountMinor) {
        if (fixedAmountMinor < 0) {
            throw new IllegalArgumentException("fixedAmountMinor não pode ser negativo");
        }
        this.fixedAmountMinor = fixedAmountMinor;
    }

    public UUID id() {
        return id;
    }

    public UUID sourceId() {
        return sourceId;
    }

    public String paymentMethod() {
        return paymentMethod;
    }

    public int percentageBp() {
        return percentageBp;
    }

    public long fixedAmountMinor() {
        return fixedAmountMinor;
    }

    public RoundingMode roundingMode() {
        return roundingMode;
    }

    public boolean active() {
        return active;
    }

    public long version() {
        return version;
    }
}
