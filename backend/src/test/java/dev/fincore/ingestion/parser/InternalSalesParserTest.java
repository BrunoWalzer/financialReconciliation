package dev.fincore.ingestion.parser;

import static org.assertj.core.api.Assertions.assertThat;

import dev.fincore.evidence.domain.Direction;
import dev.fincore.evidence.domain.RecordType;
import dev.fincore.ingestion.domain.RejectionReasonCode;
import dev.fincore.shared.money.Currency;
import dev.fincore.shared.money.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** {@link InternalSalesParser} — sem banco, sem Spring (Implementation Plan M5). */
class InternalSalesParserTest {

    private final InternalSalesParser parser = new InternalSalesParser();
    private final ParseContext context = new ParseContext(
            UUID.randomUUID(), ZoneId.of("America/Sao_Paulo"), ',', '.', List.of("dd/MM/yyyy HH:mm:ss"));

    @Test
    void deveReconhecerOCabecalhoValido() {
        List<String> header = List.of(
                "pedido_id", "nsu", "data_hora", "valor_bruto", "meio_pagamento", "documento_cliente", "tipo", "descricao");

        assertThat(parser.layout().matchesHeader(header)).isTrue();
    }

    @Test
    void deveRejeitarCabecalhoDeOutraFonte() {
        List<String> acquirerHeader = List.of(
                "nsu", "data_liquidacao", "valor_bruto", "valor_taxa", "valor_liquido", "bandeira", "tipo_operacao", "parcela");

        assertThat(parser.layout().matchesHeader(acquirerHeader)).isFalse();
    }

    @Test
    void deveAceitarLinhaValidaCompleta() {
        ParseResult result = parseLine("PED-1;NSU1;10/09/2026 14:32:11;500,00;CREDITO;11144477735;VENDA;Pedido loja online");

        assertThat(result).isInstanceOf(ParseResult.Accepted.class);
        var draft = ((ParseResult.Accepted) result).draft();
        assertThat(draft.externalId()).isEqualTo("PED-1");
        assertThat(draft.correlationKey()).isEqualTo("NSU1");
        assertThat(draft.direction()).isEqualTo(Direction.CREDIT);
        assertThat(draft.recordType()).isEqualTo(RecordType.SALE);
        assertThat(draft.grossAmount()).isEqualTo(new Money(50_000, Currency.BRL));
        assertThat(draft.counterpartyDocument()).isEqualTo("11144477735");
        assertThat(draft.paymentMethod()).isEqualTo("CREDITO");
        assertThat(draft.businessDate()).isEqualTo(LocalDate.of(2026, 9, 10));
    }

    @Test
    void deveAceitarLinhaSemNsuDocumentoOuDescricao() {
        ParseResult result = parseLine("PED-2;;10/09/2026 08:00:00;100,00;PIX;;VENDA;");

        assertThat(result).isInstanceOf(ParseResult.Accepted.class);
        var draft = ((ParseResult.Accepted) result).draft();
        assertThat(draft.correlationKey()).isNull();
        assertThat(draft.counterpartyDocument()).isNull();
        assertThat(draft.description()).isNull();
    }

    @Test
    void deveMapearEstornoParaRefundEDebito() {
        ParseResult result = parseLine("PED-3;NSU3;10/09/2026 08:00:00;100,00;PIX;;ESTORNO;");

        var draft = ((ParseResult.Accepted) result).draft();
        assertThat(draft.recordType()).isEqualTo(RecordType.REFUND);
        assertThat(draft.direction()).isEqualTo(Direction.DEBIT);
    }

    @Test
    void deveRejeitarNumeroDeColunasIncorreto() {
        ParseResult result = parseLine("PED-1;NSU1;10/09/2026 14:32:11;500,00;CREDITO;11144477735;VENDA");

        assertThat(result).isInstanceOf(ParseResult.Rejected.class);
        assertThat(((ParseResult.Rejected) result).reasonCode()).isEqualTo(RejectionReasonCode.COLUMN_COUNT_MISMATCH);
    }

    @Test
    void deveRejeitarCampoObrigatorioVazio() {
        ParseResult result = parseLine(";NSU1;10/09/2026 14:32:11;500,00;CREDITO;;VENDA;");

        assertThat(result).isInstanceOf(ParseResult.Rejected.class);
        assertThat(((ParseResult.Rejected) result).reasonCode()).isEqualTo(RejectionReasonCode.REQUIRED_FIELD_MISSING);
    }

