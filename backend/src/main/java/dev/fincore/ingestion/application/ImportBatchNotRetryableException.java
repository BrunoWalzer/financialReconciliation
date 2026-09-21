package dev.fincore.ingestion.application;

/** {@code POST /imports/{id}/retry} chamado fora de {@code FAILED} (Implementation Plan M8). */
public class ImportBatchNotRetryableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ImportBatchNotRetryableException(String message) {
        super(message);
    }
}
