package dev.fincore.ingestion.domain;

import dev.fincore.shared.identifier.Uuid7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;

/**
 * Uma linha que não virou evidência, preservada com número e conteúdo bruto (Domain §9.3)
 * — a contrapartida obrigatória da importação parcial. Nunca some; imutável após criada
 * (trigger {@code fincore_reject_mutation}, V5).
 */
@Entity
@Table(name = "rejected_record")
public class RejectedRecord {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "import_batch_id", nullable = false, updatable = false)
    private UUID importBatchId;

    @Column(name = "line_number", nullable = false, updatable = false)
    private int lineNumber;

    @Column(name = "raw_line", nullable = false, updatable = false)
    private String rawLine;

    @Column(name = "reason_code", nullable = false, updatable = false)
    private String reasonCode;

    @Column(name = "reason_detail", updatable = false)
    private String reasonDetail;

    @Column(name = "extracted_amount_minor", updatable = false)
    private Long extractedAmountMinor;

    /** Exigido pelo JPA. Nunca chamado pela aplicação. */
    protected RejectedRecord() {
    }

    public RejectedRecord(
            UUID importBatchId, int lineNumber, String rawLine, RejectionReasonCode reasonCode,
            String reasonDetail, Long extractedAmountMinor) {
        this.id = Uuid7.generate();
        this.importBatchId = Objects.requireNonNull(importBatchId, "importBatchId é obrigatório");
        this.lineNumber = lineNumber;
        this.rawLine = Objects.requireNonNull(rawLine, "rawLine é obrigatório");
        this.reasonCode = Objects.requireNonNull(reasonCode, "reasonCode é obrigatório").name();
        this.reasonDetail = reasonDetail;
        this.extractedAmountMinor = extractedAmountMinor;
    }

    public UUID id() {
        return id;
    }

    public UUID importBatchId() {
        return importBatchId;
    }

    public int lineNumber() {
        return lineNumber;
    }

    public String rawLine() {
        return rawLine;
    }

    public RejectionReasonCode reasonCode() {
        return RejectionReasonCode.valueOf(reasonCode);
    }

    public String reasonDetail() {
        return reasonDetail;
    }

    public Long extractedAmountMinor() {
        return extractedAmountMinor;
    }
}
