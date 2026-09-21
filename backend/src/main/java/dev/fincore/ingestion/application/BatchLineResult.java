package dev.fincore.ingestion.application;

/** Resultado de processar um lote de linhas: quantas entraram, quantas já existiam, quantas foram rejeitadas. */
record BatchLineResult(int insertedCount, int alreadyExistingCount, int rejectedCount) {
}
