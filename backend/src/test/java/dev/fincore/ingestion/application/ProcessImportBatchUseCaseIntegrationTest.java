package dev.fincore.ingestion.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.fincore.AbstractIntegrationTest;
import dev.fincore.ingestion.domain.ImportBatch;
import dev.fincore.ingestion.domain.ImportStatus;
import dev.fincore.ingestion.infrastructure.ImportBatchRepository;
import dev.fincore.shared.identifier.Uuid7;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * A guarda de idempotência de {@link ProcessImportBatchUseCase} (TDS 17.3): mensagem órfã,
 * lote já em estado que só sai por ação humana, e duas "entregas" concorrentes do mesmo
 * lote não podem duplicar {@link dev.fincore.evidence.domain.FinancialRecord} nem produzir
 * uma transição de estado inválida.
 */
class ProcessImportBatchUseCaseIntegrationTest extends AbstractIntegrationTest {

    private static final String HEADER =
            "pedido_id;nsu;data_hora;valor_bruto;meio_pagamento;documento_cliente;tipo;descricao";

    @Autowired
    private ImportFileUseCase importFileUseCase;

    @Autowired
    private ProcessImportBatchUseCase processImportBatchUseCase;

    @Autowired
    private ImportBatchRepository importBatchRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @WithMockUser(authorities = "RECONCILIATION_ANALYST")
    void mensagemParaBatchIdInexistenteEhNoOp() {
        // Não lança — só registra e retorna (TDS 17.3: "entity == null: ack; log órfã; return").
        processImportBatchUseCase.execute(Uuid7.generate());
    }

    @Test
    @WithMockUser(authorities = "RECONCILIATION_ANALYST")
    void redeliveryDeMensagemParaLoteJaCompletedEhNoOpENaoDuplicaRegistros() {
        String marker = "PED-IDEMP-" + UUID.randomUUID();
        String content = HEADER + "\n" + marker + ";NSU1;10/09/2026 10:00:00;500,00;CREDITO;;VENDA;x\n";
        ImportBatch received = upload(content, LocalDate.of(2026, 9, 10));

        processImportBatchUseCase.execute(received.id());
        ImportBatch afterFirst = importBatchRepository.findById(received.id()).orElseThrow();
        assertThat(afterFirst.status()).isEqualTo(ImportStatus.COMPLETED);

        // Redelivery da mesma mensagem — guarda de idempotência deve barrar antes de reprocessar.
        processImportBatchUseCase.execute(received.id());

        Long count = jdbc.queryForObject(
                "select count(*) from financial_record where external_id = ?", Long.class, marker);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @WithMockUser(authorities = "RECONCILIATION_ANALYST")
    void loteEmFailedNaoEhReprocessadoPorRedeliveryPassivaSoPorRetryExplicito() {
        String content = HEADER + "\n" + "PED-FAILGUARD;NSU1;10/09/2026 10:00:00;500,00;CREDITO;;VENDA;x\n";
        ImportBatch received = upload(content, LocalDate.of(2026, 9, 16));

        // Marca FAILED diretamente (simula esgotamento de retry) sem passar pelo pipeline real.
        jdbc.update("update import_batch set status = 'FAILED', rejection_reason = 'simulado' where id = ?", received.id());

        processImportBatchUseCase.execute(received.id());

        ImportBatch reread = importBatchRepository.findById(received.id()).orElseThrow();
        assertThat(reread.status()).isEqualTo(ImportStatus.FAILED);
        assertThat(reread.rejectionReason()).isEqualTo("simulado");
    }

    @Test
    void duasEntregasConcorrentesDoMesmoLoteNaoDuplicamFinancialRecord() throws Exception {
        String marker = "PED-CONC-" + UUID.randomUUID();
        String content = HEADER + "\n" + marker + ";NSU1;10/09/2026 10:00:00;500,00;CREDITO;;VENDA;x\n";
        ImportBatch received = uploadAsSystem(content, LocalDate.of(2026, 9, 17));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<?>> futures = List.of(
                executor.submit(() -> runAsAnalyst(startLatch, received.id())),
                executor.submit(() -> runAsAnalyst(startLatch, received.id())));

        startLatch.countDown();
        for (Future<?> future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }
        executor.shutdown();

        Long count = jdbc.queryForObject(
                "select count(*) from financial_record where external_id = ?", Long.class, marker);
        assertThat(count).isEqualTo(1);

        ImportBatch reread = importBatchRepository.findById(received.id()).orElseThrow();
        assertThat(reread.status()).isEqualTo(ImportStatus.COMPLETED);
    }

    private void runAsAnalyst(CountDownLatch startLatch, UUID batchId) {
        var auth = new TestingAuthenticationToken("analyst", "n/a", "RECONCILIATION_ANALYST");
        auth.setAuthenticated(true);
        SecurityContextHolder.setContext(new SecurityContextImpl(auth));
        try {
            startLatch.await();
            processImportBatchUseCase.execute(batchId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private ImportBatch upload(String content, LocalDate referenceDate) {
        ImportFileCommand command = new ImportFileCommand(
                "INTERNAL_SALES", "arquivo-" + Uuid7.generate() + ".csv",
                content.getBytes(StandardCharsets.UTF_8), referenceDate, null, null);
        return importFileUseCase.execute(command, Uuid7.generate(), "analista@fincore.dev");
    }

    private ImportBatch uploadAsSystem(String content, LocalDate referenceDate) {
        var auth = new TestingAuthenticationToken("analyst", "n/a", "RECONCILIATION_ANALYST");
        auth.setAuthenticated(true);
        SecurityContextHolder.setContext(new SecurityContextImpl(auth));
        try {
            return upload(content, referenceDate);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
