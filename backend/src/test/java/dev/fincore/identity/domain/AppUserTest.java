package dev.fincore.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** {@link AppUser} — e-mail e papéis, sem depender de banco (TDS 7.1, Domain §4). */
class AppUserTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void deveCriarComIdentificadorGerado() {
        AppUser user = new AppUser(
                "ana@fincore.dev", "hash", "Ana", EnumSet.of(UserRole.RECONCILIATION_ANALYST), NOW);

        assertThat(user.id()).isNotNull();
        assertThat(user.id().version()).isEqualTo(7);
        assertThat(user.active()).isTrue();
    }

    @Test
    void deveRejeitarConjuntoDePapeisVazio() {
        assertThatThrownBy(() -> new AppUser("ana@fincore.dev", "hash", "Ana", Set.of(), NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("roles");
    }

    @Test
    void deveRejeitarEmailEmBranco() {
        assertThatThrownBy(() -> new AppUser(
                        "  ", "hash", "Ana", EnumSet.of(UserRole.AUDITOR), NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("email");
    }

    @Test
    void devePermitirAcumuloDePapeis() {
        AppUser user = new AppUser(
                "ana@fincore.dev", "hash", "Ana", EnumSet.of(UserRole.ADMINISTRATOR), NOW);

        user.grantRole(UserRole.AUDITOR, NOW);

        assertThat(user.roles()).containsExactlyInAnyOrder(UserRole.ADMINISTRATOR, UserRole.AUDITOR);
    }

    @Test
    void deveRevogarUmPapelQuandoHaOutro() {
        AppUser user = new AppUser(
                "ana@fincore.dev", "hash", "Ana", EnumSet.of(UserRole.ADMINISTRATOR, UserRole.AUDITOR), NOW);

        user.revokeRole(UserRole.AUDITOR, NOW);

        assertThat(user.roles()).containsExactly(UserRole.ADMINISTRATOR);
    }

    @Test
    void deveRejeitarRevogarOUltimoPapel() {
        AppUser user = new AppUser(
                "ana@fincore.dev", "hash", "Ana", EnumSet.of(UserRole.ADMINISTRATOR), NOW);

        assertThatThrownBy(() -> user.revokeRole(UserRole.ADMINISTRATOR, NOW))
                .isInstanceOf(IllegalStateException.class);
        assertThat(user.roles()).containsExactly(UserRole.ADMINISTRATOR);
    }
}
