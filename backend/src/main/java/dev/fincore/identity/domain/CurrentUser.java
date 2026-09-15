package dev.fincore.identity.domain;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * A identidade autenticada da requisição em curso — a abstração que evita espalhar
 * {@code SecurityContextHolder} pela aplicação.
 *
 * <p>{@code JwtAuthenticationFilter} constrói uma instância a partir do
 * {@link AccessToken} verificado e a define como {@code principal} da autenticação do
 * Spring Security. Daí em diante, um controller pede o usuário atual com
 * {@code @AuthenticationPrincipal CurrentUser currentUser} — nenhum outro lugar da
 * aplicação toca o contexto de segurança diretamente.
 */
public record CurrentUser(UUID userId, Set<UserRole> roles) {

    public CurrentUser {
        Objects.requireNonNull(userId, "userId é obrigatório");
        roles = Set.copyOf(roles);
    }

    public static CurrentUser from(AccessToken accessToken) {
        return new CurrentUser(accessToken.userId(), accessToken.roles());
    }

    public boolean hasRole(UserRole role) {
        return roles.contains(role);
    }
}
