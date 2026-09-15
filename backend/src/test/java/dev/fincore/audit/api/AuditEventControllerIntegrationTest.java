package dev.fincore.audit.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.fincore.AbstractIntegrationTest;
import dev.fincore.identity.domain.AppUser;
import dev.fincore.identity.domain.UserRole;
import dev.fincore.identity.infrastructure.AppUserRepository;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code GET /audit-events} de ponta a ponta — "leitura, todos os papéis"
 * (Implementation Plan M2).
 */
@AutoConfigureMockMvc
class AuditEventControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @ParameterizedTest
    @EnumSource(UserRole.class)
    void qualquerPapelAutenticadoDeveLerAAuditoria(UserRole role) throws Exception {
        String email = "audit-events-" + role.name().toLowerCase() + "@fincore.dev";
        createUser(email, "senha-correta-123", role);
        String accessToken = loginAndGetAccessToken(email, "senha-correta-123");

        mockMvc.perform(get("/audit-events").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").value(0));
    }

    @Test
    void deveResponder401SemAutenticacao() throws Exception {
        mockMvc.perform(get("/audit-events"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void deveIncluirOEventoDeLoginNaListagem() throws Exception {
        String email = "audit-events-conteudo@fincore.dev";
        createUser(email, "senha-correta-123", UserRole.AUDITOR);
        String accessToken = loginAndGetAccessToken(email, "senha-correta-123");

        mockMvc.perform(get("/audit-events")
                        .param("size", "100")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.action == 'LOGIN_SUCCEEDED' && @.actorLabel == '" + email + "')]")
                        .exists());
    }

    @Test
    void deveRejeitarCampoDeOrdenacaoDesconhecido() throws Exception {
        String email = "audit-events-sort@fincore.dev";
        createUser(email, "senha-correta-123", UserRole.AUDITOR);
        String accessToken = loginAndGetAccessToken(email, "senha-correta-123");

        mockMvc.perform(get("/audit-events")
                        .param("sort", "passwordHash,asc")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isBadRequest());
    }

    private String loginAndGetAccessToken(String email, String password) throws Exception {
        var result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("accessToken").asText();
    }

    private AppUser createUser(String email, String rawPassword, UserRole role) {
        AppUser user = new AppUser(
                email, passwordEncoder.encode(rawPassword), "Nome", EnumSet.of(role), Instant.now());
        return appUserRepository.save(user);
    }
}
