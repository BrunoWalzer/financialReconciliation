package dev.fincore.platform.security;

import dev.fincore.identity.infrastructure.JwtAuthenticationFilter;
import dev.fincore.identity.infrastructure.JwtTokenService;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * A fiação HTTP da segurança do FINCORE (TDS 21).
 *
 * <p><b>Sem sessão, sem CSRF.</b> Cada requisição autentica com o Bearer do access token,
 * que é imune a CSRF por construção (um site de terceiro não consegue lê-lo nem
 * inseri-lo). O único endpoint autenticado por cookie é {@code /auth/refresh}, protegido
 * por {@code SameSite=Strict} e por só aceitar {@code POST} — desabilitar CSRF
 * globalmente é aceitável por causa desta análise (TDS 21.3), não por conveniência de
 * teste.
 *
 * <p><b>Autorização por papel vive na aplicação, não aqui.</b> Este filtro só decide
 * "autenticado ou não" — {@code @PreAuthorize} nos casos de uso é quem decide "pode ou
 * não pode" (TDS 21.2), inclusive para as três negativas de papel exigidas pelos
 * critérios de aceite do M2.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_ENDPOINTS = {
        "/auth/login", "/auth/refresh", "/auth/logout",
    };

    /**
     * Sem autenticação — health check precisa responder para orquestrador e monitor
     * externos, nenhum dos quais carrega um JWT do FINCORE (comportamento já provado
     * pelos testes de M0/M1, mantido aqui).
     */
    private static final String[] ACTUATOR_ENDPOINTS = {"/actuator/**"};

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtTokenService jwtTokenService,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler,
            CorsConfigurationSource corsConfigurationSource)
            throws Exception {

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                // Justificativa registrada acima e em TDS 21.3 — não é conveniência de teste.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .requestMatchers(ACTUATOR_ENDPOINTS).permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtTokenService, authenticationEntryPoint),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Origem única, vinda de ambiente, com credenciais habilitadas por causa do cookie de
     * refresh (TDS 21.3). Nunca {@code "*"}.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties corsProperties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(corsProperties.allowedOrigin()));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Correlation-Id"));
        configuration.setExposedHeaders(List.of("X-Correlation-Id"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
