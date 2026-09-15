package dev.fincore.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** {@link RefreshToken} — expiração, revogação e rotação (TDS 21.1). */
class RefreshTokenTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Duration TTL = Duration.ofDays(7);

    @Test
    void deveEmitirComSegredoEmClaroDiferenteDoHashArmazenado() {
        RefreshToken.Issued issued = RefreshToken.issue(UUID.randomUUID(), TTL, NOW, "agent", "203.0.113.10");

        assertThat(issued.rawSecret()).isNotBlank();
        assertThat(issued.token().tokenHash()).isNotEqualTo(issued.rawSecret());
        assertThat(issued.token().tokenHash()).isEqualTo(RefreshTokenSecret.hash(issued.rawSecret()));
    }

    @Test
    void deveEstarAtivoLogoAposEmitido() {
        RefreshToken.Issued issued = RefreshToken.issue(UUID.randomUUID(), TTL, NOW, null, null);

        assertThat(issued.token().isActive(NOW)).isTrue();
        assertThat(issued.token().isExpired(NOW)).isFalse();
        assertThat(issued.token().isRevoked()).isFalse();
    }

    @Test
    void deveEstarExpiradoAposOTtl() {
        RefreshToken.Issued issued = RefreshToken.issue(UUID.randomUUID(), TTL, NOW, null, null);
        Instant depoisDoTtl = NOW.plus(TTL).plusSeconds(1);

        assertThat(issued.token().isExpired(depoisDoTtl)).isTrue();
        assertThat(issued.token().isActive(depoisDoTtl)).isFalse();
    }

    @Test
    void deveEstarRevogadoAposRevoke() {
        RefreshToken.Issued issued = RefreshToken.issue(UUID.randomUUID(), TTL, NOW, null, null);

        issued.token().revoke(NOW);

        assertThat(issued.token().isRevoked()).isTrue();
        assertThat(issued.token().isActive(NOW)).isFalse();
        assertThat(issued.token().replacedById()).isNull();
    }

    @Test
    void deveApontarParaOSucessorAposRotacao() {
        RefreshToken.Issued issued = RefreshToken.issue(UUID.randomUUID(), TTL, NOW, null, null);
        UUID successorId = UUID.randomUUID();

        issued.token().rotateTo(successorId, NOW);

        assertThat(issued.token().isRevoked()).isTrue();
        assertThat(issued.token().replacedById()).isEqualTo(successorId);
    }

    @Test
    void revokeNaoDeveSobrescreverOInstanteDeUmaRevogacaoAnterior() {
        RefreshToken.Issued issued = RefreshToken.issue(UUID.randomUUID(), TTL, NOW, null, null);
        issued.token().revoke(NOW);

        issued.token().revoke(NOW.plusSeconds(60));

        assertThat(issued.token().revokedAt()).isEqualTo(NOW);
    }
}
