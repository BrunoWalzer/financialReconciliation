package dev.fincore.ingestion.application;

import dev.fincore.audit.domain.ActorRef;
import dev.fincore.ingestion.domain.ImportBatch;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Marca um {@code import_batch} como {@code FAILED} a partir de fora do pipeline síncrono —
 * hoje só {@code ingestion.infrastructure.ImportJobFailureRecoverer} chama isto, quando o
 * RabbitMQ esgota as tentativas de entrega (TDS 17.3: "esgotado o retry → DLQ, e a entidade
 * vai para FAILED com motivo"). {@link ImportBatchTransactionalSteps} é package-private —
 * este é o ponto público que a infraestrutura de mensageria pode chamar sem enxergar os
 * passos transacionais internos.
 */
@Service
public class MarkImportBatchFailedUseCase {

    private final ImportBatchTransactionalSteps transactionalSteps;
    private final Clock clock;

    public MarkImportBatchFailedUseCase(ImportBatchTransactionalSteps transactionalSteps, Clock clock) {
        this.transactionalSteps = transactionalSteps;
        this.clock = clock;
    }

    public ImportBatch execute(UUID batchId, String reason) {
        return transactionalSteps.markFailed(batchId, reason, clock.instant(), ActorRef.system(batchId));
    }
}
