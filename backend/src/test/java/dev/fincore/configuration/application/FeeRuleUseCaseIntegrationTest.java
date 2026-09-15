package dev.fincore.configuration.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.fincore.AbstractIntegrationTest;
import dev.fincore.configuration.domain.FeeRule;
import dev.fincore.configuration.domain.RoundingMode;
import dev.fincore.shared.identifier.Uuid7;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * {@link CreateFeeRuleUseCase} e {@link UpdateFeeRuleUseCase} contra PostgreSQL real — só
 * {@code ADMINISTRATOR}, {@code If-Match} obrigatório na atualização, e a checagem prévia
 * mais a constraint {@code uq_fee_rule_active_source_payment_method} na criação.
 */
class FeeRuleUseCaseIntegrationTest extends AbstractIntegrationTest {

    private static final UUID INTERNAL_SALES_ID = UUID.fromString("00000000-0000-7000-8000-000000000101");

    @Autowired
    private CreateFeeRuleUseCase createFeeRuleUseCase;

    @Autowired
    private UpdateFeeRuleUseCase updateFeeRuleUseCase;

    @Autowired
    private ListFeeRulesUseCase listFeeRulesUseCase;

    @Test
    @WithMockUser(authorities = "ADMINISTRATOR")
    void administradorDeveCriarNovaRegra() {
        FeeRule created = createFeeRuleUseCase.execute(
                new CreateFeeRuleCommand(INTERNAL_SALES_ID, "BOLETO", 150, 10L, RoundingMode.HALF_UP),
                Uuid7.generate(), "admin@fincore.dev");

        assertThat(created.id()).isNotNull();
        assertThat(created.percentageBp()).isEqualTo(150);
        assertThat(created.version()).isZero();

        List<FeeRule> rules = listFeeRulesUseCase.execute(INTERNAL_SALES_ID);
        assertThat(rules).anyMatch(rule -> rule.id().equals(created.id()));
    }

    @Test
    @WithMockUser(authorities = "ADMINISTRATOR")
    void deveRejeitarCriacaoDeSegundaRegraAtivaParaOMesmoMeio() {
        createFeeRuleUseCase.execute(
                new CreateFeeRuleCommand(INTERNAL_SALES_ID, "PIX_DUPLICADO", 100, 0L, RoundingMode.HALF_UP),
                Uuid7.generate(), "admin@fincore.dev");

        assertThatThrownBy(() -> createFeeRuleUseCase.execute(
                        new CreateFeeRuleCommand(INTERNAL_SALES_ID, "PIX_DUPLICADO", 200, 0L, RoundingMode.HALF_UP),
                        Uuid7.generate(), "admin@fincore.dev"))
                .isInstanceOf(FeeRuleAlreadyActiveException.class);
    }

    @Test
    @WithMockUser(authorities = "ADMINISTRATOR")
    void administradorDeveAtualizarRegraExistente() {
        FeeRule created = createFeeRuleUseCase.execute(
                new CreateFeeRuleCommand(INTERNAL_SALES_ID, "TED_ATUALIZAR", 100, 0L, RoundingMode.HALF_UP),
                Uuid7.generate(), "admin@fincore.dev");

        FeeRule updated = updateFeeRuleUseCase.execute(
                created.id(), created.version(),
                new UpdateFeeRuleCommand(300, 50L, RoundingMode.DOWN),
                Uuid7.generate(), "admin@fincore.dev");

        assertThat(updated.percentageBp()).isEqualTo(300);
        assertThat(updated.fixedAmountMinor()).isEqualTo(50L);
        assertThat(updated.roundingMode()).isEqualTo(RoundingMode.DOWN);
        assertThat(updated.version()).isEqualTo(created.version() + 1);
    }

    @Test
    @WithMockUser(authorities = "ADMINISTRATOR")
    void deveRejeitarAtualizacaoComIfMatchDesatualizado() {
        FeeRule created = createFeeRuleUseCase.execute(
                new CreateFeeRuleCommand(INTERNAL_SALES_ID, "DEBITO_STALE", 100, 0L, RoundingMode.HALF_UP),
                Uuid7.generate(), "admin@fincore.dev");

        assertThatThrownBy(() -> updateFeeRuleUseCase.execute(
                        created.id(), created.version() + 999,
                        new UpdateFeeRuleCommand(300, 50L, RoundingMode.DOWN),
                        Uuid7.generate(), "admin@fincore.dev"))
                .isInstanceOf(StaleConfigurationVersionException.class);
    }

    @Test
    @WithMockUser(authorities = "AUDITOR")
    void auditorNaoDeveConseguirCriar() {
        assertThatThrownBy(() -> createFeeRuleUseCase.execute(
                        new CreateFeeRuleCommand(INTERNAL_SALES_ID, "AUDITOR_TENTATIVA", 100, 0L, RoundingMode.HALF_UP),
                        Uuid7.generate(), "auditor@fincore.dev"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(authorities = "RECONCILIATION_ANALYST")
    void analistaNaoDeveConseguirListar() {
        assertThatThrownBy(() -> listFeeRulesUseCase.execute(INTERNAL_SALES_ID))
                .isInstanceOf(AccessDeniedException.class);
    }
}
