package dev.fincore.identity.api;

import dev.fincore.identity.application.GetCurrentUserUseCase;
import dev.fincore.identity.application.LoginCommand;
import dev.fincore.identity.application.LoginUseCase;
import dev.fincore.identity.application.LogoutUseCase;
import dev.fincore.identity.application.RefreshSessionUseCase;
import dev.fincore.identity.application.SessionTokens;
import dev.fincore.identity.domain.AppUser;
import dev.fincore.identity.domain.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /auth/*} (TDS 19.2). Único lugar da aplicação que sabe o que é um cookie —
 * {@code LoginUseCase}/{@code RefreshSessionUseCase}/{@code LogoutUseCase} só devolvem ou
 * recebem o segredo em claro (Implementation Plan M2: "cookie de refresh" é
 * responsabilidade da API, não da aplicação).
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final String REFRESH_COOKIE_NAME = "refreshToken";
    private static final String REFRESH_COOKIE_PATH = "/api/v1/auth";

    private final LoginUseCase loginUseCase;
    private final RefreshSessionUseCase refreshSessionUseCase;
    private final LogoutUseCase logoutUseCase;
    private final GetCurrentUserUseCase getCurrentUserUseCase;

    public AuthController(
            LoginUseCase loginUseCase,
            RefreshSessionUseCase refreshSessionUseCase,
            LogoutUseCase logoutUseCase,
            GetCurrentUserUseCase getCurrentUserUseCase) {
        this.loginUseCase = loginUseCase;
        this.refreshSessionUseCase = refreshSessionUseCase;
        this.logoutUseCase = logoutUseCase;
        this.getCurrentUserUseCase = getCurrentUserUseCase;
    }

    @PostMapping("/login")
    public ResponseEntity<AccessTokenResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        SessionTokens tokens = loginUseCase.execute(
                new LoginCommand(request.email(), request.password()),
                httpRequest.getHeader(HttpHeaders.USER_AGENT),
                httpRequest.getRemoteAddr());

        return withRefreshCookie(tokens);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AccessTokenResponse> refresh(
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken, HttpServletRequest httpRequest) {

        SessionTokens tokens = refreshSessionUseCase.execute(
                refreshToken, httpRequest.getHeader(HttpHeaders.USER_AGENT), httpRequest.getRemoteAddr());

        return withRefreshCookie(tokens);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken) {
        logoutUseCase.execute(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearedRefreshCookie().toString())
                .build();
    }

    @GetMapping("/me")
    public CurrentUserResponse me(@AuthenticationPrincipal CurrentUser currentUser) {
        AppUser user = getCurrentUserUseCase.execute(currentUser);
        return new CurrentUserResponse(user.id(), user.email(), user.displayName(), user.roles());
    }

    private static ResponseEntity<AccessTokenResponse> withRefreshCookie(SessionTokens tokens) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, tokens.refreshToken())
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(REFRESH_COOKIE_PATH)
                .maxAge(tokens.refreshTokenTtl())
                .build();

        AccessTokenResponse body = AccessTokenResponse.bearer(tokens.accessToken(), tokens.accessTokenTtl().toSeconds());
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString()).body(body);
    }

    private static ResponseCookie clearedRefreshCookie() {
        return ResponseCookie.from(REFRESH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(REFRESH_COOKIE_PATH)
                .maxAge(Duration.ZERO)
                .build();
    }
}
