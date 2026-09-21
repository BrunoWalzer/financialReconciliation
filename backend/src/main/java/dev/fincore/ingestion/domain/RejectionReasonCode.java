package dev.fincore.ingestion.domain;

/** Motivo pelo qual uma linha foi rejeitada (Domain §10.4) — nunca adivinhado, sempre determinístico. */
public enum RejectionReasonCode {
    /** Número de colunas diferente do cabeçalho declarado. */
    COLUMN_COUNT_MISMATCH,
    /** Campo obrigatório vazio. */
    REQUIRED_FIELD_MISSING,
    /** Separador decimal não resolve de forma inequívoca (Domain §10.3) — nunca adivinhado. */
    AMBIGUOUS_OR_INVALID_DECIMAL,
    /** Data impossível, ou fora dos formatos declarados da fonte. */
    INVALID_DATE,
    /** Valor bruto igual a zero. */
    ZERO_AMOUNT,
    /** Valor de enum fechado (ex.: {@code tipo}, {@code tipo_operacao}) fora do conjunto conhecido. */
    UNKNOWN_ENUM_VALUE,
    /** Documento da contraparte com dígito verificador inválido. */
    INVALID_DOCUMENT,
    /** Caractere de substituição (encoding corrompido) em campo de negócio. */
    INVALID_CHARACTER_ENCODING
}
