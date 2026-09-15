package dev.fincore.configuration.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.fincore.AbstractIntegrationTest;
import dev.fincore.configuration.domain.Source;
import dev.fincore.configuration.domain.ToleranceConfig;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * Autorização dos casos de uso de leitura de {@code configuration} — Implementation Plan
 * M3: "todas ADMINISTRATOR", inclusive as de só-leitura.
 */
class ConfigurationReadUseCasesAuthorizationTest extends AbstractIntegrationTest {

    private static final UUID SOURCE_PAIR_ID = UUID.fromString("00000000-0000-7000-8000-000000000103");

    @Autowired
    private ListSourcesUseCase listSourcesUseCase;

    @Autowired
    private GetToleranceConfigUseCase getToleranceConfigUseCase;

    @Test
    @WithMockUser(authorities = "ADMINISTRATOR")
    void administradorDeveListarFontesSemeadas() {
        List<Source> sources = listSourcesUseCase.execute();

        assertThat(sources).extracting(Source::code).contains("INTERNAL_SALES", "ACQUIRER_SETTLEMENT");
    }

    @Test
    @WithMockUser(authorities = "AUDITOR")
    void auditorNaoDeveConseguirListarFontes() {
        assertThatThrownBy(() -> listSourcesUseCase.execute())
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(authorities = "RECONCILIATION_ANALYST")
    void analistaNaoDeveConseguirListarFontes() {
        assertThatThrownBy(() -> listSourcesUseCase.execute())
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void semAutenticacaoNaoDeveConseguirListarFontes() {
        assertThatThrownBy(() -> listSourcesUseCase.execute())
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @WithMockUser(authorities = "ADMINISTRATOR")
    void administradorDeveLerTolerancia() {
        ToleranceConfig config = getToleranceConfigUseCase.execute(SOURCE_PAIR_ID);

        assertThat(config.sourcePairId()).isEqualTo(SOURCE_PAIR_ID);
    }

    @Test
    @WithMockUser(authorities = "AUDITOR")
    void auditorNaoDeveConseguirLerTolerancia() {
        assertThatThrownBy(() -> getToleranceConfigUseCase.execute(SOURCE_PAIR_ID))
                .isInstanceOf(AccessDeniedException.class);
    }
}
