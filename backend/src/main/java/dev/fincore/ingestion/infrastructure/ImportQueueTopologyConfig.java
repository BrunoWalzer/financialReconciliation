package dev.fincore.ingestion.infrastructure;

import dev.fincore.shared.messaging.MessagingProperties;
import dev.fincore.shared.messaging.RoutingKeys;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * A fila {@code fincore.import.process} (TDS 17.1/17.2) — dona: {@code ingestion}, único
 * consumidor. O exchange {@code fincore.commands} e o {@code fincore.dlx} são declarados em
 * {@code platform.messaging.RabbitTopologyConfig} (compartilhados com a fila de
 * {@code reconciliation.run}, que {@code ingestion} não conhece).
 */
@Configuration
@Profile("!test | rabbit-it")
public class ImportQueueTopologyConfig {

    @Bean
    public Queue importProcessQueue(MessagingProperties properties) {
        return QueueBuilder.durable(properties.importProcessQueue())
                .withArgument("x-dead-letter-exchange", properties.deadLetterExchange())
                .withArgument("x-dead-letter-routing-key", RoutingKeys.IMPORT_PROCESS)
                .build();
    }

    @Bean
    public Queue importProcessDeadLetterQueue(MessagingProperties properties) {
        return QueueBuilder.durable(properties.importProcessDeadLetterQueue()).build();
    }

    @Bean
    public Binding importProcessBinding(Queue importProcessQueue, DirectExchange commandsExchange) {
        return BindingBuilder.bind(importProcessQueue).to(commandsExchange).with(RoutingKeys.IMPORT_PROCESS);
    }

    @Bean
    public Binding importProcessDeadLetterBinding(Queue importProcessDeadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(importProcessDeadLetterQueue).to(deadLetterExchange).with(RoutingKeys.IMPORT_PROCESS);
    }
}
