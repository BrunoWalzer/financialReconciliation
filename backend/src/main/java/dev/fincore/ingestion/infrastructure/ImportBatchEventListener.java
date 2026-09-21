package dev.fincore.ingestion.infrastructure;

import dev.fincore.ingestion.application.ImportBatchReceivedEvent;
import dev.fincore.ingestion.application.ImportJobPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Traduz {@link ImportBatchReceivedEvent} em mensagem RabbitMQ — só depois que a transação
 * que o publicou commitou (Implementation Plan M8: "publicação... nunca dentro de
 * transação"). Se o commit reverter, o evento nunca chega aqui.
 *
 * <p>Se {@link ImportJobPublisher#publish} falhar (broker indisponível), a exceção é
 * registrada e engolida aqui — não faz sentido propagá-la: a transação já commitou, o
 * {@code import_batch} já existe em {@code RECEIVED}/{@code PROCESSING}. O sweep de
 * trabalhos travados (TDS 17.5, {@code RecoverStuckImportBatchesUseCase}) é o mecanismo
 * documentado de recuperação para exatamente este caso — "mensagem perdida entre o commit e
 * a publicação é recuperada porque a entidade ficou em estado não-terminal".
 */
@Component
public class ImportBatchEventListener {

    private static final Logger log = LoggerFactory.getLogger(ImportBatchEventListener.class);

    private final ImportJobPublisher publisher;

    public ImportBatchEventListener(ImportJobPublisher publisher) {
        this.publisher = publisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onImportBatchReceived(ImportBatchReceivedEvent event) {
        try {
            publisher.publish(event.batchId(), event.correlationId(), 1);
        } catch (RuntimeException e) {
            log.error(
                    "Falha ao publicar fincore.import.process para o lote {} (correlationId={}) — "
                            + "o sweep de trabalhos travados o recuperará",
                    event.batchId(), event.correlationId(), e);
        }
    }
}
