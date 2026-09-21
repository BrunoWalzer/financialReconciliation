package dev.fincore.ingestion.parser;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class DateTimeFieldParserTest {

    @Test
    void deveParsearDataHoraNoFormatoDeclarado() {
        var result = DateTimeFieldParser.parseDateTime("10/09/2026 23:50:00", List.of("dd/MM/yyyy HH:mm:ss"));

        assertThat(result).contains(LocalDateTime.of(2026, 9, 10, 23, 50, 0));
    }

    @Test
    void deveParsearDataSemHoraNoFormatoDeclarado() {
        var result = DateTimeFieldParser.parseDate("11/09/2026", List.of("dd/MM/yyyy"));

        assertThat(result).contains(LocalDate.of(2026, 9, 11));
    }

    @Test
    void deveRejeitarDataForaDosFormatosDeclarados() {
        var result = DateTimeFieldParser.parseDate("2026-09-11", List.of("dd/MM/yyyy"));

        assertThat(result).isEmpty();
    }

    @Test
    void deveRejeitarDataImpossivel() {
        var result = DateTimeFieldParser.parseDate("31/02/2026", List.of("dd/MM/yyyy"));

        assertThat(result).isEmpty();
    }

    @Test
    void deveRejeitarAnoForaDaFaixaPlausivel() {
        var result = DateTimeFieldParser.parseDate("01/01/1899", List.of("dd/MM/yyyy"));

        assertThat(result).isEmpty();
    }

    @Test
    void deveRejeitarVazioOuNulo() {
        assertThat(DateTimeFieldParser.parseDate("", List.of("dd/MM/yyyy"))).isEmpty();
        assertThat(DateTimeFieldParser.parseDate(null, List.of("dd/MM/yyyy"))).isEmpty();
    }

    @Test
    void cenarioDeBordaProximoDaVirada() {
        // 23:50 em São Paulo é o cenário obrigatório do M5 (seção 14 do prompt) — aqui só
        // o parsing puro; a conversão para business_date via fuso é testada no parser da
        // fonte e em BusinessDateResolverTest.
        var result = DateTimeFieldParser.parseDateTime("10/09/2026 23:50:00", List.of("dd/MM/yyyy HH:mm:ss"));

        assertThat(result).isPresent();
        assertThat(result.get().toLocalTime().getHour()).isEqualTo(23);
        assertThat(result.get().toLocalTime().getMinute()).isEqualTo(50);
    }
}
