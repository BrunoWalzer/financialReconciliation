package dev.fincore.ingestion.application;

import java.util.UUID;

/**
 * Porta para enfileirar o processamento assíncrono de um {@code import_batch} (Implementation
 * Plan M8, TDS 17.1/17.2 — fila {@code fincore.import.process}). A mensagem carrega só
 * identificador e correlação, nunca dado de negócio: o PostgreSQL é a fonte de verdade, a
 * mensagem só diz "há trabalho pendente para este id".
 *
 * <p>Uma implementação real ({@code ingestion.infrastructure.RabbitImportJobPublisher}) e uma
 * de teste (publisher falso, TDS 17.3: "os demais [testes] usam publisher falso") — a
 * abstração existe porque o próprio TDS pede as duas, não por precaução genérica.
 */
public interface ImportJobPublisher {

    void publish(UUID batchId, String correlationId, int attempt);
}
