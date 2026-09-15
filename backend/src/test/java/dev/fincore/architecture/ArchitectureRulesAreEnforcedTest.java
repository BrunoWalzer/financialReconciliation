package dev.fincore.architecture;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Prova que as regras reprovam o que deveriam reprovar.
 *
 * <p>No M0 quase todo pacote-alvo esta vazio, e uma regra que passa sobre o vazio e
 * indistinguivel de uma regra escrita errado. Cada teste aqui aplica uma regra real a uma
 * classe que a viola de proposito e exige a falha.
 */
@Tag("architecture")
class ArchitectureRulesAreEnforcedTest {

    private static JavaClasses deliberateViolations;

    @BeforeAll
    static void importViolations() {
        deliberateViolations = new ClassFileImporter().importPackages("dev.fincore.architecture.violation");
    }

    @Test
    void deveDetectarViolacaoQuandoUmCampoDeDominioEDouble() {
        assertThatThrownBy(() -> ArchitectureRules.DOMAIN_HAS_NO_FLOATING_POINT_FIELDS.check(deliberateViolations))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("SettlementWithDoubleAmount");
    }

    @Test
    void deveDetectarViolacaoQuandoMatchingLeORelogio() {
        assertThatThrownBy(() -> ArchitectureRules.MATCHING_DOES_NOT_READ_THE_CLOCK.check(deliberateViolations))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("ClockDependentRule");
    }

    @Test
    void deveDetectarViolacaoQuandoMatchingReferenciaTipoDeScore() {
        assertThatThrownBy(() -> ArchitectureRules.MATCHING_DOES_NOT_REFERENCE_SCORE_TYPES.check(deliberateViolations))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("ScoreDrivenRule");
    }

    @Test
    void deveDetectarViolacaoQuandoControllerDependeDeRepositorio() {
        assertThatThrownBy(() -> ArchitectureRules.CONTROLLERS_DO_NOT_DEPEND_ON_REPOSITORIES.check(deliberateViolations))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("LeakyController");
    }

    @Test
    void deveAprovarQuandoARegraNaoTemAlvoNoConjuntoAnalisado() {
        // As fixtures nao violam esta regra. Serve de controle: os testes acima falham por
        // violacao real, nao porque qualquer check sobre este conjunto lancaria.
        ArchRule ruleWithoutTargetHere = ArchitectureRules.MATCHING_DOES_NOT_DEPEND_ON_DIVERGENCE;
        assertThatCode(() -> ruleWithoutTargetHere.check(deliberateViolations)).doesNotThrowAnyException();
    }
}
