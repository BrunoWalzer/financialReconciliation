package dev.fincore.ingestion.infrastructure;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Limites aprovados (Implementation Plan FD-10, OD-5): 50 MB, 500 mil linhas, lote de 1000.
 *
 * <p>{@code stuckThreshold} (M8, TDS 17.5): idade máxima de um {@code import_batch} em
 * {@code RECEIVED}/{@code PROCESSING} antes de o sweep considerá-lo travado e republicar.
 */
@ConfigurationProperties(prefix = "fincore.ingestion")
public record IngestionProperties(long maxUploadBytes, long maxUploadLines, int batchSize, Duration stuckThreshold) {
}
