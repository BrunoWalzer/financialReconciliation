package dev.fincore.ingestion.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.fincore.AbstractIntegrationTest;
import dev.fincore.evidence.domain.FinancialRecord;
import dev.fincore.evidence.infrastructure.FinancialRecordRepository;
import dev.fincore.ingestion.domain.ImportBatch;
import dev.fincore.ingestion.domain.ImportStatus;
import dev.fincore.ingestion.domain.RejectedRecord;
import dev.fincore.ingestion.domain.RejectionReasonCode;
import dev.fincore.ingestion.infrastructure.ImportBatchRepository;
import dev.fincore.ingestion.infrastructure.RejectedRecordRepository;
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
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * {@link ImportFileUseCase} contra PostgreSQL real — o pipeline síncrono inteiro
 * (Implementation Plan M6/TDS 9), cada comportamento-chave de TDS 9.7 com fixture própria.
 */
class ImportFileUseCaseIntegrationTest extends AbstractIntegrationTest {

    private static final UUID INTERNAL_SALES_ID = UUID.fromString("00000000-0000-7000-8000-000000000101");
    private static final UUID ACQUIRER_SETTLEMENT_ID = UUID.fromString("00000000-0000-7000-8000-000000000102");
    private static final String INTERNAL_SALES_HEADER =
            "pedido_id;nsu;data_hora;valor_bruto;meio_pagamento;documento_cliente;tipo;descricao";
    private static final String ACQUIRER_HEADER =
            "nsu;data_liquidacao;valor_bruto;valor_taxa;valor_liquido;bandeira;tipo_operacao;parcela";

    @Autowired
    private ImportFileUseCase importFileUseCase;

    @Autowired
    private ProcessImportBatchUseCase processImportBatchUseCase;

    @Autowired
    private ImportBatchRepository importBatchRepository;

    @Autowired
    private FinancialRecordRepository financialRecordRepository;

    @Autowired
    private RejectedRecordRepository rejectedRecordRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @WithMockUser(authorities = "RECONCILIATION_ANALYST")
    void deveImportarArquivoDeInternalSalesComSucesso() {
        String marker = "IS-OK-" + UUID.randomUUID();
        String content = INTERNAL_SALES_HEADER + "\n"
                + marker + "-1;NSU-A;10/09/2026 10:00:00;500,00;CREDITO;11144477735;VENDA;Pedido A\n"
                + marker + "-2;NSU-B;10/09/2026 11:00:00;300,00;PIX;;VENDA;Pedido B\n";

        ImportBatch batch = execute("INTERNAL_SALES", content, LocalDate.of(2026, 9, 10));

        assertThat(batch.status()).isEqualTo(ImportStatus.COMPLETED);
        assertThat(batch.totalLines()).isEqualTo(2);
        assertThat(batch.acceptedCount()).isEqualTo(2);
        assertThat(batch.rejectedCount()).isZero();

        Long persisted = jdbc.queryForObject(
                "select count(*) from financial_record where source_id = ? and external_id like ?",
                Long.class, INTERNAL_SALES_ID, marker + "%");
        assertThat(persisted).isEqualTo(2);
    }

    @Test
    @WithMockUser(authorities = "RECONCILIATION_ANALYST")
    void deveImportarArquivoDeAcquirerSettlementComSucesso() {
        String marker = "NSU-AQ-" + UUID.randomUUID();
        String content = ACQUIRER_HEADER + "\n"
                + marker + ";11/09/2026;500,00;12,80;487,20;VISA;CREDITO_A_VISTA;1/1\n";

        ImportBatch batch = execute("ACQUIRER_SETTLEMENT", content, LocalDate.of(2026, 9, 11));

        assertThat(batch.status()).isEqualTo(ImportStatus.COMPLETED);
        assertThat(batch.acceptedCount()).isEqualTo(1);

        FinancialRecord record = financialRecordRepository.findAll(
                        (root, query, cb) -> cb.equal(root.get("correlationKey"), marker),
                        org.springframework.data.domain.PageRequest.of(0, 10))
                .getContent().get(0);
        assertThat(record.grossAmount().amountMinor()).isEqualTo(50_000);
        assertThat(record.declaredFeeAmount().amountMinor()).isEqualTo(1_280);
        assertThat(record.netAmount().amountMinor()).isEqualTo(48_720);
        assertThat(record.counterpartyDocument()).isNull();
    }

