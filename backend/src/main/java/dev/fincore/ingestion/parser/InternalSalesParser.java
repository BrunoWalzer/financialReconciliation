package dev.fincore.ingestion.parser;

import dev.fincore.evidence.domain.Direction;
import dev.fincore.evidence.domain.RecordType;
import dev.fincore.ingestion.domain.RejectionReasonCode;
import dev.fincore.shared.money.Currency;
import dev.fincore.shared.money.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** {@code INTERNAL_SALES} — autoridade sobre a intenção comercial (Domain §7.1). */
@Component
public final class InternalSalesParser implements RecordParser {

    private static final int COL_PEDIDO_ID = 0;
    private static final int COL_NSU = 1;
    private static final int COL_DATA_HORA = 2;
    private static final int COL_VALOR_BRUTO = 3;
    private static final int COL_MEIO_PAGAMENTO = 4;
    private static final int COL_DOCUMENTO_CLIENTE = 5;
    private static final int COL_TIPO = 6;
    private static final int COL_DESCRICAO = 7;

    private final SourceLayout layout = new InternalSalesLayout();

    @Override
    public String sourceCode() {
        return "INTERNAL_SALES";
    }

    @Override
    public SourceLayout layout() {
        return layout;
    }

    @Override
    public ParseResult parseLine(RawLine line, ParseContext context) {
        List<String> fields = line.fields();
        if (fields.size() != layout.columns().size()) {
            return new ParseResult.Rejected(
                    RejectionReasonCode.COLUMN_COUNT_MISMATCH,
                    "esperado " + layout.columns().size() + " colunas, encontrado " + fields.size(),
                    null);
        }

        String externalId = TextNormalizer.clean(fields.get(COL_PEDIDO_ID));
        if (externalId == null) {
            return rejected(RejectionReasonCode.REQUIRED_FIELD_MISSING, "pedido_id vazio", null);
        }

        String correlationKey = TextNormalizer.clean(fields.get(COL_NSU));

        Optional<LocalDateTime> parsedDateTime = DateTimeFieldParser.parseDateTime(
                fields.get(COL_DATA_HORA), context.dateFormats());
        if (parsedDateTime.isEmpty()) {
            return rejected(RejectionReasonCode.INVALID_DATE, "data_hora inválida ou fora dos formatos declarados", null);
        }
        Instant sourceTimestamp = parsedDateTime.get().atZone(context.timezone()).toInstant();
        LocalDate businessDate = dev.fincore.shared.time.BusinessDateResolver.resolve(sourceTimestamp, context.timezone());

        Optional<Long> grossMinor = DecimalNormalizer.toMinorUnits(
                fields.get(COL_VALOR_BRUTO), context.decimalSeparator(), context.thousandsSeparator());
        if (grossMinor.isEmpty()) {
            return rejected(RejectionReasonCode.AMBIGUOUS_OR_INVALID_DECIMAL, "valor_bruto ambíguo ou inválido", null);
        }
        if (grossMinor.get() == 0) {
            return rejected(RejectionReasonCode.ZERO_AMOUNT, "valor_bruto igual a zero", 0L);
        }

        String paymentMethod = PaymentMethodMapper.normalize(fields.get(COL_MEIO_PAGAMENTO));
        if (paymentMethod == null) {
            return rejected(RejectionReasonCode.REQUIRED_FIELD_MISSING, "meio_pagamento vazio", grossMinor.get());
        }

        String counterpartyDocument = null;
        String rawDocument = fields.get(COL_DOCUMENTO_CLIENTE);
        if (rawDocument != null && !rawDocument.isBlank()) {
            Optional<String> normalizedDocument = DocumentNormalizer.normalize(rawDocument);
            if (normalizedDocument.isEmpty()) {
                return rejected(RejectionReasonCode.INVALID_DOCUMENT, "documento_cliente com dígito verificador inválido", grossMinor.get());
            }
            counterpartyDocument = normalizedDocument.get();
        }

        Optional<RecordType> recordType = RecordTypeMapper.fromInternalSalesTipo(fields.get(COL_TIPO));
        if (recordType.isEmpty()) {
            return rejected(RejectionReasonCode.UNKNOWN_ENUM_VALUE, "tipo desconhecido: " + fields.get(COL_TIPO), grossMinor.get());
        }
        Direction direction = DirectionResolver.fromRecordType(recordType.get());

        String description = TextNormalizer.clean(fields.get(COL_DESCRICAO));
        String descriptionNormalized = TextNormalizer.forComparison(fields.get(COL_DESCRICAO));

        String fingerprint = FingerprintCalculator.calculate(
                sourceCode(), externalId, correlationKey, direction, recordType.get(), grossMinor.get(),
                Currency.BRL, businessDate, counterpartyDocument, paymentMethod);

        NormalizedRecordDraft draft = new NormalizedRecordDraft(
                externalId, correlationKey, direction, recordType.get(),
                new Money(grossMinor.get(), Currency.BRL), null, null,
                businessDate, sourceTimestamp, counterpartyDocument, paymentMethod,
                description, descriptionNormalized, fingerprint);

        return new ParseResult.Accepted(draft);
    }

    private static ParseResult.Rejected rejected(RejectionReasonCode code, String detail, Long extractedAmountMinor) {
        return new ParseResult.Rejected(code, detail, extractedAmountMinor);
    }
}
