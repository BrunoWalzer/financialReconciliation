package dev.fincore.identity.domain;

import dev.fincore.shared.identifier.Uuid7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.ColumnTransformer;

/**
 * Sessão renovável de um usuário (TDS 7.1, 21.1) — híbrido deliberado: o access token é
 * stateless no caminho quente, mas a renovação é stateful, porque só assim ela pode ser
 * revogada de verdade.
 *
 * <p>Rotativo: cada uso troca este token por um novo ({@link #rotateTo}), e o anterior
 * nunca mais é válido. Um token já rotacionado (ou revogado por qualquer outro motivo)
 * apresentado de novo é o sinal clássico de token roubado — é isso que
 * {@code RefreshSessionUseCase} verifica antes de aceitar a rotação.
 *
 * <p>Não há coluna de "família" no banco: a família de um token é a cadeia formada por
 * {@code replacedById}, caminhável nos dois sentidos a partir de qualquer membro (ver
 * {@link TokenFamily}).
 */
@Entity
@Table(name = "refresh_token")
public class RefreshToken {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, updatable = false)
    private String tokenHash;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by_id")
    private UUID replacedById;

    @Column(name = "user_agent", updatable = false)
    private String userAgent;

    @ColumnTransformer(write = "?::inet")
    @Column(name = "ip", updatable = false, columnDefinition = "inet")
    private String ip;

    /** Exigido pelo JPA. Nunca chamado pela aplicação. */
    protected RefreshToken() {
    }

    private RefreshToken(
            UUID userId, String tokenHash, Instant issuedAt, Instant expiresAt, String userAgent, String ip) {
        this.id = Uuid7.generate();
        this.userId = Objects.requireNonNull(userId, "userId é obrigatório");
        this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash é obrigatório");
        this.issuedAt = Objects.requireNonNull(issuedAt, "issuedAt é obrigatório");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt é obrigatório");
        this.userAgent = userAgent;
        this.ip = ip;
    }

    /**
     * Emite uma nova sessão para {@code userId}. O chamador recebe o segredo em claro
     * (para devolver no cookie) e esta entidade guarda só o hash dele.
     */
    public static Issued issue(UUID userId, Duration ttl, Instant now, String userAgent, String ip) {
        String rawSecret = RefreshTokenSecret.generate();
        RefreshToken token = new RefreshToken(
                userId, RefreshTokenSecret.hash(rawSecret), now, now.plus(ttl), userAgent, ip);
        return new Issued(token, rawSecret);
    }

    public UUID id() {
        return id;
    }

    public UUID userId() {
        return userId;
    }

    public String tokenHash() {
        return tokenHash;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant revokedAt() {
        return revokedAt;
    }

    public UUID replacedById() {
        return replacedById;
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    /** Ativo: nem expirado, nem revogado (por rotação, logout ou reação a reuso). */
    public boolean isActive(Instant now) {
        return !isExpired(now) && !isRevoked();
    }

    /** Logout, ou reação a reuso: encerra este token sem que nada o substitua. */
    public void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = Objects.requireNonNull(now, "now é obrigatório");
        }
    }

    /**
     * Rotação: este token é substituído por {@code successorId}. É diferente de
     * {@link #revoke} — aqui existe um sucessor legítimo, o que {@link TokenFamily} usa
     * para caminhar a cadeia.
     */
    public void rotateTo(UUID successorId, Instant now) {
        this.revokedAt = Objects.requireNonNull(now, "now é obrigatório");
        this.replacedById = Objects.requireNonNull(successorId, "successorId é obrigatório");
    }

    /** O par (entidade a persistir, segredo em claro a devolver ao cliente). */
    public record Issued(RefreshToken token, String rawSecret) {
    }
}