    @Test
    @WithMockUser(authorities = "RECONCILIATION_ANALYST")
    void devePreservarRawLineExatamente() {
        String marker = "IS-RAW-" + UUID.randomUUID();
        String dataLine = marker + ";NSU-RAW;10/09/2026 10:00:00;500,00;  CREDITO  ;;VENDA;Descrição   com   espaços";
        String content = INTERNAL_SALES_HEADER + "\n" + dataLine + "\n";

        execute("INTERNAL_SALES", content, LocalDate.of(2026, 9, 10));

        String rawLine = jdbc.queryForObject(
                "select raw_line from financial_record where source_id = ? and external_id = ?",
                String.class, INTERNAL_SALES_ID, marker);
        assertThat(rawLine).isEqualTo(dataLine);
    }

    @Test
    @WithMockUser(authorities = "RECONCILIATION_ANALYST")
    void deveIgnorarSegundaLinhaComMesmoExternalIdEContarComoJaExistente() {
        String marker = "IS-DUP-" + UUID.randomUUID();
        String content = INTERNAL_SALES_HEADER + "\n"
                + marker + ";NSU-1;10/09/2026 10:00:00;500,00;CREDITO;;VENDA;primeira\n"
                + marker + ";NSU-2;10/09/2026 11:00:00;900,00;PIX;;VENDA;segunda, mesmo pedido\n";

        ImportBatch batch = execute("INTERNAL_SALES", content, LocalDate.of(2026, 9, 10));

        assertThat(batch.status()).isEqualTo(ImportStatus.COMPLETED);
        assertThat(batch.acceptedCount()).isEqualTo(1);
        assertThat(batch.alreadyExistingCount()).isEqualTo(1);

        Long persisted = jdbc.queryForObject(
                "select count(*) from financial_record where source_id = ? and external_id = ?",
                Long.class, INTERNAL_SALES_ID, marker);
        assertThat(persisted).isEqualTo(1);
    }

    @Test
    @WithMockUser(authorities = "RECONCILIATION_ANALYST")
    void devePermitirFingerprintRepetidoParaDuasLinhasDeAcquirerSettlement() {
        // Domain §9.3: sem external_id, a fonte de liquidação nunca deduplica por
        // impressão digital — duas linhas idênticas continuam duas evidências.
        String marker = "NSU-FP-" + UUID.randomUUID();
        String line = marker + ";12/09/2026;700,00;15,00;685,00;VISA;CREDITO_A_VISTA;1/1";
        String content = ACQUIRER_HEADER + "\n" + line + "\n" + line + "\n";

        ImportBatch batch = execute("ACQUIRER_SETTLEMENT", content, LocalDate.of(2026, 9, 12));

        assertThat(batch.status()).isEqualTo(ImportStatus.COMPLETED);
        assertThat(batch.acceptedCount()).isEqualTo(2);

        List<String> fingerprints = jdbc.queryForList(
                "select fingerprint from financial_record where source_id = ? and correlation_key = ?",
                String.class, ACQUIRER_SETTLEMENT_ID, marker);
        assertThat(fingerprints).hasSize(2);
        assertThat(fingerprints.get(0)).isEqualTo(fingerprints.get(1));
    }

    @Test
    @WithMockUser(authorities = "RECONCILIATION_ANALYST")
    void importacaoParcialGeraCompletedWithRejects() {
        String marker = "IS-PARTIAL-" + UUID.randomUUID();
        String content = INTERNAL_SALES_HEADER + "\n"
                + marker + "-1;NSU1;10/09/2026 10:00:00;500,00;CREDITO;;VENDA;valida\n"
                + marker + "-2;NSU2;10/09/2026 10:00:00;0,00;CREDITO;;VENDA;valor zero\n"
                + marker + "-3;NSU3;10/09/2026 10:00:00;1,234;CREDITO;;VENDA;decimal ambiguo\n";

        ImportBatch batch = execute("INTERNAL_SALES", content, LocalDate.of(2026, 9, 10));

        assertThat(batch.status()).isEqualTo(ImportStatus.COMPLETED_WITH_REJECTS);
        assertThat(batch.totalLines()).isEqualTo(3);
        assertThat(batch.acceptedCount()).isEqualTo(1);
        assertThat(batch.rejectedCount()).isEqualTo(2);

        List<RejectedRecord> rejected = rejectedRecordRepository.findByImportBatchId(
                        batch.id(), org.springframework.data.domain.PageRequest.of(0, 10))
                .getContent();
        assertThat(rejected).hasSize(2);
        assertThat(rejected).extracting(RejectedRecord::reasonCode)
                .containsExactlyInAnyOrder(RejectionReasonCode.ZERO_AMOUNT, RejectionReasonCode.AMBIGUOUS_OR_INVALID_DECIMAL);
    }

