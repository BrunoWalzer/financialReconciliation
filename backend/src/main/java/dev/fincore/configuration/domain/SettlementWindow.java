package dev.fincore.configuration.domain;

import dev.fincore.shared.identifier.Uuid7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.Objects;
import java.util.UUID;

/**
 * Prazo esperado de liquidação por par de fontes e meio de pagamento (Domain D2; TDS 7.3).
 * Decisão fechada do domínio: por meio de pagamento, não por fonte — débito e crédito têm
 * prazos muito diferentes, e uma janela única geraria ausências falsas.
 * {@code paymentMethod} nulo é a janela padrão para qualquer meio não listado
 * explicitamente.
 */
@Entity
@Table(name = "settlement_window")
public class SettlementWindow {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "source_pair_id", nullable = false, updatable = false)
    private UUID sourcePairId;

    @Column(name = "payment_method", updatable = false)
    private String paymentMethod;

    @Column(name = "min_days", nullable = false)
    private int minDays;

    @Column(name = "max_days", nullable = false)
    private int maxDays;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    /** Exigido pelo JPA. Nunca chamado pela aplicação. */
    protected SettlementWindow() {
    }

    public SettlementWindow(UUID sourcePairId, String paymentMethod, int minDays, int maxDays) {
        this.id = Uuid7.generate();
        this.sourcePairId = Objects.requireNonNull(sourcePairId, "sourcePairId é obrigatório");
        this.paymentMethod = paymentMethod;
        setDays(minDays, maxDays);
    }

    public void update(int minDays, int maxDays) {
        setDays(minDays, maxDays);
    }

    private void setDays(int minDays, int maxDays) {
        if (minDays < 0) {
            throw new IllegalArgumentException("minDays não pode ser negativo");
        }
        if (maxDays < minDays) {
            throw new IllegalArgumentException("maxDays não pode ser menor que minDays");
        }
        this.minDays = minDays;
        this.maxDays = maxDays;
    }

    public UUID id() {
        return id;
    }

    public UUID sourcePairId() {
        return sourcePairId;
    }

    public String paymentMethod() {
        return paymentMethod;
    }

    public int minDays() {
        return minDays;
    }

    public int maxDays() {
        return maxDays;
    }

    public long version() {
        return version;
    }
}
