package dev.fincore.configuration.domain;

/** Periodicidade esperada de chegada de dados de uma fonte (TDS 7.3). */
public enum CoverageSchedule {
    DAILY,
    BUSINESS_DAYS,
    WEEKLY,
    NONE
}
