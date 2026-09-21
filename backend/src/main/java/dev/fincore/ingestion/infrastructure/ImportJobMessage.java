package dev.fincore.ingestion.infrastructure;

import java.time.Instant;
import java.util.UUID;

/**
 * O payload de {@code fincore.import.process} (TDS 17.2) — identificador e correlação, nunca
 * dado de negócio: o PostgreSQL é a fonte de verdade, a mensagem só diz "há trabalho
 * pendente para este id". Serializado como JSON puro (Jackson) — nunca serialização nativa
 * Java, nunca referência a classe de domínio.
 *
 * <pre>{"batchId": "018f...", "correlationId": "01J9...", "attempt": 1, "enqueuedAt": "2026-09-14T06:00:03Z"}</pre>
 */
public record ImportJobMessage(UUID batchId, String correlationId, int attempt, Instant enqueuedAt) {
}
