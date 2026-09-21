package dev.fincore.shared.money;

/**
 * Moeda explícita, nunca implícita (Domain §10.3; TDS 8.1). Enum fechado: só {@code BRL}
 * circula funcionalmente no MVP — toda constraint de banco, toda fonte e toda configuração
 * aceitam exclusivamente {@code BRL} (ex.: {@code ck_financial_record_currency}).
 * Conversão cambial está fora de escopo em qualquer milestone deste projeto.
 *
 * <p>{@code USD} não é aceito em nenhum caminho funcional do sistema — existe só para que
 * o teste de {@link Money} possa provar, com dois valores reais e não com mock, a garantia
 * que o Domain §10.3 já enuncia como válida "mesmo sem uma segunda moeda": operar
 * {@link Money} entre moedas diferentes lança {@link CurrencyMismatchException}. Este
 * projeto não usa biblioteca de mock (nenhuma dependência de Mockito existe no build) —
 * duas constantes reais são a forma de provar a regra sem introduzir uma.
 */
public enum Currency {
    BRL,
    USD
}
