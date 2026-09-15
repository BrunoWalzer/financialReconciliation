package dev.fincore.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** {@link TokenFamily} — revogação em cascata da linhagem inteira (TDS 21.1). */
class TokenFamilyTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Duration TTL = Duration.ofDays(7);

    @Test
    void deveRevogarTodosOsMembrosAtivos() {
        RefreshToken first = RefreshToken.issue(UUID.randomUUID(), TTL, NOW, null, null).token();
        RefreshToken second = RefreshToken.issue(UUID.randomUUID(), TTL, NOW, null, null).token();
        TokenFamily family = new TokenFamily(List.of(first, second));

        family.revokeAll(NOW);

        assertThat(first.isRevoked()).isTrue();
        assertThat(second.isRevoked()).isTrue();
    }

    @Test
    void naoDeveTocarUmMembroJaRotacionado() {
        RefreshToken first = RefreshToken.issue(UUID.randomUUID(), TTL, NOW, null, null).token();
        UUID successorId = UUID.randomUUID();
        first.rotateTo(successorId, NOW);
        Instant revokedAtOriginal = first.revokedAt();

        TokenFamily family = new TokenFamily(List.of(first));
        family.revokeAll(NOW.plusSeconds(60));

        // Idempotente: já revogado (pela rotação) não é revogado de novo com outro instante.
        assertThat(first.revokedAt()).isEqualTo(revokedAtOriginal);
        assertThat(first.replacedById()).isEqualTo(successorId);
    }
}
