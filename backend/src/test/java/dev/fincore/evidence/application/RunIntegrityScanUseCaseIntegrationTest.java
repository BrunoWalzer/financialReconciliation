package dev.fincore.evidence.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.fincore.AbstractIntegrationTest;
import dev.fincore.evidence.domain.Direction;
import dev.fincore.evidence.domain.FinancialRecord;
import dev.fincore.evidence.domain.RecordIntegrityFlag;
import dev.fincore.evidence.domain.RecordIntegrityFlagType;
import dev.fincore.evidence.domain.RecordType;
import dev.fincore.evidence.infrastructure.FinancialRecordRepository;
import dev.fincore.evidence.infrastructure.RecordIntegrityFlagRepository;
import dev.fincore.shared.money.Currency;
import dev.fincore.shared.money.Money;
import fincore.testsupport.ImportBatchFixtures;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * {@link RunIntegrityScanUseCase} contra PostgreSQL real — as quatro detecções da varredura
 * intra-fonte (Implementation Plan M7, TDS 9.6), cada uma restrita aos registros novos de um
 * {@code import_batch} e aos grupos de chave que eles tocam.
 */
@WithMockUser(authorities = "RECONCILIATION_ANALYST")
class RunIntegrityScanUseCaseIntegrationTest extends AbstractIntegrationTest {