    @Test
    @WithMockUser(authorities = "RECONCILIATION_ANALYST")
    void arquivoComTodasAsLinhasInvalidasEhRejectedMasPreservaAsLinhasRejeitadas() {
        String content = INTERNAL_SALES_HEADER + "\n"
                + "PED-X;NSU1;10/09/2026 10:00:00;0,00;CREDITO;;VENDA;zero\n"
                + "PED-Y;NSU2;10/09/2026 10:00:00;1,234;CREDITO;;VENDA;ambiguo\n";

        ImportBatch batch = execute("INTERNAL_SALES", content, LocalDate.of(2026, 9, 20));

        // TDS 9.7: REJECTED por zero registros financeiros — mas os rejeitos existem.
        assertThat(batch.status()).isEqualTo(ImportStatus.REJECTED);
        assertThat(batch.rejectionReason()).isEqualTo("ALL_LINES_INVALID");
        assertThat(batch.acceptedCount()).isZero();
        assertThat(batch.rejectedCount()).isEqualTo(2);

        long rejectedRows = rejectedRecordRepository.countByImportBatchId(batch.id());
        assertThat(rejectedRows).isEqualTo(2);
    }

    @Test
    @WithMockUser(authorities = "RECONCILIATION_ANALYST")
    void arquivoVazioEhRejectedComMotivoProprio() {
        ImportBatch batch = execute("INTERNAL_SALES", "", LocalDate.of(2026, 9, 21));

        assertThat(batch.status()).isEqualTo(ImportStatus.REJECTED);
        assertThat(batch.rejectionReason()).isEqualTo("EMPTY_FILE");
        assertThat(batch.acceptedCount()).isZero();
        assertThat(batch.rejectedCount()).isZero();
    }

    @Test
    @WithMockUser(authorities = "RECONCILIATION_ANALYST")
    void arquivoSoComCabecalhoEhRejectedComoVazio() {
        ImportBatch batch = execute("INTERNAL_SALES", INTERNAL_SALES_HEADER + "\n", LocalDate.of(2026, 9, 22));

        assertThat(batch.status()).isEqualTo(ImportStatus.REJECTED);
        assertThat(batch.rejectionReason()).isEqualTo("EMPTY_FILE");
    }

    @Test
    @WithMockUser(authorities = "RECONCILIATION_ANALYST")
    void cabecalhoIncompativelEhRejectedSemRegistrosNemRejeitos() {
        String content = ACQUIRER_HEADER + "\n" + "NSU1;11/09/2026;500,00;12,80;487,20;VISA;CREDITO_A_VISTA;1/1\n";

        ImportBatch batch = execute("INTERNAL_SALES", content, LocalDate.of(2026, 9, 23));

        assertThat(batch.status()).isEqualTo(ImportStatus.REJECTED);
        assertThat(batch.rejectionReason()).isEqualTo("LAYOUT_MISMATCH");
        assertThat(batch.acceptedCount()).isZero();
        assertThat(batch.rejectedCount()).isZero();
        assertThat(rejectedRecordRepository.countByImportBatchId(batch.id())).isZero();
    }

    @Test
    @WithMockUser(authorities = "RECONCILIATION_ANALYST")
    void arquivoIdenticoReenviadoEhRejeitadoComReferenciaAoOriginal() {
        String content = INTERNAL_SALES_HEADER + "\n" + "PED-DUPFILE;NSU1;10/09/2026 10:00:00;500,00;CREDITO;;VENDA;x\n";
        LocalDate referenceDate = LocalDate.of(2026, 9, 24);
        ImportBatch original = execute("INTERNAL_SALES", content, referenceDate);

        assertThatThrownBy(() -> execute("INTERNAL_SALES", content, referenceDate))
                .isInstanceOf(DuplicateFileException.class)
                .satisfies(e -> assertThat(((DuplicateFileException) e).originalImportBatchId()).isEqualTo(original.id()));
    }

