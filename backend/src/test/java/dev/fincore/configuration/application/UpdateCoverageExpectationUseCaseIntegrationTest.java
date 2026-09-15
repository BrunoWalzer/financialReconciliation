package dev.fincore.configuration.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.fincore.AbstractIntegrationTest;
import dev.fincore.configuration.domain.CoverageExpectation;
import dev.fincore.configuration.domain.CoverageSchedule;
import dev.fincore.configuration.infrastructure.CoverageExpectationRepository;
import dev.fincore.shared.identifier.Uuid7;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * {@link UpdateCoverageExpectationUseCase} contra PostgreSQL real. Cada teste cria sua
 * própria fonte e {@code coverage_expectation}, em vez de mutar a linha semeada por V3
 * para {@code ACQUIRER_SETTLEMENT} — que outros testes (ex.:
 * {@code CoverageExpectationControllerIntegrationTest}) leem esperando os valores
 * originais do seed.
 */
class UpdateCoverageExpectationUseCaseIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private UpdateCoverageExpectationUseCase updateCoverageExpectationUseCase;

    @Autowired
    private CoverageExpectationRepository coverageExpectationRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @WithMockUser(authorities = "ADMINISTRATOR")
    void administradorDeveAtualizarExpectativaDeCobertura() {
        CoverageExpectation current = createIndependentCoverageExpectation();

        CoverageExpectation updated = updateCoverageExpectationUseCase.execute(
                current.id(), current.version(),
                new UpdateCoverageExpectationCommand(CoverageSchedule.DAILY, 3),
                Uuid7.generate(), "admin@fincore.dev");

        assertThat(updated.schedule()).isEqualTo(CoverageSchedule.DAILY);
        assertThat(updated.graceDays()).isEqualTo(3);
        assertThat(updated.version()).isEqualTo(current.version() + 1);
    }

    @Test
    @WithMockUser(authorities = "ADMINISTRATOR")
    void deveRejeitarComIfMatchDesatualizado() {
        CoverageExpectation current = createIndependentCoverageExpectation();

        assertThatThrownBy(() -> updateCoverageExpectationUseCase.execute(
                        current.id(), current.version() + 999,
                        new UpdateCoverageExpectationCommand(CoverageSchedule.DAILY, 3),
                        Uuid7.generate(), "admin@fincore.dev"))
                .isInstanceOf(StaleConfigurationVersionException.class);
    }

    @Test
    @WithMockUser(authorities = "AUDITOR")
    void auditorNaoDeveConseguirAtualizar() {
        CoverageExpectation current = createIndependentCoverageExpectation();

        assertThatThrownBy(() -> updateCoverageExpectationUseCase.execute(
                        current.id(), current.version(),
                        new UpdateCoverageExpectationCommand(CoverageSchedule.DAILY, 3),
                        Uuid7.generate(), "auditor@fincore.dev"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(authorities = "RECONCILIATION_ANALYST")
    void analistaNaoDeveConseguirAtualizar() {
        CoverageExpectation current = createIndependentCoverageExpectation();

        assertThatThrownBy(() -> updateCoverageExpectationUseCase.execute(
                        current.id(), current.version(),
                        new UpdateCoverageExpectationCommand(CoverageSchedule.DAILY, 3),
                        Uuid7.generate(), "analista@fincore.dev"))
                .isInstanceOf(AccessDeniedException.class);
    }

    private CoverageExpectation createIndependentCoverageExpectation() {
        UUID sourceId = UUID.randomUUID();
        jdbc.update(
                """
                insert into source (id, code, name, timezone, decimal_separator, thousands_separator, date_formats, rounding_mode, active)
                values (?, ?, 'Nome', 'UTC', ',', '.', array['dd/MM/yyyy'], 'HALF_UP', true)
                """,
                sourceId, "TEST_SOURCE_" + sourceId);

        return coverageExpectationRepository.save(
                new CoverageExpectation(sourceId, CoverageSchedule.BUSINESS_DAYS, 1));
    }
}
