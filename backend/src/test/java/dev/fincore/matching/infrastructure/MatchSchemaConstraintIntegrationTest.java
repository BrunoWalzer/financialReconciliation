package dev.fincore.matching.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.fincore.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * O esquema V7 ao nível do banco (I-5, I-10, I-11, I-14) — SQL real contra PostgreSQL via
 * Testcontainers, nunca simulado em Java. Mesma família de {@code AuditEventConstraintIntegrationTest}
 * (M1): se a migration for enfraquecida, é aqui que a suíte quebra.
 */
class MatchSchemaConstraintIntegrationTest extends AbstractIntegrationTest {

    // UUIDs fixos do seed de referência V3 (ver V3__configuration.sql).
    private static final UUID INTERNAL_SALES_ID = UUID.fromString("00000000-0000-7000-8000-000000000101");

    @Autowired
    private JdbcTemplate jdbc;

    // ------------------------------------------------------------ I-5: match_claim

    @Test
    void i5DeveRejeitarDoisClaimsParaOMesmoRegistro() {
        UUID recordId = insertFinancialRecord();
        UUID matchOne = insertAutomaticMatch();
        UUID matchTwo = insertAutomaticMatch();
        insertClaim(recordId, matchOne);

        assertThatThrownBy(() -> insertClaim(recordId, matchTwo))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("pk_match_claim");
    }

    @Test
    void i5DeveManterOClaimOriginalAposTentativaDeDuplicarRejeitada() {
        UUID recordId = insertFinancialRecord();
        UUID matchOne = insertAutomaticMatch();
        UUID matchTwo = insertAutomaticMatch();
        insertClaim(recordId, matchOne);

        assertThatThrownBy(() -> insertClaim(recordId, matchTwo)).isInstanceOf(DataAccessException.class);

        UUID owner = jdbc.queryForObject(
                "select match_id from match_claim where financial_record_id = ?", UUID.class, recordId);
        assertThat(owner).isEqualTo(matchOne);
    }

    // ------------------------------------------------------------ I-10: match_rejection

    @Test
    void i10DeveRejeitarParForaDaOrdemCanonica() {
        UUID a = insertFinancialRecord();
        UUID b = insertFinancialRecord();
        // A ordem canônica do CHECK do banco é a ordenação nativa do tipo uuid do
        // PostgreSQL (bytewise, sem sinal) — não UUID.compareTo() do Java, que discorda
        // dela para alguns pares (ver dev.fincore.matching.domain.RejectedPair). Descobrir
        // "menor"/"maior" aqui via SQL evita reintroduzir a mesma armadilha no teste.
        UUID smaller = jdbc.queryForObject("select least(?, ?)", UUID.class, a, b);
        UUID larger = jdbc.queryForObject("select greatest(?, ?)", UUID.class, a, b);

        assertThatThrownBy(() -> insertRejection(larger, smaller))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_match_rejection_canonical_order");
    }

    @Test
    void i10DeveRejeitarOMesmoParDuasVezes() {
        UUID a = insertFinancialRecord();
        UUID b = insertFinancialRecord();
        UUID smaller = jdbc.queryForObject("select least(?, ?)", UUID.class, a, b);
        UUID larger = jdbc.queryForObject("select greatest(?, ?)", UUID.class, a, b);
        insertRejection(smaller, larger);

        assertThatThrownBy(() -> insertRejection(smaller, larger))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("uq_match_rejection_pair");
    }

    // ------------------------------------------------------------ I-11: match