    @Test
    @WithMockUser(authorities = "AUDITOR")
    void auditorNaoDeveConseguirImportar() {
        String content = INTERNAL_SALES_HEADER + "\n";

        assertThatThrownBy(() -> execute("INTERNAL_SALES", content, LocalDate.of(2026, 9, 25)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(authorities = "ADMINISTRATOR")
    void administradorNaoDeveConseguirImportar() {
        String content = INTERNAL_SALES_HEADER + "\n";

        assertThatThrownBy(() -> execute("INTERNAL_SALES", content, LocalDate.of(2026, 9, 26)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void semAutenticacaoNaoDeveConseguirImportar() {
        String content = INTERNAL_SALES_HEADER + "\n";

        assertThatThrownBy(() -> execute("INTERNAL_SALES", content, LocalDate.of(2026, 9, 27)))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @WithMockUser(authorities = "RECONCILIATION_ANALYST")
    void deveRegistrarAuditoriaDeCriacaoEConclusao() {
        String marker = "IS-AUDIT-" + UUID.randomUUID();
        String content = INTERNAL_SALES_HEADER + "\n" + marker + ";NSU1;10/09/2026 10:00:00;500,00;CREDITO;;VENDA;x\n";

        ImportBatch batch = execute("INTERNAL_SALES", content, LocalDate.of(2026, 9, 28));

        Integer createdCount = jdbc.queryForObject(
                "select count(*) from audit_event where action = 'IMPORT_BATCH_CREATED' and entity_id = ?",
                Integer.class, batch.id());
        Integer completedCount = jdbc.queryForObject(
                "select count(*) from audit_event where action = 'IMPORT_BATCH_COMPLETED' and entity_id = ?",
                Integer.class, batch.id());
        assertThat(createdCount).isEqualTo(1);
        assertThat(completedCount).isEqualTo(1);
    }

    @Test
    void duasImportacoesConcorrentesDoMesmoArquivoSoUmaDeveTerSucesso() throws Exception {
        String content = INTERNAL_SALES_HEADER + "\n"
                + "PED-RACE;NSU1;10/09/2026 10:00:00;500,00;CREDITO;;VENDA;x\n";
        LocalDate referenceDate = LocalDate.of(2026, 9, 29);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger duplicateCount = new AtomicInteger();

        List<Future<?>> futures = List.of(
                executor.submit(() -> runAsAnalyst(startLatch, content, referenceDate, successCount, duplicateCount)),
                executor.submit(() -> runAsAnalyst(startLatch, content, referenceDate, successCount, duplicateCount)));

        startLatch.countDown();
        for (Future<?> future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(duplicateCount.get()).isEqualTo(1);
    }

    private void runAsAnalyst(
            CountDownLatch startLatch, String content, LocalDate referenceDate,
            AtomicInteger successCount, AtomicInteger duplicateCount) {
        var auth = new org.springframework.security.authentication.TestingAuthenticationToken(
                "analyst", "n/a", "RECONCILIATION_ANALYST");
        auth.setAuthenticated(true);
        SecurityContextHolder.setContext(new SecurityContextImpl(auth));
        try {
            startLatch.await();
            execute("INTERNAL_SALES", content, referenceDate);
            successCount.incrementAndGet();
        } catch (DuplicateFileException e) {
            duplicateCount.incrementAndGet();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * A partir do M8, {@link ImportFileUseCase#execute} só cria o lote em {@code RECEIVED}
     * e publica a mensagem (via publisher falso, TDS 17.3) — quem de fato processa é o
     * worker. Este helper simula essa segunda etapa de forma síncrona, no mesmo thread, para
     * manter os testes de comportamento de parsing/persistência do M5/M7 verificando o
     * resultado final, sem precisar de um broker real.
     */
    private ImportBatch execute(String sourceCode, String content, LocalDate referenceDate) {
        ImportFileCommand command = new ImportFileCommand(
                sourceCode, "arquivo-" + Uuid7.generate() + ".csv",
                content.getBytes(StandardCharsets.UTF_8), referenceDate, null, null);
        ImportBatch received = importFileUseCase.execute(command, Uuid7.generate(), "analista@fincore.dev");
        processImportBatchUseCase.execute(received.id());
        return importBatchRepository.findById(received.id()).orElseThrow();
    }
}
