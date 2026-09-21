package dev.fincore.ingestion.application;

/** Upload acima de {@code fincore.ingestion.max-upload-bytes} (TDS 9.1, 9.4: "Tamanho" → 413). */
public class UploadTooLargeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public UploadTooLargeException(String message) {
        super(message);
    }
}
