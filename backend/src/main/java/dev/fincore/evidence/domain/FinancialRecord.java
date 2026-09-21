package dev.fincore.evidence.domain;

import dev.fincore.shared.identifier.Uuid7;
import dev.fincore.shared.money.Currency;
import dev.fincore.shared.money.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A evidência financeira — o átomo do sistema (Domain §5.2). Uma linha de um arquivo de uma
 * fonte, normalizada. <b>Imutável após a criação</b>: sem setters, sem {@code @Version}, sem
 * {@code updated_at} — a ausência é declaração de intenção (TDS 7.5). A proteção real contra
 * mutação é a trigger {@code fincore_reject_mutation} no PostgreSQL (V4); a ausência de
 * setters aqui é a segunda camada, que impede o próprio código Java de tentar.
 *
 * <p><b>Não tem status de conciliação.</b> Nenhum campo aqui representa {@code RECONCILED},
 * {@code MATCHED}, {@code PENDING} ou equivalente — esse conceito pertence a {@code Match} e
 * {@code Divergence} (M9+), entidades com ciclo de vida próprio que este módulo não conhece
 * (Domain §5.2, §5.6).
 *
 * <p>{@code grossAmount}, {@code declaredFeeAmount} e {@code netAmount} compartilham uma
 * única coluna {@code currency} (TDS 7.5 — não há uma coluna de moeda por valor): os três
 * {@link Money} são reconstruídos a partir dos três montantes em minor units mais essa moeda
 * comum, nunca persistidos como três VOs independentes.
 */
