package dev.fincore.ingestion.application;

import java.util.UUID;

/** Mesmo conteúdo, fonte e data de referência já foram importados (I-6, TDS 9.7). */
public class DuplicateFileException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final UUID originalImportBatchId;

    public DuplicateFileException(UUID originalImportBatchId) {
        super("arquivo já importado: " + originalImportBatchId);
        this.originalImportBatchId = originalImportBatchId;
    }

    public UUID originalImportBatchId() {
        return originalImportBatchId;
    }
}
