package dev.fincore.ingestion.domain;

/**
 * Estados de {@link ImportBatch} (Domain §9.2; TDS 7.4). Nenhum outro estado existe.
 * {@code REJECTED} significa zero <b>registros financeiros</b>, não zero linhas rejeitadas
 * — a distinção que mais se erra (TDS 9.7).
 */
public enum ImportStatus {
    /** Arquivo recebido e identificado, ainda não processado. Não terminal. */
    RECEIVED,
    /** Leitura e validação em curso. Não terminal. */
    PROCESSING,
    /** Todas as linhas aceitas. Terminal. */
    COMPLETED,
    /** Pelo menos uma aceita e pelo menos uma rejeitada. Terminal. */
    COMPLETED_WITH_REJECTS,
    /** Nenhum registro financeiro criado: arquivo inválido, duplicado ou todas as linhas inválidas. Terminal. */
    REJECTED,
    /** Falha de processamento. Não terminal — retentável (M8). */
    FAILED;

    public boolean isTerminal() {
        return this == COMPLETED || this == COMPLETED_WITH_REJECTS || this == REJECTED;
    }
}
