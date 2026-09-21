/**
 * Aritmética monetária — o único lugar do sistema onde ela existe (TDS P3).
 *
 * <p>{@link dev.fincore.shared.money.Money} (inteiro de unidade mínima + moeda explícita),
 * {@link dev.fincore.shared.money.Currency} (enum fechado, só {@code BRL} no MVP) e
 * {@link dev.fincore.shared.money.CurrencyMismatchException}. Nasce no M4, junto com
 * {@code financial_record} — que é o motivo de existir.
 */
package dev.fincore.shared.money;
