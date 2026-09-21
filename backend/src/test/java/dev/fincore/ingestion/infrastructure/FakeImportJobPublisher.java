package dev.fincore.ingestion.infrastructure;

import dev.fincore.ingestion.application.ImportJobPublisher;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * O "publisher falso" que a TDS 17.3 pede para a maioria dos testes ("os demais [testes]
 * usam publisher falso"): grava as chamadas em memória em vez de falar com um broker real.
 * Ativo sempre que o perfil {@code rabbit-it} não está — ou seja, em toda a suíte, exceto o
 * teste dedicado de RabbitMQ real ({@code ImportJobRabbitMqIntegrationTest}).
 */
@Component
@Profile("!rabbit-it")
public class FakeImportJobPublisher implements ImportJobPublisher {

    public record PublishedJob(UUID batchId, String correlationId, int attempt) {
    }

    private final List<PublishedJob> published = new ArrayList<>();

    @Override
    public synchronized void publish(UUID batchId, String correlationId, int attempt) {
        published.add(new PublishedJob(batchId, correlationId, attempt));
    }

    public synchronized List<PublishedJob> published() {
        return List.copyOf(published);
    }

    public synchronized void clear() {
        published.clear();
    }
}
