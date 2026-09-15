package dev.fincore.configuration.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.fincore.AbstractIntegrationTest;
import dev.fincore.configuration.domain.ToleranceConfig;
import dev.fincore.configuration.infrastructure.ToleranceConfigRepository;
import dev.fincore.shared.identifier.Uuid7;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * {@link UpdateToleranceConfigUseCase} contra PostgreSQL real — "a operação mais sensível
 * do sistema" (Domain §27.2): só {@code ADMINISTRATOR}, {@code If-Match} obrigatório
 * (Implementation Plan M3, critério de aceite 1), auditoria com antes/depois (critério 2).
 *
 * <p>Cada teste cria seu próprio par de fontes e {@code tolerance_config}, em vez de
 * mutar o par semeado por V3 (fonte de verdade de outros testes, como
 * {@code ConfigSnapshotFactoryIntegrationTest}).
 */
class UpdateToleranceConfigUseCaseIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private UpdateToleranceConfigUseCase updateToleranceConfigUseCase;

    @Autowired
    private ToleranceConfigRepository toleranceConfigRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithMockUser(authorities = "ADMINISTRATOR")
    void administradorDeveAtualizarERegistrarAuditoriaComAntesEDepois() throws Exception {
        ToleranceConfig current = createIndependentToleranceConfig();
        UUID actorId = Uuid7.generate();

        ToleranceConfig updated = updateToleranceConfigUseCase.execute(
                current.sourcePairId(), current.version(),
                new UpdateToleranceConfigCommand(50, "USD", 500L),
                actorId, "admin@fincore.dev");

        assertThat(updated.absoluteAmountMinor()).isEqualTo(50);
        assertThat(updated.currency()).isEqualTo("USD");
        assertThat(updated.version()).isEqualTo(current.version() + 1);

        String beforeJson = jdbc.queryForObject(
                "select before_state from audit_event where action = 'TOLERANCE_CONFIG_UPDATED' and entity_id = ? order by occurred_at desc limit 1",
                String.class,
                updated.id());
        String afterJson = jdbc.queryForObject(
                "select after_state from audit_event where action = 'TOLERANCE_CONFIG_UPDATED' and entity_id = ? order by occurred_at desc limit 1",
                String.class,
                updated.id());
        // Comparação por valor parseado, não por substring: jsonb normaliza o texto ao
        // devolver (ex.: espaço após ":"), então bater string crua é frágil.
        JsonNode before = objectMapper.readTree(beforeJson);
        JsonNode after = objectMapper.readTree(afterJson);
        assertThat(before.get("absoluteAmountMinor").asLong()).isEqualTo(current.absoluteAmountMinor());
        assertThat(after.get("absoluteAmountMinor").asLong()).isEqualTo(50);
    }

    @Test
    @WithMockUser(authorities = "ADMINISTRATOR")
    void deveRejeitarComIfMatchDesatualizado() {
        ToleranceConfig current = createIndependentToleranceConfig();
        long staleVersion = current.version() + 999;

        assertThatThrownBy(() -> updateToleranceConfigUseCase.execute(
                        current.sourcePairId(), staleVersion,
                        new UpdateToleranceConfigCommand(1, "BRL", null),
                        Uuid7.generate(), "admin@fincore.dev"))
                .isInstanceOf(StaleConfigurationVersionException.class);
    }

    @Test
    @WithMockUser(authorities = "AUDITOR")
    void auditorNaoDeveConseguirAtualizar() {
        ToleranceConfig current = createIndependentToleranceConfig();

        assertThatThrownBy(() -> updateToleranceConfigUseCase.execute(
                        current.sourcePairId(), current.version(),
                        new UpdateToleranceConfigCommand(1, "BRL", null),
                        Uuid7.generate(), "auditor@fincore.dev"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(authorities = "RECONCILIATION_ANALYST")
    void analistaNaoDeveConseguirAtualizar() {
        ToleranceConfig current = createIndependentToleranceConfig();

        assertThatThrownBy(() -> updateToleranceConfigUseCase.execute(
                        current.sourcePairId(), current.version(),
                        new UpdateToleranceConfigCommand(1, "BRL", null),
                        Uuid7.generate(), "analista@fincore.dev"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void semAutenticacaoNaoDeveConseguirAtualizar() {
        ToleranceConfig current = createIndependentToleranceConfig();

        assertThatThrownBy(() -> updateToleranceConfigUseCase.execute(
                        current.sourcePairId(), current.version(),
                        new UpdateToleranceConfigCommand(1, "BRL", null),
                        Uuid7.generate(), "ninguem@fincore.dev"))
                .isInstanceOf(RuntimeException.class);
    }

    private ToleranceConfig createIndependentToleranceConfig() {
        UUID leftId = UUID.randomUUID();
        UUID rightId = UUID.randomUUID();
        insertSource(leftId, "TEST_LEFT_" + leftId);
        insertSource(rightId, "TEST_RIGHT_" + rightId);
        UUID pairId = UUID.randomUUID();
        jdbc.update(
                "insert into source_pair (id, left_source_id, right_source_id, code) values (?, ?, ?, ?)",
                pairId, leftId, rightId, "TEST_PAIR_" + pairId);

        return toleranceConfigRepository.save(new ToleranceConfig(pairId, 2, "BRL", null, null, Instant.now()));
    }

    private void insertSource(UUID id, String code) {
        jdbc.update(
                """
                insert into source (id, code, name, timezone, decimal_separator, thousands_separator, date_formats, rounding_mode, active)
                values (?, ?, 'Nome', 'UTC', ',', '.', array['dd/MM/yyyy'], 'HALF_UP', true)
                """,
                id, code);
    }
}
