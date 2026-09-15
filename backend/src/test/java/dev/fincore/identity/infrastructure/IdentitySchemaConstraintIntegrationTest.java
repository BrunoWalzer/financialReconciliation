package dev.fincore.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.fincore.AbstractIntegrationTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * {@code app_user}, {@code user_role}, {@code refresh_token}, {@code login_throttle} ao
 * nível do banco — constraints e o seed do administrador (TDS 7.1, 29.4). SQL real via
 * Testcontainers, como toda constraint desde o M1.
 */
class IdentitySchemaConstraintIntegrationTest extends AbstractIntegrationTest {

    private static final UUID SEEDED_ADMIN_ID = UUID.fromString("00000000-0000-7000-8000-000000000001");

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void deveTerSemeadoOAdministradorInicial() {
        Integer count = jdbc.queryForObject(
                "select count(*) from app_user where id = ?", Integer.class, SEEDED_ADMIN_ID);
        assertThat(count).isEqualTo(1);

        List<String> roles = jdbc.queryForList(
                "select role from user_role where user_id = ?", String.class, SEEDED_ADMIN_ID);
        assertThat(roles).containsExactly("ADMINISTRATOR");
    }

    @Test
    void deveRejeitarEmailDuplicado() {
        insertUser(UUID.randomUUID(), "duplicado@fincore.dev");

        assertThatThrownBy(() -> insertUser(UUID.randomUUID(), "duplicado@fincore.dev"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("uq_app_user_email");
    }

    @Test
    void deveRejeitarPapelForaDoConjuntoPermitido() {
        UUID userId = UUID.randomUUID();
        insertUser(userId, "papel-invalido@fincore.dev");

        assertThatThrownBy(() -> jdbc.update("insert into user_role (user_id, role) values (?, 'SUPER_ADMIN')", userId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_user_role_role");
    }

    @Test
    void deveRejeitarUserRoleSemUsuarioExistente() {
        assertThatThrownBy(() -> jdbc.update(
                        "insert into user_role (user_id, role) values (?, 'AUDITOR')", UUID.randomUUID()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("fk_user_role_app_user");
    }

    @Test
    void deveRejeitarTokenHashDuplicado() {
        UUID userId = UUID.randomUUID();
        insertUser(userId, "token-duplicado@fincore.dev");
        insertRefreshToken(UUID.randomUUID(), userId, "hash-repetido");

        assertThatThrownBy(() -> insertRefreshToken(UUID.randomUUID(), userId, "hash-repetido"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("uq_refresh_token_token_hash");
    }

    @Test
    void deveRejeitarDoisTokensSubstituidosPeloMesmoSucessor() {
        UUID userId = UUID.randomUUID();
        insertUser(userId, "familia-ramificada@fincore.dev");
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID successor = UUID.randomUUID();
        insertRefreshToken(first, userId, "hash-1");
        insertRefreshToken(second, userId, "hash-2");
        insertRefreshToken(successor, userId, "hash-3");

        jdbc.update("update refresh_token set revoked_at = now(), replaced_by_id = ? where id = ?", successor, first);

        assertThatThrownBy(() -> jdbc.update(
                        "update refresh_token set revoked_at = now(), replaced_by_id = ? where id = ?", successor, second))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("uq_refresh_token_replaced_by_id");
    }

    @Test
    void deveRejeitarRefreshTokenSemUsuarioExistente() {
        assertThatThrownBy(() -> insertRefreshToken(UUID.randomUUID(), UUID.randomUUID(), "hash-orfa"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("fk_refresh_token_app_user");
    }

    @Test
    void deveInserirELerLoginThrottle() {
        jdbc.update(
                "insert into login_throttle (email, failed_count, first_failed_at, locked_until) values (?, 1, now(), null)",
                "throttle@fincore.dev");

        Integer failedCount = jdbc.queryForObject(
                "select failed_count from login_throttle where email = ?", Integer.class, "throttle@fincore.dev");
        assertThat(failedCount).isEqualTo(1);
    }

    private void insertUser(UUID id, String email) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update(
                """
                insert into app_user (id, email, password_hash, display_name, active, created_at, updated_at, version)
                values (?, ?, 'hash', 'Nome', true, ?, ?, 0)
                """,
                id, email, now, now);
    }

    private void insertRefreshToken(UUID id, UUID userId, String tokenHash) {
        Instant now = Instant.now();
        jdbc.update(
                """
                insert into refresh_token (id, user_id, token_hash, issued_at, expires_at)
                values (?, ?, ?, ?, ?)
                """,
                id, userId, tokenHash, Timestamp.from(now), Timestamp.from(now.plusSeconds(604800)));
    }
}