    @Test
    void i11DeveRejeitarMatchAutomaticoSemRuleId() {
        assertThatThrownBy(() -> jdbc.update(
                        """
                        insert into match (
                            id, origin, rule_id, rule_version, outcome, status, currency,
                            gross_expected_minor, fee_applied_minor, net_expected_minor, observed_minor,
                            fee_source, residual_minor, tolerance_limit_minor, tolerance_absorbed_minor,
                            evidence, created_at)
                        values (?, 'AUTOMATIC', NULL, NULL, 'RECONCILED_EXACT', 'ACTIVE', 'BRL',
                                1000, 0, 1000, 1000, 'NONE', 0, 200, 0, '{}'::jsonb, now())
                        """,
                        UUID.randomUUID()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_match_automatic_requires_rule_and_evidence");
    }

    @Test
    void i11DeveRejeitarMatchAutomaticoSemEvidencia() {
        assertThatThrownBy(() -> jdbc.update(
                        """
                        insert into match (
                            id, origin, rule_id, rule_version, outcome, status, currency,
                            gross_expected_minor, fee_applied_minor, net_expected_minor, observed_minor,
                            fee_source, residual_minor, tolerance_limit_minor, tolerance_absorbed_minor,
                            evidence, created_at)
                        values (?, 'AUTOMATIC', 'RULE_A_CORRELATION_KEY', 1, 'RECONCILED_EXACT', 'ACTIVE', 'BRL',
                                1000, 0, 1000, 1000, 'NONE', 0, 200, 0, NULL, now())
                        """,
                        UUID.randomUUID()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_match_automatic_requires_rule_and_evidence");
    }

    @Test
    void i11DeveRejeitarMatchManualSemJustificativaSuficiente() {
        assertThatThrownBy(() -> jdbc.update(
                        """
                        insert into match (
                            id, origin, outcome, status, currency,
                            gross_expected_minor, fee_applied_minor, net_expected_minor, observed_minor,
                            fee_source, residual_minor, tolerance_limit_minor, tolerance_absorbed_minor,
                            justification, created_by, created_at)
                        values (?, 'MANUAL', 'RECONCILED_EXACT', 'ACTIVE', 'BRL',
                                1000, 0, 1000, 1000, 'NONE', 0, 200, 0, 'curta', ?, now())
                        """,
                        UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_match_manual_requires_justification");
    }

    @Test
    void i11DeveAceitarMatchAutomaticoValido() {
        UUID id = insertAutomaticMatch();

        Integer count = jdbc.queryForObject("select count(*) from match where id = ?", Integer.class, id);
        assertThat(count).isEqualTo(1);
    }

    // ------------------------------------------------------------ I-14: match_participant

    @Test
    void i14DeveRejeitarDoisPrincipalNoMesmoLado() {
        UUID matchId = insertAutomaticMatch();
        UUID recordOne = insertFinancialRecord();
        UUID recordTwo = insertFinancialRecord();
        insertParticipant(matchId, recordOne, "LEFT", "PRINCIPAL");

        assertThatThrownBy(() -> insertParticipant(matchId, recordTwo, "LEFT", "PRINCIPAL"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("uq_match_participant_principal_per_side");
    }

    @Test
    void i14DevePermitirUmPrincipalPorLadoMaisComponentes() {
        UUID matchId = insertAutomaticMatch();
        UUID leftPrincipal = insertFinancialRecord();
        UUID rightPrincipal = insertFinancialRecord();
        UUID component = insertFinancialRecord();

        insertParticipant(matchId, leftPrincipal, "LEFT", "PRINCIPAL");
        insertParticipant(matchId, rightPrincipal, "RIGHT", "PRINCIPAL");
        insertParticipant(matchId, component, "LEFT", "COMPONENT");

        Integer count = jdbc.queryForObject(
                "select count(*) from match_participant where match_id = ?", Integer.class, matchId);
        assertThat(count).isEqualTo(3);
    }

    // ------------------------------------------------------------ auxiliares

    private UUID insertFinancialRecord() {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                insert into financial_record (
                    id, source_id, import_batch_id, line_number, direction, record_type,
                    gross_amount_minor, currency, business_date, raw_line, fingerprint, created_at)
                values (?, ?, ?, 1, 'CREDIT', 'SALE', 50000, 'BRL', current_date, 'raw', repeat('f', 64), now())
                """,
                id, INTERNAL_SALES_ID, insertImportBatch());
        return id;
    }

    private UUID insertImportBatch() {
        UUID id = UUID.randomUUID();
        String contentHash = (UUID.randomUUID().toString() + UUID.randomUUID().toString()).replace("-", "").substring(0, 64);
        jdbc.update(
                """
                insert into import_batch (
                    id, source_id, original_filename, content_sha256, byte_size, reference_date,
                    status, storage_key, uploaded_by, uploaded_at)
                values (?, ?, 'test.csv', ?, 10, current_date, 'COMPLETED', 'test-key', ?, now())
                """,
                id, INTERNAL_SALES_ID, contentHash, UUID.randomUUID());
        return id;
    }

    private UUID insertAutomaticMatch() {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                insert into match (
                    id, origin, rule_id, rule_version, outcome, status, currency,
                    gross_expected_minor, fee_applied_minor, net_expected_minor, observed_minor,
                    fee_source, residual_minor, tolerance_limit_minor, tolerance_absorbed_minor,
                    evidence, created_at)
                values (?, 'AUTOMATIC', 'RULE_A_CORRELATION_KEY', 1, 'RECONCILED_EXACT', 'ACTIVE', 'BRL',
                        50000, 0, 50000, 50000, 'NONE', 0, 200, 0, '{}'::jsonb, now())
                """,
                id);
        return id;
    }

    private void insertClaim(UUID financialRecordId, UUID matchId) {
        jdbc.update(
                "insert into match_claim (financial_record_id, match_id, claimed_at) values (?, ?, now())",
                financialRecordId, matchId);
    }

    private void insertParticipant(UUID matchId, UUID financialRecordId, String side, String role) {
        jdbc.update(
                "insert into match_participant (match_id, financial_record_id, side, role) values (?, ?, ?, ?)",
                matchId, financialRecordId, side, role);
    }

    private void insertRejection(UUID recordA, UUID recordB) {
        jdbc.update(
                """
                insert into match_rejection (id, record_a_id, record_b_id, rejected_by, rejected_at, reason)
                values (?, ?, ?, ?, now(), 'motivo de teste')
                """,
                UUID.randomUUID(), recordA, recordB, UUID.randomUUID());
    }
}
