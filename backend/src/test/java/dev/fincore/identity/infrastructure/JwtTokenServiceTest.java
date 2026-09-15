package dev.fincore.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.fincore.identity.domain.AccessToken;
import dev.fincore.identity.domain.UserRole;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * {@link JwtTokenService} sem Spring nem banco — construído diretamente com um
 * {@link Clock} fixo, para expiração ser testável sem esperar de verdade (regra 34).
 */
class JwtTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void deveEmitirEVerificarUmTokenValido() {
        JwtTokenService service = serviceAt(NOW);
        UUID userId = UUID.randomUUID();
        Set<UserRole> roles = Set.of(UserRole.RECONCILIATION_ANALYST);

        String token = service.issue(userId, roles);
        AccessToken verified = service.verify(token);

        assertThat(verified.userId()).isEqualTo(userId);
        assertThat(verified.roles()).isEqualTo(roles);
        assertThat(verified.issuedAt()).isEqualTo(NOW);
        assertThat(verified.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
    }

    @Test
    void deveRejeitarTokenExpirado() {
        JwtTokenService issuer = serviceAt(NOW);
        String token = issuer.issue(UUID.randomUUID(), Set.of(UserRole.AUDITOR));

        JwtTokenService verifierNoFuturo = serviceAt(NOW.plus(Duration.ofMinutes(16)));

        assertThatThrownBy(() -> verifierNoFuturo.verify(token)).isInstanceOf(InvalidAccessTokenException.class);
    }

    @Test
    void deveAceitarNoUltimoInstanteAntesDeExpirar() {
        JwtTokenService issuer = serviceAt(NOW);
        String token = issuer.issue(UUID.randomUUID(), Set.of(UserRole.AUDITOR));

        JwtTokenService verifierNoLimite = serviceAt(NOW.plus(Duration.ofMinutes(14)).plusSeconds(59));

        assertThat(verifierNoLimite.verify(token)).isNotNull();
    }

    @Test
    void deveRejeitarTokenComAssinaturaDeOutroSegredo() {
        JwtTokenService issuer = serviceAt(NOW, "primeiro-segredo-com-32-bytes-no-minimo!!");
        String token = issuer.issue(UUID.randomUUID(), Set.of(UserRole.AUDITOR));

        JwtTokenService verifierComOutroSegredo = serviceAt(NOW, "segundo-segredo-completamente-diferente!");

        assertThatThrownBy(() -> verifierComOutroSegredo.verify(token)).isInstanceOf(InvalidAccessTokenException.class);
    }

    @Test
    void deveRejeitarTokenMalformado() {
        JwtTokenService service = serviceAt(NOW);

        assertThatThrownBy(() -> service.verify("isto-nao-e-um-jwt")).isInstanceOf(InvalidAccessTokenException.class);
    }

    private static JwtTokenService serviceAt(Instant now) {
        return serviceAt(now, "test-only-jwt-signing-secret-key-32-bytes-minimum");
    }

    private static JwtTokenService serviceAt(Instant now, String secret) {
        Clock fixedClock = Clock.fixed(now, ZoneOffset.UTC);
        JwtProperties properties = new JwtProperties(secret, Duration.ofMinutes(15), Duration.ofDays(7));
        return new JwtTokenService(properties, fixedClock);
    }
}
