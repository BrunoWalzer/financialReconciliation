package dev.fincore.ingestion.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.fincore.AbstractIntegrationTest;
import dev.fincore.ingestion.domain.ImportBatch;
import dev.fincore.ingestion.domain.ImportStatus;
import dev.fincore.ingestion.infrastructure.FakeImportJobPublisher;
import dev.fincore.ingestion.infrastructure.ImportBatchRepository;
import dev.fincore.shared.identifier.Uuid7;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

/** {@code POST /imports/{id}/retry} (Implementation Plan M8) — só a partir de {@code FAILED}. */
class RetryImportUseCaseIntegrationTest extends AbstractIntegrationTest {

    private static final String HEADER =
            "pedido_id;nsu;data_hora;valor_bruto;meio_pagamento;documento_cliente;tipo;descricao";

    @Autowired
    private ImportFileUseCase importFileUseCase;

    @Autowired
    private ProcessImportBatchUseCase processImportBatchUseCase;

    @Autowired
    private RetryImportUseCase retryImportUseCase;

    @Autowired
    private ImportBatchRepository importBatchRepository;

    @Autowired
    private FakeImportJobPublisher fakeImportJobPublisher;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @WithMockUser(authorities = "RECONCILIATION_ANALYST")
    void deveRetentarLoteFailedVoltandoParaReceivedEPublicandoNovaMensagem() {
        String marker = "PED-RETRY-" + UUID.randomUUID();
        String content = HEADER + "\n" + marker + ";NSU1;10/09/2026 10:00:00;500,00;CREDITO;;VENDA;x\n";
        ImportBatch received = upload(content, LocalDate.of(2026, 9, 18));

        jdbc.update("update import_batch set status = 'FAILED', rejection_reason = 'falha simulada' where id = ?", received.id());
        fakeImportJobPublisher.clear();

        ImportBatch retried = retryImportUseCase.execute(received.id(), Uuid7.generate(), "analista@fincore.dev");

        assertThat(retried.status()).isEqualTo(ImportStatus.RECEIVED);
        assertThat(retried.rejectionReason()).isNull();
        assertThat(fakeImportJobPublisher.published()).hasSize(1);
        assertThat(fakeImportJobPublisher.published().get(0).batchId()).isEqualTo(received.id());

        // O reprocessamento completo funciona e não duplica (ON CONFLICT DO NOTHING, TDS 9.5).
        processImportBatchUseCase.execute(received.id());
        ImportBatch completed = importBatchRepository.findById(received.id()).orElseThrow();
        assertThat(completed.status()).isEqualTo(ImportStatus.COMPLETED);

        Long count = jdbc.queryForObject("select count(*) from financial_record where external_id = ?", Long.class, marker);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @WithMockUser(authorities = "RECONCILIATION_ANALYST")
    void naoDeveRetentarLoteQueNaoEstaEmFailed() {
        String content = HEADER + "\nPED-RETRY-BAD;NSU1;10/09/2026 10:00:00;500,00;CREDITO;;VENDA;x\n";
        ImportBatch received = upload(content, LocalDate.of(2026, 9, 19));

        assertThatThrownBy(() -> retryImportUseCase.execute(received.id(), Uuid7.generate(), "analista@fincore.dev"))
                .isInstanceOf(ImportBatchNotRetryableException.class);
    }

    @Test
    @WithMockUser(authorities = "AUDITOR")
    void auditorNaoDeveConseguirRetentar() {
        assertThatThrownBy(() -> retryImportUseCase.execute(Uuid7.generate(), Uuid7.generate(), "auditor@fincore.dev"))
                .isInstanceOf(AccessDeniedException.class);
    }

    private ImportBatch upload(String content, LocalDate referenceDate) {
        ImportFileCommand command = new ImportFileCommand(
                "INTERNAL_SALES", "arquivo-" + Uuid7.generate() + ".csv",
                content.getBytes(StandardCharsets.UTF_8), referenceDate, null, null);
        return importFileUseCase.execute(command, Uuid7.generate(), "analista@fincore.dev");
    }
}
