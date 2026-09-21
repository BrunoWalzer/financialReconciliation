package dev.fincore.ingestion.infrastructure;

import dev.fincore.ingestion.application.ImportJobPublisher;
import dev.fincore.shared.messaging.MessagingProperties;
import dev.fincore.shared.messaging.RoutingKeys;
import java.time.Clock;
import java.util.UUID;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Implementação real de {@link ImportJobPublisher} — publica no exchange
 * {@code fincore.commands} com a routing key de {@code fincore.import.process} (TDS 17.2).
 * Único bean de produção; sem padrão de fallback silencioso — se {@link RabbitTemplate} não
 * existir (broker mal configurado), o contexto Spring falha ao subir, o que é o
 * comportamento correto (Implementation Plan M8, seção 11: "não fingir que a mensagem foi
 * publicada").
 *
 * <p>{@code @Profile("!test | rabbit-it")}: no perfil de teste padrão,
 * {@code RabbitAutoConfiguration} está excluída (a maioria dos testes usa publisher falso,
 * TDS 17.3) — sem este gate, o Spring tentaria criar este bean sem {@link RabbitTemplate}
 * disponível. Só o teste dedicado de RabbitMQ ativa o perfil adicional {@code rabbit-it}.
 */
@Component
@Profile("!test | rabbit-it")
public class RabbitImportJobPublisher implements ImportJobPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final MessagingProperties properties;
    private final Clock clock;

    public RabbitImportJobPublisher(RabbitTemplate rabbitTemplate, MessagingProperties properties, Clock clock) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public void publish(UUID batchId, String correlationId, int attempt) {
        ImportJobMessage message = new ImportJobMessage(batchId, correlationId, attempt, clock.instant());
        rabbitTemplate.convertAndSend(properties.commandsExchange(), RoutingKeys.IMPORT_PROCESS, message);
    }
}
