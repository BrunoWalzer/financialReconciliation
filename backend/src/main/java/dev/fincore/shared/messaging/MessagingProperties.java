package dev.fincore.shared.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Nomes da topologia fixa do RabbitMQ (TDS 17.2) — externalizados para não hardcodear
 * exchange/fila no código (Implementation Plan M8, seção 23), embora os valores em si não
 * sejam regra de negócio (não mudam entre ambientes).
 *
 * <p>Fica em {@code shared}, não em {@code platform}: tanto {@code platform.messaging}
 * (declara a topologia) quanto {@code ingestion.infrastructure} (publica/consome
 * {@code fincore.import.process}) precisam disto, e {@code platform} já depende de
 * {@code ingestion} (via {@code GlobalErrorHandler} traduzindo suas exceções) — colocar
 * isto em {@code platform} criaria um ciclo de módulos. Mesmo raciocínio de
 * {@code shared.correlation.CorrelationId}: constante transversal, sem lógica de domínio.
 */
@ConfigurationProperties(prefix = "fincore.messaging")
public record MessagingProperties(
        String commandsExchange,
        String deadLetterExchange,
        String importProcessQueue,
        String importProcessDeadLetterQueue,
        String reconciliationRunQueue,
        String reconciliationRunDeadLetterQueue) {
}
