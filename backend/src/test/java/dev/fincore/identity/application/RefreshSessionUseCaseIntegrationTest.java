package dev.fincore.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.fincore.AbstractIntegrationTest;
import dev.fincore.identity.domain.AppUser;
import dev.fincore.identity.domain.RefreshToken;
import dev.fincore.identity.domain.UserRole;
import dev.fincore.identity.infrastructure.AppUserRepository;
import dev.fincore.identity.infrastructure.JwtProperties;
import dev.fincore.identity.infrastructure.RefreshTokenRepository;
import fincore.testsupport.MutableClock;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * {@link RefreshSessionUseCase} contra PostgreSQL real: rotação, expiração e a detecção
 * de reuso — o cenário de concorrência de segurança obrigatório do M2 (TDS 21.1,
 * Implementation Plan M2, critério de aceite 2).
 */
@Import(RefreshSessionUseCaseIntegrationTest.MutableClockConfig.class)
class RefreshSessionUseCaseIntegrationTest extends AbstractIntegrationTest {

    @TestConfiguration
    static class MutableClockConfig {
        // Nome de bean distinto de "clock" (o bean real, de ClockConfig): Spring Boot
        // recusa duas definições com o mesmo nome por padrão. @Primary decide o autowire.
        @Bean
        @Primary
        Clock testClock() {
            return new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        }
    }

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

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Clock clock;

    private MutableClock mutableClock() {
        return (MutableClock) clock;
    }

    @Test
    void deveRotacionarUmTokenValido() {
        AppUser user = createUser("refresh1@fincore.dev");
        RefreshToken.Issued issued = issueToken(user);

        SessionTokens tokens = refreshSessionUseCase.execute(issued.rawSecret(), "junit", "127.0.0.1");

        assertThat(tokens.accessToken()).isNotBlank();
        assertThat(tokens.refreshToken()).isNotEqualTo(issued.rawSecret());

        RefreshToken original = refreshTokenRepository.findById(issued.token().id()).orElseThrow();
        assertThat(original.isRevoked()).isTrue();
        assertThat(original.replacedById()).isNotNull();
    }

    @Test
    void oTokenAnteriorNaoDeveRenovarDeNovoAposRotacionado() {
        AppUser user = createUser("refresh2@fincore.dev");
        RefreshToken.Issued issued = issueToken(user);

        refreshSessionUseCase.execute(issued.rawSecret(), "junit", "127.0.0.1");

        assertThatThrownBy(() -> refreshSessionUseCase.execute(issued.rawSecret(), "junit", "127.0.0.1"))
                .isInstanceOf(RefreshTokenReuseDetectedException.class);
    }

    @Test
    void deveRevogarAFamiliaInteiraQuandoUmTokenJaRotacionadoEReapresentado() {
        AppUser user = createUser("refresh3@fincore.dev");
        RefreshToken.Issued first = issueToken(user);

        SessionTokens afterFirstRefresh = refreshSessionUseCase.execute(first.rawSecret(), "junit", "127.0.0.1");
        // O token do segundo refresh (filho do primeiro) ainda está ativo aqui.

        assertThatThrownBy(() -> refreshSessionUseCase.execute(first.rawSecret(), "junit", "127.0.0.1"))
                .isInstanceOf(RefreshTokenReuseDetectedException.class);

        // A família inteira — incluindo o token emitido pelo refresh legítimo anterior —
        // precisa estar revogada, não só o token reapresentado.
        List<Map<String, Object>> family = jdbc.queryForList(
                "select id, revoked_at from refresh_token where user_id = ?", user.id());
        assertThat(family).allSatisfy(row -> assertThat(row.get("revoked_at")).isNotNull());

        assertThat(afterFirstRefresh.refreshToken()).isNotBlank();
    }

    @Test
    void deveAuditarADeteccaoDeReuso() {
        AppUser user = createUser("refresh4@fincore.dev");
        RefreshToken.Issued issued = issueToken(user);
        issued.token().revoke(clock.instant());
        refreshTokenRepository.save(issued.token());

        assertThatThrownBy(() -> refreshSessionUseCase.execute(issued.rawSecret(), "junit", "127.0.0.1"))
                .isInstanceOf(RefreshTokenReuseDetectedException.class);

        Integer auditCount = jdbc.queryForObject(
                "select count(*) from audit_event where action = 'REFRESH_TOKEN_REUSE_DETECTED' and entity_id = ?",
                Integer.class,
                user.id());
        assertThat(auditCount).isEqualTo(1);
    }

    @Test
    void naoDeveAuditarUmaRenovacaoRotineira() {
        AppUser user = createUser("refresh5@fincore.dev");
        RefreshToken.Issued issued = issueToken(user);

        refreshSessionUseCase.execute(issued.rawSecret(), "junit", "127.0.0.1");

        Integer auditCount = jdbc.queryForObject(
                "select count(*) from audit_event where entity_id = ?", Integer.class, user.id());
        assertThat(auditCount).isZero();
    }

    @Test
    void deveRejeitarTokenExpirado() {
        AppUser user = createUser("refresh6@fincore.dev");
        RefreshToken.Issued issued = issueToken(user);

        mutableClock().advanceBy(jwtProperties.refreshTtl().plusSeconds(1));

        assertThatThrownBy(() -> refreshSessionUseCase.execute(issued.rawSecret(), "junit", "127.0.0.1"))
                .isInstanceOf(RefreshTokenInvalidException.class);
    }

    @Test
    void deveRejeitarTokenInexistente() {
        assertThatThrownBy(() -> refreshSessionUseCase.execute("token-que-nunca-existiu", "junit", "127.0.0.1"))
                .isInstanceOf(RefreshTokenInvalidException.class);
    }

    @Test
    void deveRejeitarAusenciaDeToken() {
        assertThatThrownBy(() -> refreshSessionUseCase.execute(null, "junit", "127.0.0.1"))
                .isInstanceOf(RefreshTokenInvalidException.class);
    }

    private AppUser createUser(String email) {
        AppUser user = new AppUser(
                email, passwordEncoder.encode("qualquer-senha"), "Nome", EnumSet.of(UserRole.AUDITOR), clock.instant());
        return appUserRepository.save(user);
    }

    private RefreshToken.Issued issueToken(AppUser user) {
        RefreshToken.Issued issued = RefreshToken.issue(
                user.id(), jwtProperties.refreshTtl(), clock.instant(), "junit", "127.0.0.1");
        refreshTokenRepository.save(issued.token());
        return issued;
    }
}
