package dev.fincore.ingestion.parser;

import static org.assertj.core.api.Assertions.assertThat;

import dev.fincore.evidence.domain.Direction;
import dev.fincore.evidence.domain.RecordType;
import dev.fincore.ingestion.domain.RejectionReasonCode;
import dev.fincore.shared.money.Currency;
import dev.fincore.shared.money.Money;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AcquirerSettlementParserTest {

    private final AcquirerSettlementParser parser = new AcquirerSettlementParser();
    private final ParseContext context = new ParseContext(
            UUID.randomUUID(), ZoneId.of("America/Sao_Paulo"), ',', '.', List.of("dd/MM/yyyy"));

    @Test
    void deveReconhecerOCabecalhoValido() {
        List<String> header = List.of(
                "nsu", "data_liquidacao", "valor_bruto", "valor_taxa", "valor_liquido", "bandeira", "tipo_operacao", "parcela");

        assertThat(parser.layout().matchesHeader(header)).isTrue();
    }

    @Test
    void deveRejeitarCabecalhoDeOutraFonte() {
        List<String> internalSalesHeader = List.of(
                "pedido_id", "nsu", "data_hora", "valor_bruto", "meio_pagamento", "documento_cliente", "tipo", "descricao");

        assertThat(parser.layout().matchesHeader(internalSalesHeader)).isFalse();
    }

    @Test
    void deveAceitarLinhaValidaCompleta() {
        ParseResult result = parseLine("NSU126;11/09/2026;500,00;12,80;487,20;VISA;CREDITO_A_VISTA;1/1");

        assertThat(result).isInstanceOf(ParseResult.Accepted.class);
        var draft = ((ParseResult.Accepted) result).draft();
        assertThat(draft.externalId()).isNull();
        assertThat(draft.correlationKey()).isEqualTo("NSU126");
        assertThat(draft.direction()).isEqualTo(Direction.CREDIT);
        assertThat(draft.recordType()).isEqualTo(RecordType.SETTLEMENT);
        assertThat(draft.grossAmount()).isEqualTo(new Money(50_000, Currency.BRL));
        assertThat(draft.declaredFeeAmount()).isEqualTo(new Money(1_280, Currency.BRL));
        assertThat(draft.netAmount()).isEqualTo(new Money(48_720, Currency.BRL));
        assertThat(draft.paymentMethod()).isEqualTo("CREDIT_CARD");
        assertThat(draft.counterpartyDocument()).isNull();
        assertThat(draft.businessDate()).isEqualTo(LocalDate.of(2026, 9, 11));
        assertThat(draft.sourceTimestamp()).isNull();
    }

    @Test
    void naoDeveDerivarDocumentoDeNenhumCampo() {
        // Domain §7.1: a liquidação não traz documento do cliente — nunca inventado aqui.
        ParseResult result = parseLine("NSU126;11/09/2026;500,00;12,80;487,20;VISA;CREDITO_A_VISTA;1/1");

        var draft = ((ParseResult.Accepted) result).draft();
        assertThat(draft.counterpartyDocument()).isNull();
    }

    @Test
    void deveRejeitarNumeroDeColunasIncorreto() {
        ParseResult result = parseLine("NSU126;11/09/2026;500,00;12,80;487,20;VISA;CREDITO_A_VISTA");

        assertThat(((ParseResult.Rejected) result).reasonCode()).isEqualTo(RejectionReasonCode.COLUMN_COUNT_MISMATCH);
    }

    @Test
    void deveRejeitarNsuVazio() {
        ParseResult result = parseLine(";11/09/2026;500,00;12,80;487,20;VISA;CREDITO_A_VISTA;1/1");

        assertThat(((ParseResult.Rejected) result).reasonCode()).isEqualTo(RejectionReasonCode.REQUIRED_FIELD_MISSING);
    }

    @Test
    void deveRejeitarValorTaxaAmbiguo() {
        ParseResult result = parseLine("NSU126;11/09/2026;500,00;1,234;487,20;VISA;CREDITO_A_VISTA;1/1");

        assertThat(((ParseResult.Rejected) result).reasonCode()).isEqualTo(RejectionReasonCode.AMBIGUOUS_OR_INVALID_DECIMAL);
    }

    @Test
    void deveRejeitarTipoOperacaoDesconhecido() {
        ParseResult result = parseLine("NSU126;11/09/2026;500,00;12,80;487,20;VISA;DEBITO_PARCELADO;1/1");

        assertThat(((ParseResult.Rejected) result).reasonCode()).isEqualTo(RejectionReasonCode.UNKNOWN_ENUM_VALUE);
    }

    @Test
    void deveRejeitarDataDeLiquidacaoInvalida() {
        ParseResult result = parseLine("NSU126;2026-09-11;500,00;12,80;487,20;VISA;CREDITO_A_VISTA;1/1");

        assertThat(((ParseResult.Rejected) result).reasonCode()).isEqualTo(RejectionReasonCode.INVALID_DATE);
    }

    @Test
    void deveIgnorarColunaParcela() {
        ParseResult resultUmaParcela = parseLine("NSU126;11/09/2026;500,00;12,80;487,20;VISA;CREDITO_A_VISTA;1/1");
        ParseResult resultDozeParcelas = parseLine("NSU127;11/09/2026;500,00;12,80;487,20;VISA;CREDITO_A_VISTA;12/12");

        assertThat(resultUmaParcela).isInstanceOf(ParseResult.Accepted.class);
        assertThat(resultDozeParcelas).isInstanceOf(ParseResult.Accepted.class);
    }

    private ParseResult parseLine(String rawLine) {
        RawLine line = new RawLine(2, rawLine, CsvLineSplitter.split(rawLine));
        return parser.parseLine(line, context);
    }
}
