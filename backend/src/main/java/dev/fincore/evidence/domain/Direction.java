package dev.fincore.evidence.domain;

/** Entrada ou saída (Domain §5.2). Predicado obrigatório de todo matching futuro — venda nunca casa com estorno. */
public enum Direction {
    CREDIT,
    DEBIT
}
