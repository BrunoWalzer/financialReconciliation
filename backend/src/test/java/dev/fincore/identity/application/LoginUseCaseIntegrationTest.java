package dev.fincore.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.fincore.AbstractIntegrationTest;
import dev.fincore.identity.domain.AppUser;
import dev.fincore.identity.domain.UserRole;
import dev.fincore.identity.infrastructure.AppUserRepository;
import dev.fincore.identity.infrastructure.JwtTokenService;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * {@link LoginUseCase} contra PostgreSQL real (TDS 16, 21.1, 21.4).
 *
 * <p>{@link #deveAutenticarOAdministradorSemeadoNaMigration()} é o teste que prova que o
 * hash BCrypt gerado por {@code crypt()}/pgcrypto em {@code V2__identity.sql} é
 * compatível com {@link PasswordEncoder} do Spring Security — a justificativa escrita na
 * migration só vale se este teste passar contra banco real, não é uma alegação.
 */
class LoginUseCaseIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private LoginUseCase loginUseCase;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void deveAutenticarOAdministradorSemeadoNaMigration() {
        // Senha do perfil test — ver application-test.yml: bootstrapAdminPassword.
        SessionTokens tokens = loginUseCase.execute(
                new LoginCommand("admin@fincore.dev", "test-only-bootstrap-password"), "junit", "127.0.0.1");

        assertThat(tokens.accessToken()).isNotBlank();
        assertThat(jwtTokenService.verify(tokens.accessToken()).roles()).contains(UserRole.ADMINISTRATOR);
    }

    @Test
    void deveAutenticarUsuarioComCredenciaisCorretas() {
        AppUser user = createUser("ana@fincore.dev", "senha-correta-123", UserRole.RECONCILIATION_ANALYST);

        SessionTokens tokens = loginUseCase.execute(
                new LoginCommand("ana@fincore.dev", "senha-correta-123"), "junit", "127.0.0.1");

        assertThat(jwtTokenService.verify(tokens.accessToken()).userId()).isEqualTo(user.id());
    }

    @Test
    void deveRejeitarSenhaErrada() {
        createUser("ana-senha-errada@fincore.dev", "senha-correta-123", UserRole.RECONCILIATION_ANALYST);

        assertThatThrownBy(() -> loginUseCase.execute(
                        new LoginCommand("ana-senha-errada@fincore.dev", "senha-errada"), "junit", "127.0.0.1"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void deveRejeitarUsuarioInexistente() {
        assertThatThrownBy(() -> loginUseCase.execute(
                        new LoginCommand("ninguem@fincore.dev", "qualquer-senha"), "junit", "127.0.0.1"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void deveRejeitarUsuarioInativo() {
        AppUser user = createUser("inativo@fincore.dev", "senha-123456", UserRole.AUDITOR);
        jdbc.update("update app_user set active = false where id = ?", user.id());

        assertThatThrownBy(() -> loginUseCase.execute(
                        new LoginCommand("inativo@fincore.dev", "senha-123456"), "junit", "127.0.0.1"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void devePersistirIncrementoDeFalhaMesmoComATransacaoDeLoginRevertendoParaOChamador() {
        createUser("ana2@fincore.dev", "senha-correta-123", UserRole.AUDITOR);

        try {
            loginUseCase.execute(new LoginCommand("ana2@fincore.dev", "senha-errada"), "junit", "127.0.0.1");
        } catch (InvalidCredentialsException ignored) {
            // esperado
        }

        Integer failedCount = jdbc.queryForObject(
                "select failed_count from login_throttle where email = ?", Integer.class, "ana2@fincore.dev");
        assertThat(failedCount).isEqualTo(1);
    }

    @Test
    void deveBloquearAposCincoFalhasEPersistirOBloqueio() {
        createUser("ana3@fincore.dev", "senha-correta-123", UserRole.AUDITOR);

        for (int i = 0; i < 5; i++) {
            try {
                loginUseCase.execute(new LoginCommand("ana3@fincore.dev", "senha-errada"), "junit", "127.0.0.1");
            } catch (InvalidCredentialsException ignored) {
                // esperado nas 5 tentativas
            }
        }

        // Mesmo com a senha CORRETA, a conta bloqueada nega — o bloqueio é persistido no
        // banco (login_throttle), não apenas mantido no objeto em memória desta chamada.
        assertThatThrownBy(() -> loginUseCase.execute(
                        new LoginCommand("ana3@fincore.dev", "senha-correta-123"), "junit", "127.0.0.1"))
                .isInstanceOf(InvalidCredentialsException.class);

        Map<String, Object> throttle = jdbc.queryForMap(
                "select failed_count, locked_until from login_throttle where email = ?", "ana3@fincore.dev");
        assertThat((Integer) throttle.get("failed_count")).isGreaterThanOrEqualTo(5);
        assertThat(throttle.get("locked_until")).isNotNull();
    }

    @Test
    void deveZerarBloqueioAposLoginBemSucedido() {
        createUser("ana4@fincore.dev", "senha-correta-123", UserRole.AUDITOR);
        for (int i = 0; i < 3; i++) {
            try {
                loginUseCase.execute(new LoginCommand("ana4@fincore.dev", "senha-errada"), "junit", "127.0.0.1");
            } catch (InvalidCredentialsException ignored) {
                // esperado
            }
        }

        loginUseCase.execute(new LoginCommand("ana4@fincore.dev", "senha-correta-123"), "junit", "127.0.0.1");

        Integer failedCount = jdbc.queryForObject(
                "select failed_count from login_throttle where email = ?", Integer.class, "ana4@fincore.dev");
        assertThat(failedCount).isZero();
    }

    @Test
    void deveIgnorarCampoDePapelSeAlgumForInjetadoNoComando() {
        // LoginCommand só tem email e senha — não há como o cliente influenciar o papel
        // por este caminho; o papel emitido no token vem sempre do que está persistido.
        AppUser user = createUser("ana5@fincore.dev", "senha-correta-123", UserRole.AUDITOR);

        SessionTokens tokens = loginUseCase.execute(
                new LoginCommand("ana5@fincore.dev", "senha-correta-123"), "junit", "127.0.0.1");

        assertThat(jwtTokenService.verify(tokens.accessToken()).roles()).containsExactly(UserRole.AUDITOR);
        assertThat(user.roles()).containsExactly(UserRole.AUDITOR);
    }

    private AppUser createUser(String email, String rawPassword, UserRole role) {
        AppUser user = new AppUser(
                email, passwordEncoder.encode(rawPassword), "Nome de Teste", EnumSet.of(role), Instant.now());
        return appUserRepository.save(user);
    }
}
