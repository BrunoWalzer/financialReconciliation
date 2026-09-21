package dev.fincore.ingestion.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.fincore.AbstractIntegrationTest;
import dev.fincore.ingestion.infrastructure.FakeImportJobPublisher;
import dev.fincore.shared.identifier.Uuid7;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * A publicação de {@link ImportBatchReceivedEvent} → {@link ImportJobPublisher} (Implementation
 * Plan M8, TDS 17.1) — usando {@link FakeImportJobPublisher}, o "publisher falso" que a
 * própria TDS 17.3 pede para a maioria dos testes.
 */
class ImportFileUseCasePublishingIntegrationTest extends AbstractIntegrationTest {

    private static final String HEADER =
            "pedido_id;nsu;data_hora;valor_bruto;meio_pagamento;documento_cliente;tipo;descricao";

    @Autowired
    private ImportFileUseCase importFileUseCase;

    @Autowired
    private FakeImportJobPublisher fakeImportJobPublisher;

    @Test
    @WithMockUser(authorities = "RECONCILIATION_ANALYST")
    void uploadValidoPublicaExatamenteUmaMensagemComOBatchIdEACorrelationIdCorretos() {
        fakeImportJobPublisher.clear();
        String content = HEADER + "\nPED-PUB-1;NSU1;10/09/2026 10:00:00;500,00;CREDITO;;VENDA;x\n";
        ImportFileCommand command = new ImportFileCommand(
                "INTERNAL_SALES", "arquivo-" + Uuid7.generate() + ".csv",
                content.getBytes(StandardCharsets.UTF_8), LocalDate.of(2026, 9, 10), null, null);

        var received = importFileUseCase.execute(command, Uuid7.generate(), "analista@fincore.dev");

        List<FakeImportJobPublisher.PublishedJob> jobs = fakeImportJobPublisher.published();
        assertThat(jobs).hasSize(1);
        assertThat(jobs.get(0).batchId()).isEqualTo(received.id());
        assertThat(jobs.get(0).correlationId()).isEqualTo(received.correlationId());
        assertThat(jobs.get(0).attempt()).isEqualTo(1);
    }

    @Test
    @WithMockUser(authorities = "RECONCILIATION_ANALYST")
    void arquivoDuplicadoNaoPublicaSegundaMensagem() {
        String content = HEADER + "\nPED-PUB-DUP;NSU1;10/09/2026 10:00:00;500,00;CREDITO;;VENDA;x\n";
        LocalDate referenceDate = LocalDate.of(2026, 9, 15);
        ImportFileCommand command = new ImportFileCommand(
                "INTERNAL_SALES", "arquivo-" + Uuid7.generate() + ".csv",
                content.getBytes(StandardCharsets.UTF_8), referenceDate, null, null);
        importFileUseCase.execute(command, Uuid7.generate(), "analista@fincore.dev");

        fakeImportJobPublisher.clear();
        ImportFileCommand duplicate = new ImportFileCommand(
                "INTERNAL_SALES", "arquivo-" + Uuid7.generate() + ".csv",
                content.getBytes(StandardCharsets.UTF_8), referenceDate, null, null);

        assertThatThrownBy(() -> importFileUseCase.execute(duplicate, Uuid7.generate(), "analista@fincore.dev"))
                .isInstanceOf(DuplicateFileException.class);

        // A transação que criaria o segundo lote nunca commitou — nenhum evento
        // AFTER_COMMIT disparou, logo nenhuma mensagem nova foi publicada (Implementation
        // Plan M8: "publicação... nunca dentro de transação", e aqui não há nem transação
        // bem-sucedida para disparar o listener).
        assertThat(fakeImportJobPublisher.published()).isEmpty();
    }
}
