package dev.fincore.ingestion.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.fincore.AbstractIntegrationTest;
import dev.fincore.evidence.domain.FinancialRecord;
import dev.fincore.evidence.infrastructure.FinancialRecordRepository;
import dev.fincore.ingestion.domain.ImportBatch;
import dev.fincore.ingestion.domain.ImportStatus;
import dev.fincore.ingestion.infrastructure.ImportBatchRepository;
import dev.fincore.shared.identifier.Uuid7;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * A varredura de integridade intra-fonte (Implementation Plan M7, TDS 9.6) disparada de
 * ponta a ponta pelo pipeline síncrono — {@link ImportFileUseCase} chamando
 * {@code ImportBatchTransactionalSteps.runIntegrityScan} ao fim de uma importação
 * bem-sucedida, nunca dentro de M5/M6 (nada aqui reabre ou reimplementa aquele pipeline).
 */
class ImportFileUseCaseIntegrityScanIntegrationTest extends AbstractIntegrationTest {

    private static final UUID ACQUIRER_SETTLEMENT_ID = UUID.fromString("00000000-0000-7000-8000-000000000102");
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
    private JdbcTemplate jdbc;

    @Test
    @WithMockUser(authorities = "RECONCILIATION_ANALYST")
    void chaveDeCorrelacaoRepetidaEntreDuasImportacoesMarcaAmbosOsRegistros() {
        String nsu = "NSU-SCAN-" + UUID.randomUUID();
        String firstFile = ACQUIRER_HEADER + "\n" + nsu + ";10/09/2026;500,00;10,00;490,00;VISA;CREDITO_A_VISTA;1/1\n";
        execute(firstFile, LocalDate.of(2026, 9, 10));

        String secondFile = ACQUIRER_HEADER + "\n" + nsu + ";11/09/2026;300,00;5,00;295,00;MASTER;CREDITO_A_VISTA;1/1\n";
        execute(secondFile, LocalDate.of(2026, 9, 11));

        List<UUID> flaggedRecordIds = jdbc.queryForList(
                """
                select fr.id from financial_record fr
                join record_integrity_flag f on f.financial_record_id = fr.id
                where fr.source_id = ? and fr.correlation_key = ? and f.flag_type = 'DUPLICATE_CORRELATION_KEY'
                """,
                UUID.class, ACQUIRER_SETTLEMENT_ID, nsu);
        assertThat(flaggedRecordIds).hasSize(2);
    }

    @Test
    @WithMockUser(authorities = "RECONCILIATION_ANALYST")
    void brutoMenosTaxaDiferenteDoLiquidoAlemDaToleranciaMarcaInconsistenciaInterna() {
        String nsu = "NSU-INC-" + UUID.randomUUID();
        // bruto 500,00 - taxa 10,00 = 490,00, mas o líquido declarado é 480,00 -> 10 reais de diferença.
        String content = ACQUIRER_HEADER + "\n" + nsu + ";10/09/2026;500,00;10,00;480,00;VISA;CREDITO_A_VISTA;1/1\n";

        execute(content, LocalDate.of(2026, 9, 10));

        FinancialRecord record = financialRecordRepository.findAll(
                        (root, query, cb) -> cb.equal(root.get("correlationKey"), nsu),
                        org.springframework.data.domain.PageRequest.of(0, 10))
                .getContent().get(0);

        Integer flagCount = jdbc.queryForObject(
                "select count(*) from record_integrity_flag where financial_record_id = ? and flag_type = 'SOURCE_INTERNAL_INCONSISTENCY'",
                Integer.class, record.id());
        assertThat(flagCount).isEqualTo(1);
    }

    @Test
    @WithMockUser(authorities = "RECONCILIATION_ANALYST")
    void duasLinhasIdenticasSemExternalIdNaMesmaImportacaoMarcamPossibleDuplicateEmAmbas() {
        String nsu = "NSU-PD-" + UUID.randomUUID();
        String line = nsu + ";10/09/2026;500,00;10,00;490,00;VISA;CREDITO_A_VISTA;1/1";
        String content = ACQUIRER_HEADER + "\n" + line + "\n" + line + "\n";

        execute(content, LocalDate.of(2026, 9, 10));

        Integer flaggedCount = jdbc.queryForObject(
                """
                select count(*) from financial_record fr
                join record_integrity_flag f on f.financial_record_id = fr.id
                where fr.source_id = ? and fr.correlation_key = ? and f.flag_type = 'POSSIBLE_DUPLICATE'
                """,
                Integer.class, ACQUIRER_SETTLEMENT_ID, nsu);
        assertThat(flaggedCount).isEqualTo(2);
    }

    @Test
    @WithMockUser(authorities = "RECONCILIATION_ANALYST")
    void loteRejeitadoPorTodasAsLinhasInvalidasNaoDisparaVarredura() {
        String content = ACQUIRER_HEADER + "\n" + "NSU-X;10/09/2026;0,00;10,00;490,00;VISA;CREDITO_A_VISTA;1/1\n";

        ImportBatch batch = execute(content, LocalDate.of(2026, 9, 12));

        assertThat(batch.status()).isEqualTo(ImportStatus.REJECTED);
        Integer flagCount = jdbc.queryForObject(
                "select count(*) from record_integrity_flag where detected_by_batch_id = ?", Integer.class, batch.id());
        assertThat(flagCount).isZero();
    }

    private ImportBatch execute(String content, LocalDate referenceDate) {
        ImportFileCommand command = new ImportFileCommand(
                "ACQUIRER_SETTLEMENT", "arquivo-" + Uuid7.generate() + ".csv",
                content.getBytes(StandardCharsets.UTF_8), referenceDate, null, null);
        ImportBatch received = importFileUseCase.execute(command, Uuid7.generate(), "analista@fincore.dev");
        processImportBatchUseCase.execute(received.id());
        return importBatchRepository.findById(received.id()).orElseThrow();
    }
}
