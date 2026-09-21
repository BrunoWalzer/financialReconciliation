package dev.fincore.evidence.domain;

/**
 * Venda, liquidação, movimentação bancária, estorno, taxa e ajuste não são entidades — são
 * valores deste enum sobre a mesma estrutura de {@link FinancialRecord} (Domain §5.4).
 */
public enum RecordType {
    SALE,
    SETTLEMENT,
    BANK_MOVEMENT,
    REFUND,
    FEE,
    ADJUSTMENT
}
