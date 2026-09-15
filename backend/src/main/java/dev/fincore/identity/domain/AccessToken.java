package dev.fincore.identity.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * O access token decodificado (Implementation Plan §6, VO). Só o necessário para
 * identificar e autorizar — sem dado financeiro, sem permissão calculada, sem claim
 * desnecessário (TDS 27, seção JWT / Access Token do M2).
 *
 * <p>Representa tanto o que {@code JwtTokenService} emite quanto o que ele verifica: a
 * emissão preenche {@code userId}/{@code roles}/{@code issuedAt}/{@code expiresAt} antes
 * de assinar; a verificação reconstrói o mesmo VO a partir de um JWT já validado.
 */
public record AccessToken(UUID userId, Set<UserRole> roles, Instant issuedAt, Instant expiresAt) {

    public AccessToken {
        Objects.requireNonNull(userId, "userId é obrigatório");
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("roles não pode ser vazio");
        }
        Objects.requireNonNull(issuedAt, "issuedAt é obrigatório");
        Objects.requireNonNull(expiresAt, "expiresAt é obrigatório");
        roles = Set.copyOf(roles);
    }
}