    @Test
    void deveRejeitarDecimalAmbiguo() {
        ParseResult result = parseLine("PED-1;NSU1;10/09/2026 14:32:11;1,234;CREDITO;;VENDA;");

        assertThat(result).isInstanceOf(ParseResult.Rejected.class);
        assertThat(((ParseResult.Rejected) result).reasonCode()).isEqualTo(RejectionReasonCode.AMBIGUOUS_OR_INVALID_DECIMAL);
    }

    @Test
    void deveRejeitarValorZero() {
        ParseResult result = parseLine("PED-1;NSU1;10/09/2026 14:32:11;0,00;CREDITO;;VENDA;");

        assertThat(result).isInstanceOf(ParseResult.Rejected.class);
        assertThat(((ParseResult.Rejected) result).reasonCode()).isEqualTo(RejectionReasonCode.ZERO_AMOUNT);
    }

    @Test
    void deveRejeitarTimestampInvalido() {
        ParseResult result = parseLine("PED-1;NSU1;2026-09-10T14:32:11;500,00;CREDITO;;VENDA;");

        assertThat(result).isInstanceOf(ParseResult.Rejected.class);
        assertThat(((ParseResult.Rejected) result).reasonCode()).isEqualTo(RejectionReasonCode.INVALID_DATE);
    }

    @Test
    void deveRejeitarTipoDesconhecido() {
        ParseResult result = parseLine("PED-1;NSU1;10/09/2026 14:32:11;500,00;CREDITO;;DEVOLUCAO;");

        assertThat(result).isInstanceOf(ParseResult.Rejected.class);
        assertThat(((ParseResult.Rejected) result).reasonCode()).isEqualTo(RejectionReasonCode.UNKNOWN_ENUM_VALUE);
    }

    @Test
    void deveRejeitarDocumentoComDigitoVerificadorInvalido() {
        ParseResult result = parseLine("PED-1;NSU1;10/09/2026 14:32:11;500,00;CREDITO;11144477736;VENDA;");

        assertThat(result).isInstanceOf(ParseResult.Rejected.class);
        assertThat(((ParseResult.Rejected) result).reasonCode()).isEqualTo(RejectionReasonCode.INVALID_DOCUMENT);
    }

    @Test
    void devePreservarDescricaoComCaracteresUtf8() {
        ParseResult result = parseLine("PED-1;NSU1;10/09/2026 14:32:11;500,00;CREDITO;;VENDA;Pedido com açúcar e café");

        var draft = ((ParseResult.Accepted) result).draft();
        assertThat(draft.description()).isEqualTo("Pedido com açúcar e café");
        assertThat(draft.descriptionNormalized()).isEqualTo("PEDIDO COM ACUCAR E CAFE");
    }

    @Test
    void cenarioDeBorda2350ProximoDaMudancaDeDataResolveParaODiaAnterior() {
        // TDS 8.4 / Domain §5.5: 23:50 em São Paulo (-03:00) é 10/09; em UTC já seria 11/09.
        ParseResult result = parseLine("PED-1;NSU1;10/09/2026 23:50:00;500,00;CREDITO;;VENDA;");

        var draft = ((ParseResult.Accepted) result).draft();
        assertThat(draft.businessDate()).isEqualTo(LocalDate.of(2026, 9, 10));
        Instant expectedInstant = LocalDate.of(2026, 9, 10).atTime(23, 50, 0).atZone(ZoneId.of("America/Sao_Paulo")).toInstant();
        assertThat(draft.sourceTimestamp()).isEqualTo(expectedInstant);
    }

    @Test
    void deveConverterValorBrutoParaMoneyExatoSemPontoFlutuante() {
        ParseResult result = parseLine("PED-1;NSU1;10/09/2026 14:32:11;12345,67;CREDITO;;VENDA;");

        var draft = ((ParseResult.Accepted) result).draft();
        assertThat(draft.grossAmount().amountMinor()).isEqualTo(1_234_567L);
    }

    private ParseResult parseLine(String rawLine) {
        RawLine line = new RawLine(2, rawLine, CsvLineSplitter.split(rawLine));
        return parser.parseLine(line, context);
    }
}
