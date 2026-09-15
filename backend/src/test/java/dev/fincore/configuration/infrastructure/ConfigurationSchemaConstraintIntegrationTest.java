package dev.fincore.configuration.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.fincore.AbstractIntegrationTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * {@code source}, {@code source_pair}, {@code tolerance_config}, {@code fee_rule},
 * {@code settlement_window}, {@code coverage_expectation} ao nível do banco — constraints
 * e o seed de referência (V3, TDS 7.3). SQL real via Testcontainers.
 */
class ConfigurationSchemaConstraintIntegrationTest extends AbstractIntegrationTest {

    // UUIDs fixos do seed de referência V3.
    private static final UUID INTERNAL_SALES_ID = UUID.fromString("00000000-0000-7000-8000-000000000101");
    private static final UUID ACQUIRER_SETTLEMENT_ID = UUID.fromString("00000000-0000-7000-8000-000000000102");
    private static final UUID SOURCE_PAIR_ID = UUID.fromString("00000000-0000-7000-8000-000000000103");

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void deveTerSemeadoAsDuasFontesDeReferencia() {
        // containsAll, não containsExactly: outros testes desta classe criam fontes
        // independentes via createIndependentSourcePair() para isolar suas próprias constraints.
        List<String> codes = jdbc.queryForList("select code from source", String.class);
        assertThat(codes).contains("ACQUIRER_SETTLEMENT", "INTERNAL_SALES");
    }

    @Test
    void deveTerSemeadoOParDeFontesATolerancia() {
        Integer pairCount = jdbc.queryForObject(
                "select count(*) from source_pair where id = ?", Integer.class, SOURCE_PAIR_ID);
        assertThat(pairCount).isEqualTo(1);

        Integer toleranceCount = jdbc.queryForObject(
                "select count(*) from tolerance_config where source_pair_id = ?", Integer.class, SOURCE_PAIR_ID);
        assertThat(toleranceCount).isEqualTo(1);
    }

    @Test
    void deveTerSemeadoDuasJanelasDeLiquidacaoEUmaExpectativaDeCobertura() {
        Integer windowCount = jdbc.queryForObject(
                "select count(*) from settlement_window where source_pair_id = ?", Integer.class, SOURCE_PAIR_ID);
        assertThat(windowCount).isEqualTo(2);

        Integer coverageCount = jdbc.queryForObject(
                "select count(*) from coverage_expectation where source_id = ?", Integer.class, ACQUIRER_SETTLEMENT_ID);
        assertThat(coverageCount).isEqualTo(1);
    }

