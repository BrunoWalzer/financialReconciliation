/**
 * Persistência de importação e a porta de armazenamento do arquivo original (TDS 9.3).
 * {@link dev.fincore.ingestion.infrastructure.FileStorage} tem uma única implementação —
 * filesystem (Implementation Plan FD-10, OD-4) — nunca S3/MinIO.
 */
package dev.fincore.ingestion.infrastructure;
