package dev.fincore.matching.domain;

/** Se uma regra pode produzir correspondência automática, ou só sugerir (TDS 11.2). */
public enum Decisiveness {
    AUTO_MATCH,
    SUGGEST_ONLY
}
