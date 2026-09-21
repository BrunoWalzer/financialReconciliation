package dev.fincore.evidence.application;

/** Resultado de {@link BulkInsertFinancialRecordsUseCase}: quantas linhas novas entraram e quantas já existiam. */
public record BulkInsertResult(int insertedCount, int alreadyExistingCount) {
}