@Entity
@Table(name = "financial_record")
public class FinancialRecord {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "source_id", nullable = false, updatable = false)
    private UUID sourceId;

    @Column(name = "import_batch_id", nullable = false, updatable = false)
    private UUID importBatchId;

    @Column(name = "line_number", nullable = false, updatable = false)
    private int lineNumber;

    @Column(name = "external_id", updatable = false)
    private String externalId;

    @Column(name = "correlation_key", updatable = false)
    private String correlationKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, updatable = false)
    private Direction direction;

    @Enumerated(EnumType.STRING)
    @Column(name = "record_type", nullable = false, updatable = false)
    private RecordType recordType;

    @Column(name = "gross_amount_minor", nullable = false, updatable = false)
    private long grossAmountMinor;

    @Column(name = "declared_fee_amount_minor", updatable = false)
    private Long declaredFeeAmountMinor;

    @Column(name = "net_amount_minor", updatable = false)
    private Long netAmountMinor;

    // CHAR(3) real no banco (TDS 7: "dinheiro em BIGINT... com CHAR(3) de moeda ao lado") —
    // JdbcTypeCode explícito porque o padrão do Hibernate para String é VARCHAR, que não
    // bate com CHAR na validação de schema (o mesmo problema já visto em V3/tolerance_config,
    // desta vez resolvido mantendo o tipo do documento em vez de trocá-lo).
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency", nullable = false, length = 3, updatable = false)
    private String currency;

    @Column(name = "business_date", nullable = false, updatable = false)
    private LocalDate businessDate;

    @Column(name = "source_timestamp", updatable = false)
    private Instant sourceTimestamp;

    @Column(name = "counterparty_document", updatable = false)
    private String counterpartyDocument;

    @Column(name = "payment_method", updatable = false)
    private String paymentMethod;

    @Column(name = "description", updatable = false)
    private String description;

    @Column(name = "description_normalized", updatable = false)
    private String descriptionNormalized;

    @Column(name = "raw_line", nullable = false, updatable = false)
    private String rawLine;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "fingerprint", nullable = false, length = 64, updatable = false)
    private String fingerprint;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Exigido pelo JPA. Nunca chamado pela aplicação. */
    protected FinancialRecord() {
    }

    public FinancialRecord(
            UUID sourceId,
            UUID importBatchId,
            int lineNumber,
            String externalId,
            String correlationKey,
            Direction direction,
            RecordType recordType,
            Money grossAmount,
            Money declaredFeeAmount,
            Money netAmount,
            LocalDate businessDate,
            Instant sourceTimestamp,
            String counterpartyDocument,
            String paymentMethod,
            String description,
            String descriptionNormalized,
            String rawLine,
            String fingerprint,
            Instant createdAt) {
        this.id = Uuid7.generate();
        this.sourceId = Objects.requireNonNull(sourceId, "sourceId é obrigatório");
        this.importBatchId = Objects.requireNonNull(importBatchId, "importBatchId é obrigatório");
        this.lineNumber = lineNumber;
        this.externalId = externalId;
        this.correlationKey = correlationKey;
        this.direction = Objects.requireNonNull(direction, "direction é obrigatório");
        this.recordType = Objects.requireNonNull(recordType, "recordType é obrigatório");

        Objects.requireNonNull(grossAmount, "grossAmount é obrigatório");
        if (grossAmount.isZero()) {
            throw new IllegalArgumentException("grossAmount não pode ser zero");
        }
        // TDS 7.5: CHECK (currency IN ('BRL')) — Currency tem uma segunda constante (USD)
        // só para o teste de Money provar a rejeição de moeda cruzada sem mock; nenhum
        // FinancialRecord real pode usá-la.
        if (grossAmount.currency() != Currency.BRL) {
            throw new IllegalArgumentException("currency só aceita BRL no MVP");
        }
        if (declaredFeeAmount != null && declaredFeeAmount.currency() != grossAmount.currency()) {
            throw new IllegalArgumentException("declaredFeeAmount precisa ter a mesma moeda de grossAmount");
        }
        if (netAmount != null && netAmount.currency() != grossAmount.currency()) {
            throw new IllegalArgumentException("netAmount precisa ter a mesma moeda de grossAmount");
        }
        // Coerência estrutural das duas fontes do MVP (C-6 / TDS 7.5): um valor líquido sem
        // taxa declarada não é representável por nenhum dos dois layouts do Implementation Plan.
        if (netAmount != null && declaredFeeAmount == null) {
            throw new IllegalArgumentException("netAmount exige declaredFeeAmount");
        }

        this.grossAmountMinor = grossAmount.amountMinor();
        this.declaredFeeAmountMinor = declaredFeeAmount == null ? null : declaredFeeAmount.amountMinor();
        this.netAmountMinor = netAmount == null ? null : netAmount.amountMinor();
        this.currency = grossAmount.currency().name();

        this.businessDate = Objects.requireNonNull(businessDate, "businessDate é obrigatório");
        this.sourceTimestamp = sourceTimestamp;
        this.counterpartyDocument = counterpartyDocument;
        this.paymentMethod = paymentMethod;
        this.description = description;
        this.descriptionNormalized = descriptionNormalized;
        this.rawLine = requireNonBlank(rawLine, "rawLine");
        this.fingerprint = requireNonBlank(fingerprint, "fingerprint");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt é obrigatório");
    }

    public UUID id() {
        return id;
    }

    public UUID sourceId() {
        return sourceId;
    }

    public UUID importBatchId() {
        return importBatchId;
    }

    public int lineNumber() {
        return lineNumber;
    }

    public String externalId() {
        return externalId;
    }

    public String correlationKey() {
        return correlationKey;
    }

    public Direction direction() {
        return direction;
    }

    public RecordType recordType() {
        return recordType;
    }

    public Money grossAmount() {
        return new Money(grossAmountMinor, currency());
    }

    public Money declaredFeeAmount() {
        return declaredFeeAmountMinor == null ? null : new Money(declaredFeeAmountMinor, currency());
    }

    public Money netAmount() {
        return netAmountMinor == null ? null : new Money(netAmountMinor, currency());
    }

    public Currency currency() {
        return Currency.valueOf(currency);
    }

    public LocalDate businessDate() {
        return businessDate;
    }

    public Instant sourceTimestamp() {
        return sourceTimestamp;
    }

    public String counterpartyDocument() {
        return counterpartyDocument;
    }

    public String paymentMethod() {
        return paymentMethod;
    }

    public String description() {
        return description;
    }

    public String descriptionNormalized() {
        return descriptionNormalized;
    }

    public String rawLine() {
        return rawLine;
    }

    public String fingerprint() {
        return fingerprint;
    }

    public Instant createdAt() {
        return createdAt;
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " é obrigatório");
        }
        return value;
    }
}