    @Test
    void deveRejeitarCodigoDeFonteDuplicado() {
        assertThatThrownBy(() -> insertSource(UUID.randomUUID(), "INTERNAL_SALES"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("uq_source_code");
    }

    @Test
    void deveRejeitarRoundingModeForaDoConjuntoPermitido() {
        assertThatThrownBy(() -> jdbc.update(
                        """
                        insert into source (id, code, name, timezone, decimal_separator, thousands_separator, date_formats, rounding_mode, active)
                        values (?, 'INVALIDO', 'Nome', 'UTC', ',', '.', array['dd/MM/yyyy'], 'CEIL', true)
                        """,
                        UUID.randomUUID()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_source_rounding_mode");
    }

    @Test
    void deveRejeitarSourcePairComLadosIguais() {
        assertThatThrownBy(() -> jdbc.update(
                        "insert into source_pair (id, left_source_id, right_source_id, code) values (?, ?, ?, 'MESMO_LADO')",
                        UUID.randomUUID(), INTERNAL_SALES_ID, INTERNAL_SALES_ID))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_source_pair_distinct_sides");
    }

    @Test
    void deveRejeitarSourcePairComParDeLadosDuplicado() {
        assertThatThrownBy(() -> jdbc.update(
                        "insert into source_pair (id, left_source_id, right_source_id, code) values (?, ?, ?, 'OUTRO_CODIGO')",
                        UUID.randomUUID(), INTERNAL_SALES_ID, ACQUIRER_SETTLEMENT_ID))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("uq_source_pair_left_right");
    }

    @Test
    void deveRejeitarToleranceConfigComValorNegativo() {
        UUID otherPairId = createIndependentSourcePair();

        assertThatThrownBy(() -> jdbc.update(
                        """
                        insert into tolerance_config (id, source_pair_id, absolute_amount_minor, currency, updated_at, version)
                        values (?, ?, -1, 'BRL', now(), 0)
                        """,
                        UUID.randomUUID(), otherPairId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_tolerance_config_amount_non_negative");
    }

    @Test
    void deveRejeitarSegundaToleranceConfigParaOMesmoPar() {
        assertThatThrownBy(() -> jdbc.update(
                        """
                        insert into tolerance_config (id, source_pair_id, absolute_amount_minor, currency, updated_at, version)
                        values (?, ?, 5, 'BRL', now(), 0)
                        """,
                        UUID.randomUUID(), SOURCE_PAIR_ID))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("uq_tolerance_config_source_pair");
    }

    @Test
    void deveRejeitarFeeRuleComPercentageBpForaDoIntervalo() {
        assertThatThrownBy(() -> insertFeeRule(UUID.randomUUID(), INTERNAL_SALES_ID, "BOLETO", 10_001, true))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_fee_rule_percentage_bp_range");
    }

    @Test
    void deveRejeitarDuasRegrasDeTaxaAtivasParaOMesmoMeioDePagamento() {
        insertFeeRule(UUID.randomUUID(), INTERNAL_SALES_ID, "PIX", 100, true);

        assertThatThrownBy(() -> insertFeeRule(UUID.randomUUID(), INTERNAL_SALES_ID, "PIX", 200, true))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("uq_fee_rule_active_source_payment_method");
    }

    @Test
    void devePermitirDuasRegrasDeTaxaComPaymentMethodNuloSeUmaEstiverInativa() {
        // Fonte própria: evita colidir com deveRejeitarDuasRegrasDeTaxaAtivasComPaymentMethodNulo,
        // que também usa payment_method nulo — ambas mexeriam na mesma linha se compartilhassem a fonte.
        UUID sourceId = createIndependentSource();
        insertFeeRule(UUID.randomUUID(), sourceId, null, 100, false);

        // Como a primeira está inativa, uma segunda ativa com o mesmo (source, meio nulo) é permitida.
        insertFeeRule(UUID.randomUUID(), sourceId, null, 200, true);

        Integer activeCount = jdbc.queryForObject(
                "select count(*) from fee_rule where source_id = ? and payment_method is null and active",
                Integer.class,
                sourceId);
        assertThat(activeCount).isEqualTo(1);
    }

    @Test
    void deveRejeitarDuasRegrasDeTaxaAtivasComPaymentMethodNulo() {
        UUID sourceId = createIndependentSource();
        insertFeeRule(UUID.randomUUID(), sourceId, null, 100, true);

        // NULL não deve ser tratado como "distinto de si mesmo": este é o caso que a
        // expressão COALESCE(payment_method, '') existe para cobrir (ver V3__configuration.sql).
        assertThatThrownBy(() -> insertFeeRule(UUID.randomUUID(), sourceId, null, 200, true))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("uq_fee_rule_active_source_payment_method");
    }

    @Test
    void deveRejeitarSettlementWindowComMaxDaysMenorQueMinDays() {
        UUID otherPairId = createIndependentSourcePair();

        assertThatThrownBy(() -> insertSettlementWindow(UUID.randomUUID(), otherPairId, null, 5, 3))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_settlement_window_days");
    }

    @Test
    void deveRejeitarDuasJanelasComOMesmoParEPaymentMethodNulo() {
        // O par SOURCE_PAIR_ID já tem uma janela com payment_method nulo (seed V3).
        assertThatThrownBy(() -> insertSettlementWindow(UUID.randomUUID(), SOURCE_PAIR_ID, null, 1, 5))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("uq_settlement_window_pair_payment_method");
    }

    @Test
    void deveRejeitarSegundaCoverageExpectationParaAMesmaFonte() {
        assertThatThrownBy(() -> jdbc.update(
                        "insert into coverage_expectation (id, source_id, schedule, grace_days, active, version) values (?, ?, 'DAILY', 0, true, 0)",
                        UUID.randomUUID(), ACQUIRER_SETTLEMENT_ID))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("uq_coverage_expectation_source");
    }

    @Test
    void deveRejeitarCoverageExpectationComScheduleForaDoConjuntoPermitido() {
        assertThatThrownBy(() -> jdbc.update(
                        "insert into coverage_expectation (id, source_id, schedule, grace_days, active, version) values (?, ?, 'MONTHLY', 0, true, 0)",
                        UUID.randomUUID(), INTERNAL_SALES_ID))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_coverage_expectation_schedule");
    }

    private UUID createIndependentSource() {
        UUID id = UUID.randomUUID();
        insertSource(id, "TEST_SOURCE_" + id);
        return id;
    }

    private UUID createIndependentSourcePair() {
        UUID leftId = UUID.randomUUID();
        UUID rightId = UUID.randomUUID();
        insertSource(leftId, "TEST_LEFT_" + leftId);
        insertSource(rightId, "TEST_RIGHT_" + rightId);
        UUID pairId = UUID.randomUUID();
        jdbc.update(
                "insert into source_pair (id, left_source_id, right_source_id, code) values (?, ?, ?, ?)",
                pairId, leftId, rightId, "TEST_PAIR_" + pairId);
        return pairId;
    }

    private void insertSource(UUID id, String code) {
        jdbc.update(
                """
                insert into source (id, code, name, timezone, decimal_separator, thousands_separator, date_formats, rounding_mode, active)
                values (?, ?, 'Nome', 'UTC', ',', '.', array['dd/MM/yyyy'], 'HALF_UP', true)
                """,
                id, code);
    }

    private void insertFeeRule(UUID id, UUID sourceId, String paymentMethod, int percentageBp, boolean active) {
        jdbc.update(
                "insert into fee_rule (id, source_id, payment_method, percentage_bp, fixed_amount_minor, rounding_mode, active, version) "
                        + "values (?, ?, ?, ?, 0, 'HALF_UP', ?, 0)",
                id, sourceId, paymentMethod, percentageBp, active);
    }

    private void insertSettlementWindow(UUID id, UUID sourcePairId, String paymentMethod, int minDays, int maxDays) {
        jdbc.update(
                "insert into settlement_window (id, source_pair_id, payment_method, min_days, max_days, version) values (?, ?, ?, ?, ?, 0)",
                id, sourcePairId, paymentMethod, minDays, maxDays);
    }
}
