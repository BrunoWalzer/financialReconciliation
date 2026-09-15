package dev.fincore.identity.application;

import dev.fincore.audit.application.AuditEventRequest;
import dev.fincore.audit.application.AuditService;
import dev.fincore.audit.domain.ActorRef;
import dev.fincore.identity.domain.AppUser;
import dev.fincore.identity.domain.RefreshToken;
import dev.fincore.identity.domain.RefreshTokenSecret;
import dev.fincore.identity.domain.TokenFamily;
import dev.fincore.identity.infrastructure.AppUserRepository;
import dev.fincore.identity.infrastructure.JwtProperties;
import dev.fincore.identity.infrastructure.JwtTokenService;
import dev.fincore.identity.infrastructure.RefreshTokenRepository;
import dev.fincore.identity.infrastructure.TokenFamilyLocator;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rotaciona uma sessão (TDS 21.1). Renovação de token não é auditada por si (TDS 22.3 —
 * "o que NÃO é auditado" lista explicitamente "renovação de token"); só o desvio —
 * reuso de um token já revogado — gera {@link AuditEventRequest}.
 *
 * <p><b>Concorrência (TDS 8.2, cenário obrigatório "reuso de refresh token"):</b> duas
 * chamadas concorrentes com o mesmo token não podem ambas rotacionar com sucesso — isso
 * criaria dois filhos válidos a partir de um token de uso único. A escrita da rotação usa
 * {@link RefreshTokenRepository#revokeIfActive} — uma comparação-e-troca via
 * {@code WHERE revoked_at IS NULL} — em vez de carregar-modificar-salvar a entidade; a
 * chamada que perde a corrida encontra zero linhas afetadas e reage exatamente como uma
 * detecção de reuso, revogando a família inteira. Ver o Javadoc do repositório.
 *
 * <p>{@code noRollbackFor}: quando reuso é detectado, a família inteira já foi revogada e
 * a auditoria já foi gravada antes de lançar {@link RefreshTokenReuseDetectedException} —
 * essa escrita precisa sobreviver à exceção que devolve 401 ao cliente.
 */
@Service
public class RefreshSessionUseCase {

    private final RefreshTokenRepository refreshTokenRepository;
    private final AppUserRepository appUserRepository;
    private final TokenFamilyLocator tokenFamilyLocator;
    private final JwtTokenService jwtTokenService;
    private final AuditService auditService;
    private final Clock clock;
    private final JwtProperties jwtProperties;
    private final EntityManager entityManager;

    public RefreshSessionUseCase(
            RefreshTokenRepository refreshTokenRepository,
            AppUserRepository appUserRepository,
            TokenFamilyLocator tokenFamilyLocator,
            JwtTokenService jwtTokenService,
            AuditService auditService,
            Clock clock,
            JwtProperties jwtProperties,
            EntityManager entityManager) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.appUserRepository = appUserRepository;
        this.tokenFamilyLocator = tokenFamilyLocator;
        this.jwtTokenService = jwtTokenService;
        this.auditService = auditService;
        this.clock = clock;
        this.jwtProperties = jwtProperties;
        this.entityManager = entityManager;
    }

    @Transactional(noRollbackFor = RefreshTokenReuseDetectedException.class)
    public SessionTokens execute(String rawRefreshToken, String userAgent, String ip) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new RefreshTokenInvalidException();
        }

        Instant now = clock.instant();
        String hash = RefreshTokenSecret.hash(rawRefreshToken);
        RefreshToken presented = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(RefreshTokenInvalidException::new);

        // expiresAt nunca muda depois de criado: seguro ler do snapshot, sem corrida.
        if (presented.isExpired(now)) {
            throw new RefreshTokenInvalidException();
        }
        if (presented.isRevoked()) {
            reactToReuse(presented, now);
            throw new RefreshTokenReuseDetectedException();
        }

        AppUser user = appUserRepository.findById(presented.userId())
                .filter(AppUser::active)
                .orElseThrow(RefreshTokenInvalidException::new);

        int claimed = refreshTokenRepository.claimForRotation(presented.id(), now);
        if (claimed == 0) {
            // Perdeu a corrida: outra chamada já revogou este token entre a leitura acima
            // e esta escrita. presented está desatualizado (a leitura não viu a rotação
            // alheia); refresh() busca o estado real — incluindo o replaced_by_id que a
            // vencedora já ligou — antes de caminhar a família, ou o filho legítimo dela
            // ficaria de fora da revogação em cascata.
            entityManager.refresh(presented);
            reactToReuse(presented, now);
            throw new RefreshTokenReuseDetectedException();
        }

        // Só chega aqui quem venceu a corrida: o filho pode ser inserido sem risco de
        // ficar órfão, e linkSuccessor não precisa de condição — mais ninguém disputa
        // esta linha depois do claim acima.
        RefreshToken.Issued issued = RefreshToken.issue(user.id(), jwtProperties.refreshTtl(), now, userAgent, ip);
        refreshTokenRepository.save(issued.token());
        refreshTokenRepository.linkSuccessor(presented.id(), issued.token().id());

        String accessToken = jwtTokenService.issue(user.id(), user.roles());

        return new SessionTokens(
                accessToken, jwtTokenService.accessTtl(), issued.rawSecret(), jwtProperties.refreshTtl());
    }

    /** Um refresh já revogado foi apresentado de novo: revoga a família inteira e audita (TDS 21.1). */
    private void reactToReuse(RefreshToken presented, Instant now) {
        TokenFamily family = tokenFamilyLocator.locate(presented);
        family.revokeAll(now);
        for (RefreshToken member : family.members()) {
            refreshTokenRepository.save(member);
        }

        auditService.record(AuditEventRequest.of(
                ActorRef.system(), "REFRESH_TOKEN_REUSE_DETECTED", "AppUser", presented.userId()));
    }
}
