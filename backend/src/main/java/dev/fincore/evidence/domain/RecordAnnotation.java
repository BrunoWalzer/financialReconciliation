package dev.fincore.evidence.domain;

import dev.fincore.shared.identifier.Uuid7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A resposta a "o operador sabe o valor correto" sem tocar na evidência (TDS 7.5).
 * Append-only: uma vez criada, nunca muda (trigger {@code fincore_reject_mutation} em V4).
 *
 * <p>{@code authorId} não tem FK para {@code app_user} — {@code evidence} não depende de
 * {@code identity} (TDS 4.2/4.3), mesmo raciocínio já aplicado a
 * {@code tolerance_config.updated_by} no M3.
 */
@Entity
@Table(name = "record_annotation")
public class RecordAnnotation {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "financial_record_id", nullable = false, updatable = false)
    private UUID financialRecordId;

    @Column(name = "author_id", nullable = false, updatable = false)
    private UUID authorId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "text", nullable = false, updatable = false)
    private String text;

    /** Exigido pelo JPA. Nunca chamado pela aplicação. */
    protected RecordAnnotation() {
    }

    public RecordAnnotation(UUID financialRecordId, UUID authorId, String text, Instant createdAt) {
        this.id = Uuid7.generate();
        this.financialRecordId = Objects.requireNonNull(financialRecordId, "financialRecordId é obrigatório");
        this.authorId = Objects.requireNonNull(authorId, "authorId é obrigatório");
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text é obrigatório");
        }
        this.text = text;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt é obrigatório");
    }

    public UUID id() {
        return id;
    }

    public UUID financialRecordId() {
        return financialRecordId;
    }

    public UUID authorId() {
        return authorId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public String text() {
        return text;
    }
}
