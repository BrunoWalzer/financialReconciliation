package dev.fincore.matching.domain;

import dev.fincore.evidence.domain.Direction;
import dev.fincore.evidence.domain.FinancialRecord;
import dev.fincore.evidence.domain.RecordType;
import dev.fincore.shared.identifier.Uuid7;
import dev.fincore.shared.money.Currency;
import dev.fincore.shared.money.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Um builder mínimo de {@link FinancialRecord} só para os testes do motor de matching — o
 * construtor de 19 posições é correto para o domínio, mas ilegível repetido em dezenas de
 * cenários. Cada campo tem um padrão plausível; os testes sobrescrevem só o que importa
 * para o cenário.
 */
public final class FinancialRecordFixture {

    private UUID sourceId = Uuid7.generate();
    private UUID importBatchId = Uuid7.generate();
    private int lineNumber = 1;
    private String externalId;
    private String correlationKey;
    private Direction direction = Direction.CREDIT;
    private RecordType recordType = RecordType.SALE;
    private Money grossAmount = new Money(50_000, Currency.BRL);
    private Money declaredFeeAmount;
    private Money netAmount;
    private LocalDate businessDate = LocalDate.of(2026, 9, 10);
    private Instant sourceTimestamp;
    private String counterpartyDocument;
    private String paymentMethod = "CREDIT_CARD";
    private String description = "descrição";
    private String descriptionNormalized = "DESCRICAO";
    private String rawLine = "linha bruta";
    private String fingerprint = randomFingerprint();
    private Instant createdAt = Instant.parse("2026-09-10T12:00:00Z");

    public static FinancialRecordFixture aRecord() {
        return new FinancialRecordFixture();
    }

    public FinancialRecordFixture sourceId(UUID value) {
        this.sourceId = value;
        return this;
    }

    public FinancialRecordFixture externalId(String value) {
        this.externalId = value;
        return this;
    }

    public FinancialRecordFixture correlationKey(String value) {
        this.correlationKey = value;
        return this;
    }

    public FinancialRecordFixture direction(Direction value) {
        this.direction = value;
        return this;
    }

    public FinancialRecordFixture recordType(RecordType value) {
        this.recordType = value;
        return this;
    }

    public FinancialRecordFixture grossAmount(long minor) {
        this.grossAmount = new Money(minor, Currency.BRL);
        return this;
    }

    public FinancialRecordFixture grossAmount(Money value) {
        this.grossAmount = value;
        return this;
    }

    public FinancialRecordFixture declaredFeeAmount(Long minor) {
        this.declaredFeeAmount = minor == null ? null : new Money(minor, Currency.BRL);
        return this;
    }

    public FinancialRecordFixture netAmount(Long minor) {
        this.netAmount = minor == null ? null : new Money(minor, Currency.BRL);
        return this;
    }

    public FinancialRecordFixture businessDate(LocalDate value) {
        this.businessDate = value;
        return this;
    }

    public FinancialRecordFixture counterpartyDocument(String value) {
        this.counterpartyDocument = value;
        return this;
    }

    public FinancialRecordFixture paymentMethod(String value) {
        this.paymentMethod = value;
        return this;
    }

    public FinancialRecordFixture fingerprint(String value) {
        this.fingerprint = value;
        return this;
    }

    public FinancialRecord build() {
        return new FinancialRecord(
                sourceId, importBatchId, lineNumber, externalId, correlationKey, direction, recordType,
                grossAmount, declaredFeeAmount, netAmount, businessDate, sourceTimestamp, counterpartyDocument,
                paymentMethod, description, descriptionNormalized, rawLine, fingerprint, createdAt);
    }

    private static String randomFingerprint() {
        return UUID.randomUUID().toString().replace("-", "").repeat(2).substring(0, 64);
    }
}
