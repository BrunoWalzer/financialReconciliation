package dev.fincore.configuration.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.fincore.AbstractIntegrationTest;
import dev.fincore.configuration.domain.SettlementWindow;
import dev.fincore.configuration.infrastructure.SettlementWindowRepository;
import dev.fincore.shared.identifier.Uuid7;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * {@link UpdateSettlementWindowUseCase} contra PostgreSQL real — janelas só nascem por
 * seed (sem POST no M3); este teste cobre a atualização da janela padrão semeada.
 */
class UpdateSettlementWindowUseCaseIntegrationTest extends AbstractIntegrationTest {

    private static final UUID SOURCE_PAIR_ID = UUID.fromString("00000000-0000-7000-8000-000000000103");

    @Autowired
    private UpdateSettlementWindowUseCase updateSettlementWindowUseCase;

    @Autowired
    private ListSettlementWindowsUseCase listSettlementWindowsUseCase;

    @Autowired
    private SettlementWindowRepository settlementWindowRepository;

    @Test
    @WithMockUser(authorities = "ADMINISTRATOR")
    void administradorDeveAtualizarJanelaPadrao() {
        SettlementWindow defaultWindow = findDefaultWindow();

        SettlementWindow updated = updateSettlementWindowUseCase.execute(
                defaultWindow.id(), defaultWindow.version(),
                new UpdateSettlementWindowCommand(2, 5),
                Uuid7.generate(), "admin@fincore.dev");

        assertThat(updated.minDays()).isEqualTo(2);
        assertThat(updated.maxDays()).isEqualTo(5);
        assertThat(updated.version()).isEqualTo(defaultWindow.version() + 1);
    }

    @Test
    @WithMockUser(authorities = "ADMINISTRATOR")
    void deveRejeitarComIfMatchDesatualizado() {
        SettlementWindow defaultWindow = findDefaultWindow();

        assertThatThrownBy(() -> updateSettlementWindowUseCase.execute(
                        defaultWindow.id(), defaultWindow.version() + 999,
                        new UpdateSettlementWindowCommand(2, 5),
                        Uuid7.generate(), "admin@fincore.dev"))
                .isInstanceOf(StaleConfigurationVersionException.class);
    }

    @Test
    @WithMockUser(authorities = "AUDITOR")
    void auditorNaoDeveConseguirAtualizar() {
        SettlementWindow defaultWindow = findDefaultWindow();

        assertThatThrownBy(() -> updateSettlementWindowUseCase.execute(
                        defaultWindow.id(), defaultWindow.version(),
                        new UpdateSettlementWindowCommand(2, 5),
                        Uuid7.generate(), "auditor@fincore.dev"))
                .isInstanceOf(AccessDeniedException.class);
    }

    // Via repositório direto (sem @PreAuthorize): usado também a partir de testes que
    // verificam negativa de autorização, onde o ator do teste não é ADMINISTRATOR.
    private SettlementWindow findDefaultWindow() {
        List<SettlementWindow> windows = settlementWindowRepository.findBySourcePairId(SOURCE_PAIR_ID);
        return windows.stream().filter(w -> w.paymentMethod() == null).findFirst().orElseThrow();
    }
}
