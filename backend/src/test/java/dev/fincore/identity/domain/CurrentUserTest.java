package dev.fincore.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** {@link CurrentUser} — a identidade que os controllers pedem via {@code @AuthenticationPrincipal}. */
class CurrentUserTest {

    @Test
    void deveReconstruirAPartirDoAccessToken() {
        UUID userId = UUID.randomUUID();
        AccessToken accessToken = new AccessToken(
                userId, Set.of(UserRole.ADMINISTRATOR), Instant.now(), Instant.now().plusSeconds(900));

        CurrentUser currentUser = CurrentUser.from(accessToken);

        assertThat(currentUser.userId()).isEqualTo(userId);
        assertThat(currentUser.hasRole(UserRole.ADMINISTRATOR)).isTrue();
        assertThat(currentUser.hasRole(UserRole.AUDITOR)).isFalse();
    }
}
