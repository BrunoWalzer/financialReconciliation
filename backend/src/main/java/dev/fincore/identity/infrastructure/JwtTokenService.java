package dev.fincore.identity.infrastructure;

import dev.fincore.identity.domain.AccessToken;
import dev.fincore.identity.domain.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Emite e verifica o access token (JWT HS256, TDS 21.1).
 *
 * <p>Claims deliberadamente mínimas: {@code sub} (o id do usuário) e {@code roles}. Nada
 * financeiro, nada de permissão calculada, nada que a especificação não peça — o token
 * serve só para identificar e autorizar (seção "JWT / Access Token" do M2).
 *
 * <p>Sem {@code issuer}/{@code audience}: nenhum documento-fonte os define, e um único
 * serviço emitindo e verificando para si mesmo não tem para quem apontar um emissor ou
 * uma audiência distintos.
 */
@Service
public class JwtTokenService {

    private static final String ROLES_CLAIM = "roles";

    private final SecretKey key;
    private final Duration accessTtl;
    private final Clock clock;

    public JwtTokenService(JwtProperties properties, Clock clock) {
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.accessTtl = properties.accessTtl();
        this.clock = clock;
    }

    public Duration accessTtl() {
        return accessTtl;
    }

    public String issue(UUID userId, Set<UserRole> roles) {
        Instant now = clock.instant();
        List<String> roleNames = roles.stream().map(Enum::name).collect(Collectors.toList());

        return Jwts.builder()
                .subject(userId.toString())
                .claim(ROLES_CLAIM, roleNames)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTtl)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Verifica assinatura e expiração e reconstrói o {@link AccessToken}.
     *
     * @throws InvalidAccessTokenException assinatura inválida, formato malformado ou token expirado
     */
    public AccessToken verify(String rawToken) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(rawToken)
                    .getPayload();

            UUID userId = UUID.fromString(claims.getSubject());
            Set<UserRole> roles = parseRoles(claims);

            return new AccessToken(userId, roles, claims.getIssuedAt().toInstant(), claims.getExpiration().toInstant());
        } catch (JwtException | IllegalArgumentException e) {
            // IllegalArgumentException cobre subject que não é um UUID válido — trata-se
            // como token inválido, não como bug: um JWT malformado é entrada, não defeito.
            throw new InvalidAccessTokenException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Set<UserRole> parseRoles(Claims claims) {
        List<String> roleNames = claims.get(ROLES_CLAIM, List.class);
        if (roleNames == null || roleNames.isEmpty()) {
            throw new IllegalArgumentException("token sem roles");
        }
        return roleNames.stream().map(UserRole::valueOf).collect(Collectors.toUnmodifiableSet());
    }
}
