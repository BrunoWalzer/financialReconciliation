package dev.fincore.shared.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * O único lugar do sistema onde aritmética monetária existe (TDS P3, 8.1). Inteiro de
 * unidade mínima — nunca {@code double}/{@code float} — porque aritmética binária de ponto
 * flutuante não representa exatamente um décimo de centavo, e essa imprecisão decide se uma
 * conciliação fecha ou gera divergência (Domain §10.3).
 *
 * <p>Todas as operações entre dois {@link Money} exigem a mesma moeda; moedas diferentes
 * lançam {@link CurrencyMismatchException} — a proibição vale mesmo só havendo {@code BRL}
 * no MVP. Toda soma/subtração usa aritmética exata ({@code Math.*Exact}): overflow lança
 * {@link ArithmeticException} em vez de estourar silenciosamente.
 *
 * <p>Igualdade e {@code hashCode} são por valor + moeda (garantido pelo {@code record}).
 * Não existe construtor a partir de {@code double}.
 */
public record Money(long amountMinor, Currency currency) implements Comparable<Money> {

    public Money {
        Objects.requireNonNull(currency, "currency é obrigatória — moeda nunca é implícita (Domain §10.3)");
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.addExact(amountMinor, other.amountMinor), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.subtractExact(amountMinor, other.amountMinor), currency);
    }

    public Money negate() {
        return new Money(Math.negateExact(amountMinor), currency);
    }

    public Money abs() {
        return new Money(Math.absExact(amountMinor), currency);
    }

    public boolean isZero() {
        return amountMinor == 0;
    }

    /** Verdadeiro se o valor absoluto deste {@link Money} não excede o limite informado. */
    public boolean isWithin(Money limit) {
        requireSameCurrency(limit);
        return Math.absExact(amountMinor) <= Math.absExact(limit.amountMinor);
    }

    /**
     * Percentual em <em>basis points</em> inteiros (250 = 2,5%), conforme TDS 8.2.
     * Escala intermediária 6 evita {@link ArithmeticException} de dízima periódica antes do
     * arredondamento final para a unidade mínima — o {@code roundingMode} decisivo é sempre
     * o informado, nunca um padrão da linguagem, e vem da regra de negócio (ex.: fee rule),
     * nunca de {@link Money}.
     */
    public Money percentageOf(int basisPoints, RoundingMode roundingMode) {
        Objects.requireNonNull(roundingMode, "roundingMode é obrigatório — nunca um padrão da linguagem (TDS 8.2)");

        BigDecimal raw = BigDecimal.valueOf(amountMinor)
                .multiply(BigDecimal.valueOf(basisPoints))
                .divide(BigDecimal.valueOf(10_000), 6, RoundingMode.HALF_UP);
        BigDecimal rounded = raw.setScale(0, roundingMode);
        return new Money(rounded.longValueExact(), currency);
    }

    @Override
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return Long.compare(amountMinor, other.amountMinor);
    }

    private void requireSameCurrency(Money other) {
        Objects.requireNonNull(other, "other é obrigatório");
        if (currency != other.currency) {
            throw new CurrencyMismatchException(currency, other.currency);
        }
    }
}
