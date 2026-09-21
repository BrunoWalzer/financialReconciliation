package dev.fincore.ingestion.infrastructure;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.fincore.AbstractIntegrationTest;
import dev.fincore.ingestion.application.ImportFileCommand;
import dev.fincore.ingestion.application.ImportFileUseCase;
import dev.fincore.ingestion.domain.ImportBatch;
import dev.fincore.ingestion.domain.ImportStatus;
import dev.fincore.shared.identifier.Uuid7;
import dev.fincore.shared.messaging.MessagingProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * O único teste com RabbitMQ real (TDS 17.3: "os demais [testes] usam publisher falso") —
 * prova o contrato consumidor + DLQ de ponta a ponta: upload publica de verdade, um
 * {@code @RabbitListener} real consome e processa, e o esgotamento de tentativas leva o
 * lote a {@code FAILED} com a mensagem morta na DLQ.
 *
 * <p>Ativa o perfil adicional {@code rabbit-it} e limpa a exclusão de
 * {@code RabbitAutoConfiguration} do perfil {@code test} (ver {@code application-test.yml})
 * — só esta classe tem um broker real disponível; todas as outras usam
 * {@link FakeImportJobPublisher}.
 */
@ActiveProfiles({"test", "rabbit-it"})
@SpringBootTest(properties = "spring.autoconfigure.exclude=")
class ImportJobRabbitMqIntegrationTest extends AbstractIntegrationTest {

    private static final String HEADER =
            "pedido_id;nsu;data_hora;valor_bruto;meio_pagamento;documento_cliente;tipo;descricao";

    static final RabbitMQContainer RABBITMQ =
            new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management-alpine"));

    static {
        RABBITMQ.start();
    }

    @DynamicPropertySource
    static void rabbitProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port", RABBITMQ::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBITMQ::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBITMQ::getAdminPassword);
    }

    @Autowired
    private ImportFileUseCase importFileUseCase;

    @Autowired
    private ImportBatchRepository importBatchRepository;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private MessagingProperties messagingProperties;

    @Autowired
    private JdbcTemplate jdbc;

    @Value("${fincore.ingestion.file-storage-path}")
    private String fileStoragePath;

    @Test
    @WithMockUser(authorities = "RECONCILIATION_ANALYST")
    void uploadPublicaDeVerdadeEOWorkerRealProcessaAteCompleted() {
        String content = HEADER + "\nPED-RMQ-OK;NSU1;10/09/2026 10:00:00;500,00;CREDITO;;VENDA;x\n";
        ImportFileCommand command = new ImportFileCommand(
                "INTERNAL_SALES", "arquivo-" + Uuid7.generate() + ".csv",
                content.getBytes(UTF_8), LocalDate.of(2026, 9, 10), null, null);

        ImportBatch received = importFileUseCase.execute(command, Uuid7.generate(), "analista@fincore.dev");
        assertThat(received.status()).isEqualTo(ImportStatus.RECEIVED);

        Awaitility.await()
                .atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> {
                    ImportBatch reread = importBatchRepository.findById(received.id()).orElseThrow();
                    assertThat(reread.status()).isEqualTo(ImportStatus.COMPLETED);
                    assertThat(reread.acceptedCount()).isEqualTo(1);
                });
    }

    @Test
    @WithMockUser(authorities = "RECONCILIATION_ANALYST")
    void esgotadasAsTentativasOLoteVaiParaFailedEAMensagemMorreNaDlq() throws IOException {
        String content = HEADER + "\nPED-RMQ-DLQ;NSU1;10/09/2026 10:00:00;500,00;CREDITO;;VENDA;x\n";
        ImportFileCommand command = new ImportFileCommand(
                "INTERNAL_SALES", "arquivo-" + Uuid7.generate() + ".csv",
                content.getBytes(UTF_8), LocalDate.of(2026, 9, 11), null, null);

        ImportBatch received = importFileUseCase.execute(command, Uuid7.generate(), "analista@fincore.dev");

        // Remove o arquivo gravado: toda tentativa de leitura por ProcessImportBatchUseCase
        // falha da mesma forma, determinística — não é uma exceção simulada de propósito
        // específico do teste, é uma falha de infraestrutura genuína (arquivo sumiu).
        Files.delete(Path.of(fileStoragePath).resolve(received.storageKey()));

        Awaitility.await()
                .atMost(Duration.ofSeconds(40))
                .untilAsserted(() -> {
                    ImportBatch reread = importBatchRepository.findById(received.id()).orElseThrow();
                    assertThat(reread.status()).isEqualTo(ImportStatus.FAILED);
                    assertThat(reread.rejectionReason()).contains("retries esgotados");
                });

        Message dead = rabbitTemplate.receive(messagingProperties.importProcessDeadLetterQueue(), 5000);
        assertThat(dead).isNotNull();
    }
}
