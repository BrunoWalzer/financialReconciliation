package dev.fincore.ingestion.application;

import java.util.UUID;

/**
 * Publicado in-process, dentro da transação que grava o {@code import_batch} em
 * {@code RECEIVED}. Um {@code @TransactionalEventListener(phase = AFTER_COMMIT)} o traduz
 * em mensagem RabbitMQ só depois do commit (Implementation Plan M8: "publicação... nunca
 * dentro de transação") — nunca publicamos aqui dentro, só sinalizamos a intenção.
 */
public record ImportBatchReceivedEvent(UUID batchId, String correlationId) {
}
