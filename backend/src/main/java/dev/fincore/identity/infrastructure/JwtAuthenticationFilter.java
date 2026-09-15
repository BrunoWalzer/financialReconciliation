package dev.fincore.identity.infrastructure;

import dev.fincore.identity.domain.AccessToken;
import dev.fincore.identity.domain.CurrentUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Lê {@code Authorization: Bearer <token>}, verifica com {@link JwtTokenService} e, se
 * válido, autentica a requisição (TDS 21.2 — o restante da aplicação nunca lê o cabeçalho
 * diretamente).
 *
 * <p>Ausência de cabeçalho segue sem autenticação — cabe às regras de autorização do
 * {@code SecurityFilterChain} decidir se a rota exige uma (login/refresh/logout não
 * exigem). Um token presente e inválido, porém, é rejeitado aqui mesmo: apresentar uma
 * credencial ruim é diferente de não apresentar nenhuma.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenService jwtTokenService;
    private final AuthenticationEntryPoint authenticationEntryPoint;

    public JwtAuthenticationFilter(JwtTokenService jwtTokenService, AuthenticationEntryPoint authenticationEntryPoint) {
        this.jwtTokenService = jwtTokenService;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        String rawToken = header.substring(BEARER_PREFIX.length());
        try {
            AccessToken accessToken = jwtTokenService.verify(rawToken);
            authenticate(accessToken);
            chain.doFilter(request, response);
        } catch (InvalidAccessTokenException e) {
            SecurityContextHolder.clearContext();
            authenticationEntryPoint.commence(request, response, new AuthenticationServiceException("token inválido", e));
        }
    }

    private static void authenticate(AccessToken accessToken) {
        CurrentUser currentUser = CurrentUser.from(accessToken);
        List<GrantedAuthority> authorities = accessToken.roles().stream()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(role.name()))
                .toList();

        Authentication authentication = new UsernamePasswordAuthenticationToken(currentUser, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
