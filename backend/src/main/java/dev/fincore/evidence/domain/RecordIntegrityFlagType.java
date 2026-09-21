package dev.fincore.evidence.domain;

/**
 * O que a varredura intra-fonte (M7, TDS 9.6) aprendeu sobre um registro, sem alterá-lo.
 * Os quatro valores são exatamente os do {@code CHECK ck_record_integrity_flag_type} (V4) —
 * nenhum valor novo pode ser adicionado aqui sem uma migration correspondente.
 */
public enum RecordIntegrityFlagType {
    DUPLICATE_EXTERNAL_ID,
    DUPLICATE_CORRELATION_KEY,
    POSSIBLE_DUPLICATE,
    SOURCE_INTERNAL_INCONSISTENCY
}
