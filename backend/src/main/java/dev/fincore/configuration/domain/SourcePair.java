package dev.fincore.configuration.domain;

import dev.fincore.shared.identifier.Uuid7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;

/**
 * Duas fontes cuja evidência será conciliada entre si (TDS 7.3). Não tem endpoint de
 * mutação no M3 — só nasce por seed de referência.
 */
@Entity
@Table(name = "source_pair")
public class SourcePair {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "left_source_id", nullable = false, updatable = false)
    private UUID leftSourceId;

    @Column(name = "right_source_id", nullable = false, updatable = false)
    private UUID rightSourceId;

    @Column(name = "code", nullable = false, updatable = false)
    private String code;

    /** Exigido pelo JPA. Nunca chamado pela aplicação. */
    protected SourcePair() {
    }

    public SourcePair(UUID leftSourceId, UUID rightSourceId, String code) {
        Objects.requireNonNull(leftSourceId, "leftSourceId é obrigatório");
        Objects.requireNonNull(rightSourceId, "rightSourceId é obrigatório");
        if (leftSourceId.equals(rightSourceId)) {
            throw new IllegalArgumentException("leftSourceId e rightSourceId precisam ser fontes distintas");
        }
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code é obrigatório");
        }
        this.id = Uuid7.generate();
        this.leftSourceId = leftSourceId;
        this.rightSourceId = rightSourceId;
        this.code = code;
    }

    public UUID id() {
        return id;
    }

    public UUID leftSourceId() {
        return leftSourceId;
    }

    public UUID rightSourceId() {
        return rightSourceId;
    }

    public String code() {
        return code;
    }
}
