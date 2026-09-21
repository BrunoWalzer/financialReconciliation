package dev.fincore.ingestion.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Limites aprovados (Implementation Plan FD-10, OD-5): 50 MB, 500 mil linhas, lote de 1000. */
@ConfigurationProperties(prefix = "fincore.ingestion")
public record IngestionProperties(long maxUploadBytes, long maxUploadLines, int batchSize) {
}
