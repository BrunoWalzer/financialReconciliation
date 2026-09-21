package dev.fincore.ingestion.parser;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** {@link DecimalNormalizer} — ambiguidade é rejeição, nunca adivinhação (Domain §10.3). */
class DecimalNormalizerTest {

    @Test
    void deveConverterValorSimplesComVirgulaDecimal() {
        assertThat(DecimalNormalizer.toMinorUnits("500,00", ',', '.')).contains(50_000L);
    }

    @Test
    void deveConverterValorComSeparadorDeMilhar() {
        assertThat(DecimalNormalizer.toMinorUnits("1.234,56", ',', '.')).contains(123_456L);
    }

    @Test
    void deveConverterValorNegativoComSinal() {
        assertThat(DecimalNormalizer.toMinorUnits("-500,00", ',', '.')).contains(-50_000L);
    }

    @Test
    void deveRejeitarValorSemParteDecimalExplicita() {
        // "500" sozinho é ambíguo: 5,00 ou 500,00? Nunca adivinha.
        assertThat(DecimalNormalizer.toMinorUnits("500", ',', '.')).isEmpty();
    }

    @Test
    void deveRejeitarDecimalAmbiguoComTresCasas() {
        // O exemplo exato do cenário obrigatório: "1,234" não é nem 1,234 nem 1.234,00.
        assertThat(DecimalNormalizer.toMinorUnits("1,234", ',', '.')).isEmpty();
    }

    @Test
    void deveRejeitarComDoisSeparadoresDecimais() {
        assertThat(DecimalNormalizer.toMinorUnits("1,23,45", ',', '.')).isEmpty();
    }

    @Test
    void deveRejeitarTextoNaoNumerico() {
        assertThat(DecimalNormalizer.toMinorUnits("abc,de", ',', '.')).isEmpty();
    }

    @Test
    void deveRejeitarVazioOuNulo() {
        assertThat(DecimalNormalizer.toMinorUnits("", ',', '.')).isEmpty();
        assertThat(DecimalNormalizer.toMinorUnits("   ", ',', '.')).isEmpty();
        assertThat(DecimalNormalizer.toMinorUnits(null, ',', '.')).isEmpty();
    }

    @Test
    void deveConverterZeroExato() {
        assertThat(DecimalNormalizer.toMinorUnits("0,00", ',', '.')).contains(0L);
    }

    @Test
    void deveFuncionarComSeparadoresInvertidos() {
        // Formato en-US: ponto decimal, vírgula de milhar.
        assertThat(DecimalNormalizer.toMinorUnits("1,234.56", '.', ',')).contains(123_456L);
    }
}
