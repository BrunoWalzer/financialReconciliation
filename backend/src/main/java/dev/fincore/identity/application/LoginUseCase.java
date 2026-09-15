package dev.fincore.identity.application;

import dev.fincore.audit.application.AuditEventRequest;
import dev.fincore.audit.application.AuditService;
import dev.fincore.audit.domain.ActorRef;
import dev.fincore.identity.domain.AppUser;
import dev.fincore.identity.domain.LoginThrottle;
import dev.fincore.identity.domain.RefreshToken;
import dev.fincore.identity.infrastructure.AppUserRepository;
import dev.fincore.identity.infrastructure.JwtProperties;
import dev.fincore.identity.infrastructure.JwtTokenService;
import dev.fincore.identity.infrastructure.LoginThrottleRepository;
import dev.fincore.identity.infrastructure.RefreshTokenRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Autentica um usuário (TDS 16 — 1 transação: throttle + refresh token + auditoria).
 *
 * <p><b>E-mail inexistente e senha errada devolvem a mesma resposta, no mesmo tempo</b>
 * (Domain §27.4). A defesa contra diferença de tempo é rodar {@code passwordEncoder.matches}
 * sempre, mesmo quando o usuário não existe — contra um hash fixo, calculado uma vez,
 * com o mesmo custo de um hash real. Sem isso, "usuário não encontrado" retornaria mais
 * rápido que "senha errada" e o tempo de resposta vazaria se o e-mail existe.
 *
 * <p>{@code noRollbackFor}: mesmo quando o login falha, o incremento do contador de
 * falhas em {@code login_throttle} precisa ser persistido — é o próprio mecanismo de
 * defesa contra força bruta (TDS 21.4). Sem isso, toda tentativa falha reverteria a
 * própria contagem que deveria bloqueá-la.
 */
@Service
public class LoginUseCase {

    private final AppUserRepository appUserRepository;
    private final LoginThrottleRepository loginThrottleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final AuditService auditService;
    private final Clock clock;
    private final JwtProperties jwtProperties;
    private final String dummyHash;

    public LoginUseCase(
            AppUserRepository appUserRepository,
            LoginThrottleRepository loginThrottleRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService,
            AuditService auditService,
            Clock clock,
            JwtProperties jwtProperties) {
        this.appUserRepository = appUserRepository;
        this.loginThrottleRepository = loginThrottleRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.auditService = auditService;
        this.clock = clock;
        this.jwtProperties = jwtProperties;
        // Calculado uma vez, com o mesmo custo de BCrypt de um hash real — ver Javadoc da classe.
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing-safety");
    }

    @Transactional(noRollbackFor = InvalidCredentialsException.class)
    public SessionTokens execute(LoginCommand command, String userAgent, String ip) {
        Objects.requireNonNull(command, "command é obrigatório");
        Instant now = clock.instant();

        LoginThrottle throttle = loginThrottleRepository.findById(command.email())
                .orElseGet(() -> new LoginThrottle(command.email()));

        if (throttle.isLocked(now)) {
            throw new InvalidCredentialsException();
        }

        Optional<AppUser> maybeUser = appUserRepository.findByEmail(command.email());
        String hashToCheck = maybeUser.map(AppUser::passwordHash).orElse(dummyHash);
        boolean passwordMatches = passwordEncoder.matches(command.password(), hashToCheck);

        if (maybeUser.isEmpty() || !passwordMatches || !maybeUser.get().active()) {
            throttle.recordFailure(now);
            loginThrottleRepository.save(throttle);
            auditLoginFailed(command.email(), maybeUser.orElse(null), now);
            throw new InvalidCredentialsException();
        }

        AppUser user = maybeUser.get();
        throttle.recordSuccess();
        loginThrottleRepository.save(throttle);

        String accessToken = jwtTokenService.issue(user.id(), user.roles());
        RefreshToken.Issued issued = RefreshToken.issue(user.id(), jwtProperties.refreshTtl(), now, userAgent, ip);
        refreshTokenRepository.save(issued.token());

        auditService.record(AuditEventRequest.of(
                ActorRef.user(user.id(), user.email()), "LOGIN_SUCCEEDED", "AppUser", user.id()));

        return new SessionTokens(
                accessToken, jwtTokenService.accessTtl(), issued.rawSecret(), jwtProperties.refreshTtl());
    }

    private void auditLoginFailed(String attemptedEmail, AppUser user, Instant now) {
        UUID entityId = user != null ? user.id() : null;
        ActorRef actor = user != null ? ActorRef.user(user.id(), user.email()) : ActorRef.system();

        auditService.record(new AuditEventRequest(
                actor,
                "LOGIN_FAILED",
                "AppUser",
                entityId,
                null,
                "{\"attemptedEmail\":\"" + escapeJson(attemptedEmail) + "\"}",
                null,
                null,
                null));
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
