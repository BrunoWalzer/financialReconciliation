package dev.fincore.evidence.api;

import dev.fincore.shared.money.Money;

/**
 * {@code { "minor": 49000, "currency": "BRL" }} (TDS 19/20: valor monetário sempre como
 * inteiro em unidade mínima acompanhado da moeda — nunca decimal, que muitos clientes
 * interpretam como {@code double}).
 */
public record MoneyResponse(long minor, String currency) {

    public static MoneyResponse from(Money money) {
        return money == null ? null : new MoneyResponse(money.amountMinor(), money.currency().name());
    }
}
