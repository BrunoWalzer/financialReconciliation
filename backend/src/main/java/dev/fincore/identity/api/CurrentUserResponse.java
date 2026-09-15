package dev.fincore.identity.api;

import dev.fincore.identity.domain.UserRole;
import java.util.Set;
import java.util.UUID;

/** {@code GET /auth/me} (TDS 19.2). Nunca inclui senha nem hash. */
public record CurrentUserResponse(UUID id, String email, String displayName, Set<UserRole> roles) {
}
