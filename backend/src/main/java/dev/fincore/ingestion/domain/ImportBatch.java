package dev.fincore.ingestion.domain;

import dev.fincore.shared.identifier.Uuid7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Um arquivo recebido de uma fonte, com seu resultado (Domain §9.1). Mutável até o estado
 * terminal (Domain §6.3) — diferente de {@code FinancialRecord}, não é protegida por
 * trigger de imutabilidade; a proteção aqui é a máquina de estados: cada transição exige
 * o estado de origem certo, ou lança.
 *
 * <p><b>Não existe rollback de importação</b> (Domain §9.3): não há método que volte um
 * estado terminal para um anterior, nem que apague um {@code FinancialRecord} já persistido.
 */
@Entity
@Table(name = "import_batch")
public class ImportBatch {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "source_id", nullable = false, updatable = false)
    private UUID sourceId;

    @Column(name = "original_filename", nullable = false, updatable = false)
    private String originalFilename;

    // CHAR(64) real no banco — JdbcTypeCode explícito porque o padrão do Hibernate para
    // String é VARCHAR (mesmo ajuste já feito em FinancialRecord.currency/fingerprint, M4).
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "content_sha256", nullable = false, length = 64, updatable = false)
    private String contentSha256;

    @Column(name = "byte_size", nullable = false, updatable = false)
    private long byteSize;

    @Column(name = "reference_date", nullable = false, updatable = false)
    private LocalDate referenceDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ImportStatus status;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "reimport_of_id", updatable = false)
    private UUID reimportOfId;

    @Column(name = "reimport_reason", updatable = false)
    private String reimportReason;

    @Column(name = "storage_key", nullable = false, updatable = false)
    private String storageKey;

    @Column(name = "total_lines")
    private Integer totalLines;

    @Column(name = "accepted_count")
    private Integer acceptedCount;

    @Column(name = "rejected_count")
    private Integer rejectedCount;

    @Column(name = "already_existing_count")
    private Integer alreadyExistingCount;

    @Column(name = "uploaded_by", nullable = false, updatable = false)
    private UUID uploadedBy;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "correlation_id", updatable = false)
    private String correlationId;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    /** Exigido pelo JPA. Nunca chamado pela aplicação. */
    protected ImportBatch() {
    }

    public ImportBatch(
            UUID sourceId,
            String originalFilename,
            String contentSha256,
            long byteSize,
            LocalDate referenceDate,
            String storageKey,
            UUID uploadedBy,
            Instant uploadedAt,
            UUID reimportOfId,
            String reimportReason,
            String correlationId) {
        this.id = Uuid7.generate();
        this.sourceId = Objects.requireNonNull(sourceId, "sourceId é obrigatório");
        this.originalFilename = requireNonBlank(originalFilename, "originalFilename");
        this.contentSha256 = requireNonBlank(contentSha256, "contentSha256");
        if (byteSize < 0) {
            throw new IllegalArgumentException("byteSize não pode ser negativo");
        }
        this.byteSize = byteSize;
        this.referenceDate = Objects.requireNonNull(referenceDate, "referenceDate é obrigatório");
        this.storageKey = requireNonBlank(storageKey, "storageKey");
        this.uploadedBy = Objects.requireNonNull(uploadedBy, "uploadedBy é obrigatório");
        this.uploadedAt = Objects.requireNonNull(uploadedAt, "uploadedAt é obrigatório");
        // Reimportação intencional exige motivo textual (Domain §9.3) — nunca um clique vazio.
        if (reimportOfId != null && (reimportReason == null || reimportReason.trim().length() < 10)) {
            throw new IllegalArgumentException("reimportReason precisa ter ao menos 10 caracteres quando reimportOfId é informado");
        }
        this.reimportOfId = reimportOfId;
        this.reimportReason = reimportReason;
        this.correlationId = correlationId;
        this.status = ImportStatus.RECEIVED;
    }

    public void startProcessing(Instant now) {
        requireStatus(ImportStatus.RECEIVED);
        this.status = ImportStatus.PROCESSING;
        this.startedAt = now;
    }

    /** Cabeçalho incompatível, ou arquivo vazio/só-cabeçalho (Domain §9.3) — zero registros, zero rejeitos. */
    public void rejectStructurally(String reasonCode, Instant now) {
        requireStatus(ImportStatus.PROCESSING);
        this.status = ImportStatus.REJECTED;
        this.rejectionReason = requireNonBlank(reasonCode, "reasonCode");
        this.totalLines = 0;
        this.acceptedCount = 0;
        this.rejectedCount = 0;
        this.alreadyExistingCount = 0;
        this.finishedAt = now;
    }

    /**
     * Encerra após o laço de lotes. {@code REJECTED} quando nenhuma linha foi aceita —
     * "todas inválidas" (TDS 9.7) — mesmo com linhas rejeitadas persistidas;
     * {@code COMPLETED_WITH_REJECTS} quando há aceitas e rejeitadas; {@code COMPLETED}
     * quando todas foram aceitas.
     */
    public void complete(int totalLines, int acceptedCount, int rejectedCount, int alreadyExistingCount, Instant now) {
        requireStatus(ImportStatus.PROCESSING);
        if (acceptedCount == 0 && rejectedCount > 0) {
            this.status = ImportStatus.REJECTED;
            this.rejectionReason = "ALL_LINES_INVALID";
        } else {
            this.status = rejectedCount > 0 ? ImportStatus.COMPLETED_WITH_REJECTS : ImportStatus.COMPLETED;
        }
        this.totalLines = totalLines;
        this.acceptedCount = acceptedCount;
        this.rejectedCount = rejectedCount;
        this.alreadyExistingCount = alreadyExistingCount;
        this.finishedAt = now;
    }

    /** Falha de processamento (TDS 9.5) — registros já persistidos nos lotes anteriores permanecem. */
    public void fail(String reason, Instant now) {
        requireStatus(ImportStatus.RECEIVED, ImportStatus.PROCESSING);
        this.status = ImportStatus.FAILED;
        this.rejectionReason = reason;
        this.finishedAt = now;
    }

    private void requireStatus(ImportStatus... allowed) {
        for (ImportStatus candidate : allowed) {
            if (status == candidate) {
                return;
            }
        }
        throw new IllegalStateException("transição inválida a partir de " + status);
    }

    public UUID id() {
        return id;
    }

    public UUID sourceId() {
        return sourceId;
    }

    public String originalFilename() {
        return originalFilename;
    }

    public String contentSha256() {
        return contentSha256;
    }

    public long byteSize() {
        return byteSize;
    }

    public LocalDate referenceDate() {
        return referenceDate;
    }

    public ImportStatus status() {
        return status;
    }

    public String rejectionReason() {
        return rejectionReason;
    }

    public UUID reimportOfId() {
        return reimportOfId;
    }

    public String reimportReason() {
        return reimportReason;
    }

    public String storageKey() {
        return storageKey;
    }

    public Integer totalLines() {
        return totalLines;
    }

    public Integer acceptedCount() {
        return acceptedCount;
    }

    public Integer rejectedCount() {
        return rejectedCount;
    }

    public Integer alreadyExistingCount() {
        return alreadyExistingCount;
    }

    public UUID uploadedBy() {
        return uploadedBy;
    }

    public Instant uploadedAt() {
        return uploadedAt;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant finishedAt() {
        return finishedAt;
    }

    public String correlationId() {
        return correlationId;
    }

    public long version() {
        return version;
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " é obrigatório");
        }
        return value;
    }
}
