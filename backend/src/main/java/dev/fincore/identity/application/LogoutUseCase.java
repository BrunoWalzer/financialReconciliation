package dev.fincore.identity.application;

import dev.fincore.identity.domain.RefreshToken;
import dev.fincore.identity.domain.RefreshTokenSecret;
import dev.fincore.identity.infrastructure.RefreshTokenRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Encerra uma sessão (TDS 21.1: "logout revoga; token revogado não renova").
 *
 * <p>Silenciosamente bem-sucedido mesmo quando não há nada para revogar — token ausente,
 * já revogado, ou inexistente. Logout não é auditado (TDS 22.3 não o lista entre os
 * eventos de acesso, e é o mesmo raciocínio de "renovação de token": rotina, não decisão).
 */
@Service
public class LogoutUseCase {

    private final RefreshTokenRepository refreshTokenRepository;
    private final Clock clock;

    public LogoutUseCase(RefreshTokenRepository refreshTokenRepository, Clock clock) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.clock = clock;
    }

    @Transactional
    public void execute(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        String hash = RefreshTokenSecret.hash(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(hash).ifPresent(this::revokeIfActive);
    }

    private void revokeIfActive(RefreshToken token) {
        if (!token.isRevoked()) {
            token.revoke(clock.instant());
            refreshTokenRepository.save(token);
        }
    }
}
