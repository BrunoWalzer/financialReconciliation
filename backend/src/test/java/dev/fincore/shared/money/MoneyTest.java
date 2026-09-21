package dev.fincore.shared.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.RoundingMode;
import org.junit.jupiter.api.Test;

/**
 * {@link Money} — o único lugar do sistema onde aritmética monetária existe (TDS P3, 8.1).
 * Cobertura completa exigida pelo Implementation Plan M4: é a classe mais reusada do
 * sistema.
 */
class MoneyTest {

    @Test
    void deveCriarComValorEMoeda() {
        Money money = new Money(500, Currency.BRL);

        assertThat(money.amountMinor()).isEqualTo(500);
        assertThat(money.currency()).isEqualTo(Currency.BRL);
    }

    @Test
    void deveRejeitarMoedaNula() {
        assertThatThrownBy(() -> new Money(500, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void zeroDeveSerZero() {
        Money zero = new Money(0, Currency.BRL);

        assertThat(zero.isZero()).isTrue();
        assertThat(new Money(1, Currency.BRL).isZero()).isFalse();
    }

    @Test
    void devePermitirValoresPositivos() {
        Money money = new Money(12_345, Currency.BRL);

        assertThat(money.amountMinor()).isEqualTo(12_345);
    }

    @Test
    void devePermitirValoresNegativos() {
        // Normalização preserva sinal explícito (Domain §10.2); Money não proíbe negativos
        // — quem exige valor positivo é a regra de negócio específica (ex.: gross_amount <> 0
        // em FinancialRecord), não o VO genérico.
        Money money = new Money(-500, Currency.BRL);

        assertThat(money.amountMinor()).isEqualTo(-500);
        assertThat(money.isZero()).isFalse();
    }

    @Test
    void deveSomarNaMesmaMoeda() {
        Money sum = new Money(300, Currency.BRL).plus(new Money(200, Currency.BRL));

        assertThat(sum).isEqualTo(new Money(500, Currency.BRL));
    }

    @Test
    void deveSubtrairNaMesmaMoeda() {
        Money difference = new Money(300, Currency.BRL).minus(new Money(120, Currency.BRL));

        assertThat(difference).isEqualTo(new Money(180, Currency.BRL));
    }

    @Test
    void deveNegar() {
        assertThat(new Money(500, Currency.BRL).negate()).isEqualTo(new Money(-500, Currency.BRL));
        assertThat(new Money(-500, Currency.BRL).negate()).isEqualTo(new Money(500, Currency.BRL));
    }

    @Test
    void deveCalcularValorAbsoluto() {
        assertThat(new Money(-500, Currency.BRL).abs()).isEqualTo(new Money(500, Currency.BRL));
        assertThat(new Money(500, Currency.BRL).abs()).isEqualTo(new Money(500, Currency.BRL));
    }

    @Test
    void deveCompararPorValorNaMesmaMoeda() {
        assertThat(new Money(500, Currency.BRL).compareTo(new Money(100, Currency.BRL))).isPositive();
        assertThat(new Money(100, Currency.BRL).compareTo(new Money(500, Currency.BRL))).isNegative();
        assertThat(new Money(100, Currency.BRL).compareTo(new Money(100, Currency.BRL))).isZero();
    }

    @Test
    void deveLancarEmOperacoesEntreMoedasDiferentes() {
        Money brl = new Money(100, Currency.BRL);
        Money usd = new Money(100, Currency.USD);

        assertThatThrownBy(() -> brl.plus(usd)).isInstanceOf(CurrencyMismatchException.class);
        assertThatThrownBy(() -> brl.minus(usd)).isInstanceOf(CurrencyMismatchException.class);
        assertThatThrownBy(() -> brl.compareTo(usd)).isInstanceOf(CurrencyMismatchException.class);
        assertThatThrownBy(() -> brl.isWithin(usd)).isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void deveSomarNoLimiteSuperiorSemEstourar() {
        Money sum = new Money(Long.MAX_VALUE - 1, Currency.BRL).plus(new Money(1, Currency.BRL));

        assertThat(sum.amountMinor()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void deveLancarEmOverflowDeSoma() {
        Money max = new Money(Long.MAX_VALUE, Currency.BRL);

        assertThatThrownBy(() -> max.plus(new Money(1, Currency.BRL)))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void deveLancarEmOverflowDeSubtracao() {
        Money min = new Money(Long.MIN_VALUE, Currency.BRL);

        assertThatThrownBy(() -> min.minus(new Money(1, Currency.BRL)))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void deveLancarEmOverflowDeNegacao() {
        Money min = new Money(Long.MIN_VALUE, Currency.BRL);

        assertThatThrownBy(min::negate).isInstanceOf(ArithmeticException.class);
    }

    @Test
    void deveLancarEmOverflowDeValorAbsoluto() {
        Money min = new Money(Long.MIN_VALUE, Currency.BRL);

        assertThatThrownBy(min::abs).isInstanceOf(ArithmeticException.class);
    }

    @Test
    void isWithinDeveSerVerdadeiroNosLimitesExatos() {
        Money limit = new Money(200, Currency.BRL);

        assertThat(new Money(200, Currency.BRL).isWithin(limit)).isTrue();
        assertThat(new Money(-200, Currency.BRL).isWithin(limit)).isTrue();
        assertThat(new Money(199, Currency.BRL).isWithin(limit)).isTrue();
    }

    @Test
    void isWithinDeveSerFalsoAcimaDoLimite() {
        Money limit = new Money(200, Currency.BRL);

        assertThat(new Money(201, Currency.BRL).isWithin(limit)).isFalse();
        assertThat(new Money(-201, Currency.BRL).isWithin(limit)).isFalse();
    }

    @Test
    void percentageOfComHalfUpArredondaPontoCincoParaCima() {
        // 1 minor unit * 5000bp / 10000 = 0.5 exato.
        Money result = new Money(1, Currency.BRL).percentageOf(5000, RoundingMode.HALF_UP);

        assertThat(result).isEqualTo(new Money(1, Currency.BRL));
    }

    @Test
    void percentageOfComHalfEvenArredondaPontoCincoParaOParMaisProximo() {
        Money result = new Money(1, Currency.BRL).percentageOf(5000, RoundingMode.HALF_EVEN);

        assertThat(result).isEqualTo(new Money(0, Currency.BRL));
    }

    @Test
    void percentageOfComDownTruncaPontoCinco() {
        Money result = new Money(1, Currency.BRL).percentageOf(5000, RoundingMode.DOWN);

        assertThat(result).isEqualTo(new Money(0, Currency.BRL));
    }

    @Test
    void percentageOfComUpArredondaPontoCincoParaCima() {
        Money result = new Money(1, Currency.BRL).percentageOf(5000, RoundingMode.UP);

        assertThat(result).isEqualTo(new Money(1, Currency.BRL));
    }

    @Test
    void percentageOfDeveCalcularBasisPointsExatos() {
        // 250 bp = 2,5% de 50000 = 1250, sem parte fracionária — todo RoundingMode concorda.
        Money result = new Money(50_000, Currency.BRL).percentageOf(250, RoundingMode.HALF_UP);

        assertThat(result).isEqualTo(new Money(1_250, Currency.BRL));
    }

    @Test
    void percentageOfDeveExigirRoundingModeExplicito() {
        Money money = new Money(100, Currency.BRL);

        assertThatThrownBy(() -> money.percentageOf(100, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void deveTerIgualdadePorValorEMoeda() {
        assertThat(new Money(500, Currency.BRL)).isEqualTo(new Money(500, Currency.BRL));
        assertThat(new Money(500, Currency.BRL)).isNotEqualTo(new Money(500, Currency.USD));
        assertThat(new Money(500, Currency.BRL)).isNotEqualTo(new Money(501, Currency.BRL));
    }

    @Test
    void deveTerHashCodeConsistenteComIgualdade() {
        assertThat(new Money(500, Currency.BRL).hashCode()).isEqualTo(new Money(500, Currency.BRL).hashCode());
    }

    @Test
    void toStringDeveExporValorEMoeda() {
        String text = new Money(500, Currency.BRL).toString();

        assertThat(text).contains("500").contains("BRL");
    }
}