    private static final UUID INTERNAL_SALES_ID = UUID.fromString("00000000-0000-7000-8000-000000000101");
    private static final UUID ACQUIRER_SETTLEMENT_ID = UUID.fromString("00000000-0000-7000-8000-000000000102");
    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 9, 10);

    @Autowired
    private RunIntegrityScanUseCase runIntegrityScanUseCase;

    @Autowired
    private FinancialRecordRepository financialRecordRepository;

    @Autowired
    private RecordIntegrityFlagRepository flagRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void deveMarcarAmbosOsRegistrosQuandoChaveDeCorrelacaoERepetidaNaFonte() {
        String correlationKey = "NSU-DUP-" + UUID.randomUUID();
        UUID existingBatch = ImportBatchFixtures.insertMinimal(jdbc, ACQUIRER_SETTLEMENT_ID);
        FinancialRecord existing = save(newAcquirerRecord(existingBatch, correlationKey, 500_00, 12_80, 487_20));

        UUID newBatch = ImportBatchFixtures.insertMinimal(jdbc, ACQUIRER_SETTLEMENT_ID);
        FinancialRecord created = save(newAcquirerRecord(newBatch, correlationKey, 700_00, 15_00, 685_00));

        runIntegrityScanUseCase.execute(ACQUIRER_SETTLEMENT_ID, newBatch, Instant.now());

        assertThat(flagTypesOf(existing.id())).containsExactly(RecordIntegrityFlagType.DUPLICATE_CORRELATION_KEY);
        assertThat(flagTypesOf(created.id())).containsExactly(RecordIntegrityFlagType.DUPLICATE_CORRELATION_KEY);
    }

    @Test
    void naoDeveMarcarRegistrosDeGruposNaoTocadosPeloLote() {
        // Domain/TDS 9.6: a varredura é restrita aos grupos que o lote novo toca — um
        // par duplicado pré-existente e alheio ao lote atual não deve ser reexaminado.
        String untouchedKey = "NSU-ALHEIO-" + UUID.randomUUID();
        UUID untouchedBatch = ImportBatchFixtures.insertMinimal(jdbc, ACQUIRER_SETTLEMENT_ID);
        FinancialRecord untouched1 = save(newAcquirerRecord(untouchedBatch, untouchedKey, 100_00, 1_00, 99_00));
        FinancialRecord untouched2 = save(newAcquirerRecord(untouchedBatch, untouchedKey, 200_00, 2_00, 198_00));

        String newKey = "NSU-NOVO-" + UUID.randomUUID();
        UUID newBatch = ImportBatchFixtures.insertMinimal(jdbc, ACQUIRER_SETTLEMENT_ID);
        save(newAcquirerRecord(newBatch, newKey, 300_00, 3_00, 297_00));

        runIntegrityScanUseCase.execute(ACQUIRER_SETTLEMENT_ID, newBatch, Instant.now());

        assertThat(flagTypesOf(untouched1.id())).isEmpty();
        assertThat(flagTypesOf(untouched2.id())).isEmpty();
    }

    @Test
    void deveMarcarFingerprintRepetidoSemExternalIdComoPossibleDuplicate() {
        String fingerprint = "z".repeat(64);
        UUID batchId = ImportBatchFixtures.insertMinimal(jdbc, ACQUIRER_SETTLEMENT_ID);
        FinancialRecord first = save(newAcquirerRecordWithFingerprint(batchId, "NSU-FP1-" + UUID.randomUUID(), fingerprint));
        FinancialRecord second = save(newAcquirerRecordWithFingerprint(batchId, "NSU-FP2-" + UUID.randomUUID(), fingerprint));

        runIntegrityScanUseCase.execute(ACQUIRER_SETTLEMENT_ID, batchId, Instant.now());

        assertThat(flagTypesOf(first.id())).containsExactly(RecordIntegrityFlagType.POSSIBLE_DUPLICATE);
        assertThat(flagTypesOf(second.id())).containsExactly(RecordIntegrityFlagType.POSSIBLE_DUPLICATE);
    }

    @Test
    void naoDeveMarcarPossibleDuplicateQuandoExternalIdEstaPresente() {
        // INTERNAL_SALES sempre tem external_id — fingerprint repetido aqui nunca é
        // POSSIBLE_DUPLICATE (a condição do TDS 9.6 é "sem external_id").
        String fingerprint = "y".repeat(64);
        UUID batchId = ImportBatchFixtures.insertMinimal(jdbc, INTERNAL_SALES_ID);
        FinancialRecord first = save(newInternalSalesRecord(batchId, "PED-FP1-" + UUID.randomUUID(), fingerprint));
        FinancialRecord second = save(newInternalSalesRecord(batchId, "PED-FP2-" + UUID.randomUUID(), fingerprint));

        runIntegrityScanUseCase.execute(INTERNAL_SALES_ID, batchId, Instant.now());

        assertThat(flagTypesOf(first.id())).isEmpty();
        assertThat(flagTypesOf(second.id())).isEmpty();
    }

    @Test
    void deveMarcarInconsistenciaInternaAlemDaTolerancia() {
        // gross(700,00) - fee(15,00) = 685,00, mas o líquido declarado é 683,00 -> diferença de 2 centavos.
        UUID batchId = ImportBatchFixtures.insertMinimal(jdbc, ACQUIRER_SETTLEMENT_ID);
        FinancialRecord inconsistent = save(newAcquirerRecord(batchId, "NSU-INC-" + UUID.randomUUID(), 700_00, 15_00, 683_00));

        runIntegrityScanUseCase.execute(ACQUIRER_SETTLEMENT_ID, batchId, Instant.now());

        assertThat(flagTypesOf(inconsistent.id())).containsExactly(RecordIntegrityFlagType.SOURCE_INTERNAL_INCONSISTENCY);
    }

    @Test
    void naoDeveMarcarInconsistenciaInternaDentroDaTolerancia() {
        // gross(700,00) - fee(15,00) = 685,00; líquido 684,99 -> diferença de 1 centavo, na tolerância.
        UUID batchId = ImportBatchFixtures.insertMinimal(jdbc, ACQUIRER_SETTLEMENT_ID);
        FinancialRecord withinTolerance = save(newAcquirerRecord(batchId, "NSU-TOL-" + UUID.randomUUID(), 700_00, 15_00, 684_99));

        runIntegrityScanUseCase.execute(ACQUIRER_SETTLEMENT_ID, batchId, Instant.now());

        assertThat(flagTypesOf(withinTolerance.id())).isEmpty();
    }

    @Test
    void segundaVarreduraSobreOMesmoLoteNaoCriaFlagDuplicada() {
        String correlationKey = "NSU-IDEMP-" + UUID.randomUUID();
        UUID batchA = ImportBatchFixtures.insertMinimal(jdbc, ACQUIRER_SETTLEMENT_ID);
        save(newAcquirerRecord(batchA, correlationKey, 100_00, 1_00, 99_00));
        UUID batchB = ImportBatchFixtures.insertMinimal(jdbc, ACQUIRER_SETTLEMENT_ID);
        FinancialRecord second = save(newAcquirerRecord(batchB, correlationKey, 200_00, 2_00, 198_00));

        runIntegrityScanUseCase.execute(ACQUIRER_SETTLEMENT_ID, batchB, Instant.now());
        runIntegrityScanUseCase.execute(ACQUIRER_SETTLEMENT_ID, batchB, Instant.now());

        Long flagCount = jdbc.queryForObject(
                "select count(*) from record_integrity_flag where financial_record_id = ?", Long.class, second.id());
        assertThat(flagCount).isEqualTo(1);
    }

    @Test
    void naoDeveAlterarOFinancialRecordAoMarcar() {
        UUID batchA = ImportBatchFixtures.insertMinimal(jdbc, ACQUIRER_SETTLEMENT_ID);
        String correlationKey = "NSU-IMMUT-" + UUID.randomUUID();
        FinancialRecord original = save(newAcquirerRecord(batchA, correlationKey, 100_00, 1_00, 99_00));
        UUID batchB = ImportBatchFixtures.insertMinimal(jdbc, ACQUIRER_SETTLEMENT_ID);
        save(newAcquirerRecord(batchB, correlationKey, 200_00, 2_00, 198_00));

        runIntegrityScanUseCase.execute(ACQUIRER_SETTLEMENT_ID, batchB, Instant.now());

        FinancialRecord reread = financialRecordRepository.findById(original.id()).orElseThrow();
        assertThat(reread.grossAmount()).isEqualTo(original.grossAmount());
        assertThat(reread.correlationKey()).isEqualTo(original.correlationKey());
        assertThat(reread.fingerprint()).isEqualTo(original.fingerprint());
    }

    @Test
    void findExternalIdDuplicatesNuncaEncontraNadaSobOUnicoParcialDeExternalId() {
        // uq_financial_record_source_external_id (V4) já impede duas linhas com o mesmo
        // (source_id, external_id) — a detecção existe por simetria e defesa em profundidade,
        // mas é estruturalmente inalcançável sob o schema atual (ver Javadoc de
        // IntegrityScanQueries e o relatório do M7, Desvios).
        UUID batchId = ImportBatchFixtures.insertMinimal(jdbc, INTERNAL_SALES_ID);
        FinancialRecord record = save(newInternalSalesRecord(batchId, "PED-UNICO-" + UUID.randomUUID(), "w".repeat(64)));

        runIntegrityScanUseCase.execute(INTERNAL_SALES_ID, batchId, Instant.now());

        assertThat(flagTypesOf(record.id())).isEmpty();
    }

    private List<RecordIntegrityFlagType> flagTypesOf(UUID financialRecordId) {
        return flagRepository.findByFinancialRecordIdOrderByDetectedAtAsc(financialRecordId).stream()
                .map(RecordIntegrityFlag::flagType)
                .toList();
    }

    private FinancialRecord save(FinancialRecord record) {
        return financialRecordRepository.save(record);
    }

    private static FinancialRecord newAcquirerRecord(
            UUID importBatchId, String correlationKey, long grossMinor, long feeMinor, long netMinor) {
        // Cada chamada precisa de uma impressão digital PRÓPRIA — do contrário, dois
        // registros deste helper colidiriam por fingerprint e disparariam POSSIBLE_DUPLICATE
        // mesmo quando o teste não tem nenhuma intenção de testar aquela detecção.
        return new FinancialRecord(
                ACQUIRER_SETTLEMENT_ID, importBatchId, 1, null, correlationKey, Direction.CREDIT, RecordType.SETTLEMENT,
                new Money(grossMinor, Currency.BRL), new Money(feeMinor, Currency.BRL), new Money(netMinor, Currency.BRL),
                BUSINESS_DATE, null, null, "CREDIT_CARD", "VISA", "VISA",
                "linha", randomFingerprint(), Instant.now());
    }

    private static String randomFingerprint() {
        return UUID.randomUUID().toString().replace("-", "").repeat(2).substring(0, 64);
    }

    private static FinancialRecord newAcquirerRecordWithFingerprint(UUID importBatchId, String correlationKey, String fingerprint) {
        return new FinancialRecord(
                ACQUIRER_SETTLEMENT_ID, importBatchId, 1, null, correlationKey, Direction.CREDIT, RecordType.SETTLEMENT,
                new Money(500_00, Currency.BRL), new Money(12_80, Currency.BRL), new Money(487_20, Currency.BRL),
                BUSINESS_DATE, null, null, "CREDIT_CARD", "VISA", "VISA",
                "linha", fingerprint, Instant.now());
    }

    private static FinancialRecord newInternalSalesRecord(UUID importBatchId, String externalId, String fingerprint) {
        return new FinancialRecord(
                INTERNAL_SALES_ID, importBatchId, 1, externalId, null, Direction.CREDIT, RecordType.SALE,
                new Money(500_00, Currency.BRL), null, null,
                BUSINESS_DATE, null, null, "CREDIT_CARD", "descrição", "DESCRICAO",
                "linha", fingerprint, Instant.now());
    }
}
