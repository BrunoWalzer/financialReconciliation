package dev.fincore.ingestion.infrastructure;

import dev.fincore.ingestion.application.MarkImportBatchFailedUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Esgotadas as 3 tentativas (TDS 17.3), este é o único ponto que decide o que acontece:
 * marca o {@code import_batch} {@code FAILED} com o motivo, então rejeita a mensagem sem
 * reencaminhar — o que o RabbitMQ, por causa do {@code x-dead-letter-exchange} da fila,
 * roteia sozinho para a DLQ. Uma ação, dois efeitos, exatamente como o Implementation Plan
 * M8 descreve: "esgotado o retry → DLQ + entidade em FAILED com failure_reason".
 *
 * <p>A DLQ nunca é reprocessada automaticamente (TDS 17.3) — só entra na fila principal de
 * novo via {@code POST /imports/{id}/retry}, ação humana.
 */
@Component
@Profile("!test | rabbit-it")
public class ImportJobFailureRecoverer implements MessageRecoverer {

    private static final Logger log = LoggerFactory.getLogger(ImportJobFailureRecoverer.class);

    private final MarkImportBatchFailedUseCase markImportBatchFailedUseCase;
    private final MessageConverter messageConverter;

    public ImportJobFailureRecoverer(MarkImportBatchFailedUseCase markImportBatchFailedUseCase, MessageConverter messageConverter) {
        this.markImportBatchFailedUseCase = markImportBatchFailedUseCase;
        this.messageConverter = messageConverter;
    }

    @Override
    public void recover(Message message, Throwable cause) {
        ImportJobMessage payload = (ImportJobMessage) messageConverter.fromMessage(message);
        log.error(
                "fincore.import.process esgotou as tentativas para batchId={} correlationId={} — FAILED + DLQ",
                payload.batchId(), payload.correlationId(), cause);
        markImportBatchFailedUseCase.execute(payload.batchId(), "retries esgotados: " + cause.getMessage());
        throw new org.springframework.amqp.AmqpRejectAndDontRequeueException("esgotado o retry, encaminhado para DLQ", cause);
    }
}
