package dev.fincore.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import dev.fincore.AbstractIntegrationTest;
import dev.fincore.identity.domain.AppUser;
import dev.fincore.identity.domain.RefreshToken;
import dev.fincore.identity.domain.UserRole;
import dev.fincore.identity.infrastructure.AppUserRepository;
import dev.fincore.identity.infrastructure.JwtProperties;
import dev.fincore.identity.infrastructure.RefreshTokenRepository;
import java.time.Instant;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * {@link LogoutUseCase} contra PostgreSQL real (TDS 21.1: "logout revoga; token revogado
 * não renova").
 */
class LogoutUseCaseIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private LogoutUseCase logoutUseCase;

    @Autowired
    private RefreshSessionUseCase refreshSessionUseCase;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtProperties jwtProperties;

    @Test
    void deveRevogarOTokenApresentado() {
        AppUser user = createUser("logout1@fincore.dev");
        RefreshToken.Issued issued = issueToken(user);

        logoutUseCase.execute(issued.rawSecret());

        RefreshToken token = refreshTokenRepository.findById(issued.token().id()).orElseThrow();
        assertThat(token.isRevoked()).isTrue();
    }

    @Test
    void oTokenRevogadoPeloLogoutNaoDeveRenovar() {
        AppUser user = createUser("logout2@fincore.dev");
        RefreshToken.Issued issued = issueToken(user);

        logoutUseCase.execute(issued.rawSecret());

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> refreshSessionUseCase.execute(issued.rawSecret(), "junit", "127.0.0.1"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void deveSerSilenciosoQuandoNaoHaTokenParaRevogar() {
        assertThatCode(() -> logoutUseCase.execute("token-que-nunca-existiu")).doesNotThrowAnyException();
    }

    @Test
    void deveSerSilenciosoQuandoOTokenEstaAusente() {
        assertThatCode(() -> logoutUseCase.execute(null)).doesNotThrowAnyException();
    }

    @Test
    void deveSerIdempotenteSobreUmTokenJaRevogado() {
        AppUser user = createUser("logout3@fincore.dev");
        RefreshToken.Issued issued = issueToken(user);
        logoutUseCase.execute(issued.rawSecret());

        assertThatCode(() -> logoutUseCase.execute(issued.rawSecret())).doesNotThrowAnyException();
    }

    private AppUser createUser(String email) {
        AppUser user = new AppUser(
                email, passwordEncoder.encode("qualquer-senha"), "Nome", EnumSet.of(UserRole.AUDITOR), Instant.now());
        return appUserRepository.save(user);
    }

    private RefreshToken.Issued issueToken(AppUser user) {
        RefreshToken.Issued issued = RefreshToken.issue(
                user.id(), jwtProperties.refreshTtl(), Instant.now(), "junit", "127.0.0.1");
        refreshTokenRepository.save(issued.token());
        return issued;
    }
}
