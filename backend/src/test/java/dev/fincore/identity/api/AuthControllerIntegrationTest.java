package dev.fincore.identity.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.fincore.AbstractIntegrationTest;
import dev.fincore.identity.domain.AppUser;
import dev.fincore.identity.domain.UserRole;
import dev.fincore.identity.infrastructure.AppUserRepository;
import dev.fincore.identity.infrastructure.JwtProperties;
import fincore.testsupport.MutableClock;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * {@code /auth/*} de ponta a ponta, com Spring Security real (TDS 19.2, 21).
 */
@AutoConfigureMockMvc
@Import(AuthControllerIntegrationTest.MutableClockConfig.class)
class AuthControllerIntegrationTest extends AbstractIntegrationTest {

    @TestConfiguration
    static class MutableClockConfig {
        @Bean
        @Primary
        Clock testClock() {
            return new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Clock clock;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void deveFazerLoginEDevolverAccessTokenMaisCookieDeRefresh() throws Exception {
        createUser("http-login1@fincore.dev", "senha-correta-123", UserRole.RECONCILIATION_ANALYST);

        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("http-login1@fincore.dev", "senha-correta-123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(cookie().exists("refreshToken"))
                .andExpect(cookie().httpOnly("refreshToken", true))
                .andExpect(cookie().secure("refreshToken", true))
                .andExpect(cookie().path("refreshToken", "/api/v1/auth"))
                .andReturn();

        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).containsIgnoringCase("SameSite=Strict");
    }

    @Test
    void deveResponder401ComCredenciaisInvalidas() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("ninguem@fincore.dev", "qualquer")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @Test
    void deveResponder400ComCamposEmBranco() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void deveIgnorarCampoDePapelExtraNoCorpoDeLogin() throws Exception {
        createUser("http-login2@fincore.dev", "senha-correta-123", UserRole.AUDITOR);

        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"email\":\"http-login2@fincore.dev\",\"password\":\"senha-correta-123\",\"role\":\"ADMINISTRATOR\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String accessToken = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("accessToken")
                .asText();

        mockMvc.perform(get("/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("AUDITOR"))
                .andExpect(jsonPath("$.roles.length()").value(1));
    }

    @Test
    void deveExporIdentidadeAutenticadaEmMe() throws Exception {
        AppUser user = createUser("http-me@fincore.dev", "senha-correta-123", UserRole.AUDITOR);
        String accessToken = loginAndGetAccessToken("http-me@fincore.dev", "senha-correta-123");

        mockMvc.perform(get("/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.id().toString()))
                .andExpect(jsonPath("$.email").value("http-me@fincore.dev"))
                .andExpect(jsonPath("$.roles[0]").value("AUDITOR"));
    }

    @Test
    void deveResponder401EmMeSemToken() throws Exception {
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void deveResponder401EmMeComTokenMalformado() throws Exception {
        mockMvc.perform(get("/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer isto-nao-e-um-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void deveResponder401EmMeComAccessTokenExpirado() throws Exception {
        createUser("http-expired@fincore.dev", "senha-correta-123", UserRole.AUDITOR);
        String accessToken = loginAndGetAccessToken("http-expired@fincore.dev", "senha-correta-123");

        ((MutableClock) clock).advanceBy(jwtProperties.accessTtl().plusSeconds(1));

        mockMvc.perform(get("/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void deveRotacionarViaHttp() throws Exception {
        createUser("http-refresh@fincore.dev", "senha-correta-123", UserRole.AUDITOR);

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("http-refresh@fincore.dev", "senha-correta-123")))
                .andReturn();
        Cookie refreshCookie = loginResult.getResponse().getCookie("refreshToken");

        mockMvc.perform(post("/auth/refresh").cookie(refreshCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(cookie().exists("refreshToken"));
    }

    @Test
    void deveResponder401NoRefreshSemCookie() throws Exception {
        mockMvc.perform(post("/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void deveDeslogarELimparOCookie() throws Exception {
        createUser("http-logout@fincore.dev", "senha-correta-123", UserRole.AUDITOR);
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("http-logout@fincore.dev", "senha-correta-123")))
                .andReturn();
        Cookie refreshCookie = loginResult.getResponse().getCookie("refreshToken");

        mockMvc.perform(post("/auth/logout").cookie(refreshCookie))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("refreshToken", 0));

        mockMvc.perform(post("/auth/refresh").cookie(refreshCookie))
                .andExpect(status().isUnauthorized());
    }

    private String loginAndGetAccessToken(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, password)))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("accessToken").asText();
    }

    private String loginJson(String email, String password) throws Exception {
        return objectMapper.writeValueAsString(Map.of("email", email, "password", password));
    }

    private AppUser createUser(String email, String rawPassword, UserRole role) {
        AppUser user = new AppUser(
                email, passwordEncoder.encode(rawPassword), "Nome", EnumSet.of(role), Instant.now());
        return appUserRepository.save(user);
    }
}
