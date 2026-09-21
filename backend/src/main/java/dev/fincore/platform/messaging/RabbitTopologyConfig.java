package dev.fincore.platform.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.fincore.shared.messaging.MessagingProperties;
import dev.fincore.shared.messaging.RoutingKeys;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * A topologia fixa de RabbitMQ do TDS 17.2: o exchange de comandos, o exchange de mortas e
 * a fila de {@code reconciliation.run} — declarada agora, consumida só no M11
 * (Implementation Plan M8: "a segunda é declarada agora e consumida no M11").
 *
 * <p>A fila {@code fincore.import.process} (dona: {@code ingestion}) fica em
 * {@code ingestion.infrastructure}, não aqui — cada fila é declarada por quem a usa; só o
 * exchange compartilhado e a fila ainda sem consumidor vivem em {@code platform}.
 *
 * <p>Sem topic exchange: não há roteamento dinâmico a fazer (TDS 17.2).
 *
 * <p>{@code @Profile("!test | rabbit-it")}: a maioria dos testes usa publisher falso (TDS
 * 17.3) e roda com {@code RabbitAutoConfiguration} excluída — sem isso, toda a suíte
 * tentaria abrir conexão com um broker real ao subir o contexto. Só o teste dedicado de
 * RabbitMQ ativa o perfil adicional {@code rabbit-it}.
 */
@Configuration
@Profile("!test | rabbit-it")
public class RabbitTopologyConfig {

    /**
     * Contrato de mensagem explícito, nunca serialização Java nativa (Implementation Plan
     * M8, seção 10) — o payload trafega como JSON puro, sem nome de pacote nem referência a
     * classe de domínio embutidos.
     */
    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public DirectExchange commandsExchange(MessagingProperties properties) {
        return new DirectExchange(properties.commandsExchange(), true, false);
    }

    @Bean
    public DirectExchange deadLetterExchange(MessagingProperties properties) {
        return new DirectExchange(properties.deadLetterExchange(), true, false);
    }

    @Bean
    public Queue reconciliationRunQueue(MessagingProperties properties) {
        return QueueBuilder.durable(properties.reconciliationRunQueue())
                .withArgument("x-dead-letter-exchange", properties.deadLetterExchange())
                .withArgument("x-dead-letter-routing-key", RoutingKeys.RECONCILIATION_RUN)
                .build();
    }

    @Bean
    public Queue reconciliationRunDeadLetterQueue(MessagingProperties properties) {
        return QueueBuilder.durable(properties.reconciliationRunDeadLetterQueue()).build();
    }

    @Bean
    public Binding reconciliationRunBinding(Queue reconciliationRunQueue, DirectExchange commandsExchange) {
        return BindingBuilder.bind(reconciliationRunQueue).to(commandsExchange).with(RoutingKeys.RECONCILIATION_RUN);
    }

    @Bean
    public Binding reconciliationRunDeadLetterBinding(
            Queue reconciliationRunDeadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(reconciliationRunDeadLetterQueue)
                .to(deadLetterExchange)
                .with(RoutingKeys.RECONCILIATION_RUN);
    }
}
