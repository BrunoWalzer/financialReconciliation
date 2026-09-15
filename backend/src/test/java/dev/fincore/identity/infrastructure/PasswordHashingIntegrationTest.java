package dev.fincore.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import dev.fincore.AbstractIntegrationTest;
import dev.fincore.identity.domain.AppUser;
import dev.fincore.identity.domain.UserRole;
import java.time.Instant;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * A senha nunca é armazenada em texto puro (M2 §4). BCrypt custo 12 (TDS 21.1).
 */
class PasswordHashingIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void oHashArmazenadoNuncaEIgualASenhaOriginal() {
        String rawPassword = "senha-em-texto-puro-123";
        AppUser user = appUserRepository.save(new AppUser(
                "hash-check@fincore.dev", passwordEncoder.encode(rawPassword), "Nome",
                EnumSet.of(UserRole.AUDITOR), Instant.now()));

        String storedHash = jdbc.queryForObject(
                "select password_hash from app_user where id = ?", String.class, user.id());

        assertThat(storedHash).isNotEqualTo(rawPassword).doesNotContain(rawPassword);
    }

    @Test
    void oHashArmazenadoDeveTerOFormatoBcryptComCusto12() {
        AppUser user = appUserRepository.save(new AppUser(
                "bcrypt-format@fincore.dev", passwordEncoder.encode("qualquer-senha"), "Nome",
                EnumSet.of(UserRole.AUDITOR), Instant.now()));

        String storedHash = jdbc.queryForObject(
                "select password_hash from app_user where id = ?", String.class, user.id());

        // $2a$12$... — algoritmo, custo (12) e salt+hash, nessa ordem (TDS 21.1: "BCrypt custo 12").
        assertThat(storedHash).matches("^\\$2[aby]\\$12\\$.{53}$");
    }

    @Test
    void deveVerificarSenhaCorretaContraOHashArmazenado() {
        String rawPassword = "senha-correta-para-verificar";
        AppUser user = appUserRepository.save(new AppUser(
                "verify-correct@fincore.dev", passwordEncoder.encode(rawPassword), "Nome",
                EnumSet.of(UserRole.AUDITOR), Instant.now()));

        AppUser reloaded = appUserRepository.findById(user.id()).orElseThrow();

        assertThat(passwordEncoder.matches(rawPassword, reloaded.passwordHash())).isTrue();
    }

    @Test
    void naoDeveVerificarSenhaIncorretaContraOHashArmazenado() {
        AppUser user = appUserRepository.save(new AppUser(
                "verify-incorrect@fincore.dev", passwordEncoder.encode("senha-verdadeira"), "Nome",
                EnumSet.of(UserRole.AUDITOR), Instant.now()));

        AppUser reloaded = appUserRepository.findById(user.id()).orElseThrow();

        assertThat(passwordEncoder.matches("senha-errada", reloaded.passwordHash())).isFalse();
    }
}
