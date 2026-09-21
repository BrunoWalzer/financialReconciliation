package dev.fincore.ingestion.parser;

import dev.fincore.ingestion.domain.RejectionReasonCode;
import dev.fincore.shared.money.Currency;
import dev.fincore.shared.money.Money;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * {@code ACQUIRER_SETTLEMENT} — autoridade sobre taxa e prazo (Domain §7.1). Não traz
 * identificador próprio de linha ({@code external_id} sempre nulo) nem documento da
 * contraparte — as duas ausências são decisão de domínio já registrada (Domain §7.1),
 * nunca preenchidas artificialmente aqui.
 */
@Component
public final class AcquirerSettlementParser implements RecordParser {

    private static final int COL_NSU = 0;
    private static final int COL_DATA_LIQUIDACAO = 1;
    private static final int COL_VALOR_BRUTO = 2;
    private static final int COL_VALOR_TAXA = 3;
    private static final int COL_VALOR_LIQUIDO = 4;
    private static final int COL_BANDEIRA = 5;
    private static final int COL_TIPO_OPERACAO = 6;

    private final SourceLayout layout = new AcquirerSettlementLayout();

    @Override
    public String sourceCode() {
        return "ACQUIRER_SETTLEMENT";
    }

    @Override
    public SourceLayout layout() {
        return layout;
    }

    @Override
    public ParseResult parseLine(RawLine line, ParseContext context) {
        List<String> fields = line.fields();
        if (fields.size() != layout.columns().size()) {
            return rejected(RejectionReasonCode.COLUMN_COUNT_MISMATCH,
                    "esperado " + layout.columns().size() + " colunas, encontrado " + fields.size(), null);
        }

        String correlationKey = TextNormalizer.clean(fields.get(COL_NSU));
        if (correlationKey == null) {
            return rejected(RejectionReasonCode.REQUIRED_FIELD_MISSING, "nsu vazio", null);
        }

        Optional<LocalDate> businessDate = DateTimeFieldParser.parseDate(fields.get(COL_DATA_LIQUIDACAO), context.dateFormats());
        if (businessDate.isEmpty()) {
            return rejected(RejectionReasonCode.INVALID_DATE, "data_liquidacao inválida ou fora dos formatos declarados", null);
        }

        Optional<Long> grossMinor = DecimalNormalizer.toMinorUnits(
                fields.get(COL_VALOR_BRUTO), context.decimalSeparator(), context.thousandsSeparator());
        if (grossMinor.isEmpty()) {
            return rejected(RejectionReasonCode.AMBIGUOUS_OR_INVALID_DECIMAL, "valor_bruto ambíguo ou inválido", null);
        }
        if (grossMinor.get() == 0) {
            return rejected(RejectionReasonCode.ZERO_AMOUNT, "valor_bruto igual a zero", 0L);
        }

        Optional<Long> feeMinor = DecimalNormalizer.toMinorUnits(
                fields.get(COL_VALOR_TAXA), context.decimalSeparator(), context.thousandsSeparator());
        if (feeMinor.isEmpty()) {
            return rejected(RejectionReasonCode.AMBIGUOUS_OR_INVALID_DECIMAL, "valor_taxa ambíguo ou inválido", grossMinor.get());
        }

        Optional<Long> netMinor = DecimalNormalizer.toMinorUnits(
                fields.get(COL_VALOR_LIQUIDO), context.decimalSeparator(), context.thousandsSeparator());
        if (netMinor.isEmpty()) {
            return rejected(RejectionReasonCode.AMBIGUOUS_OR_INVALID_DECIMAL, "valor_liquido ambíguo ou inválido", grossMinor.get());
        }

        String description = TextNormalizer.clean(fields.get(COL_BANDEIRA));
        if (description == null) {
            return rejected(RejectionReasonCode.REQUIRED_FIELD_MISSING, "bandeira vazia", grossMinor.get());
        }
        String descriptionNormalized = TextNormalizer.forComparison(fields.get(COL_BANDEIRA));

        Optional<AcquirerOperationType> operation = AcquirerOperationType.fromTipoOperacao(fields.get(COL_TIPO_OPERACAO));
        if (operation.isEmpty()) {
            return rejected(RejectionReasonCode.UNKNOWN_ENUM_VALUE,
                    "tipo_operacao desconhecido: " + fields.get(COL_TIPO_OPERACAO), grossMinor.get());
        }
        // parcela (última coluna) é ignorada no MVP (Implementation Plan DR-1).

        String fingerprint = FingerprintCalculator.calculate(
                sourceCode(), null, correlationKey, operation.get().direction(), operation.get().recordType(),
                grossMinor.get(), Currency.BRL, businessDate.get(), null, operation.get().paymentMethod());

        NormalizedRecordDraft draft = new NormalizedRecordDraft(
                null, correlationKey, operation.get().direction(), operation.get().recordType(),
                new Money(grossMinor.get(), Currency.BRL),
                new Money(feeMinor.get(), Currency.BRL),
                new Money(netMinor.get(), Currency.BRL),
                businessDate.get(), null, null, operation.get().paymentMethod(),
                description, descriptionNormalized, fingerprint);

        return new ParseResult.Accepted(draft);
    }

    private static ParseResult.Rejected rejected(RejectionReasonCode code, String detail, Long extractedAmountMinor) {
        return new ParseResult.Rejected(code, detail, extractedAmountMinor);
    }
}
