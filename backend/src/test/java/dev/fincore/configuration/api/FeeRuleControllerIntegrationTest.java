package dev.fincore.configuration.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** {@code GET|POST|PUT /config/fee-rules} de ponta a ponta (TDS 19.2). */
@AutoConfigureMockMvc
class FeeRuleControllerIntegrationTest extends AbstractIntegrationTest {

    private static final UUID INTERNAL_SALES_ID = UUID.fromString("00000000-0000-7000-8000-000000000101");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @WithMockUser(authorities = "ADMINISTRATOR")
    void administradorDeveListarPorFonte() throws Exception {
        mockMvc.perform(get("/config/fee-rules").param("sourceId", INTERNAL_SALES_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @WithMockUser(authorities = "AUDITOR")
    void auditorDeveReceber403AoListar() throws Exception {
        mockMvc.perform(get("/config/fee-rules").param("sourceId", INTERNAL_SALES_ID.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void administradorDeveCriarNovaRegraViaHttp() throws Exception {
        String accessToken = createAdminAndLogin("fee-rule-http-create@fincore.dev");

        mockMvc.perform(post("/config/fee-rules")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "sourceId", INTERNAL_SALES_ID.toString(),
                                "paymentMethod", "HTTP_CREATE_TEST",
                                "percentageBp", 150,
                                "fixedAmountMinor", 0,
                                "roundingMode", "HALF_UP"))))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andExpect(jsonPath("$.percentageBp").value(150));
    }

    @Test
    void deveResponder400ComPercentageBpForaDoIntervaloViaHttp() throws Exception {
        String accessToken = createAdminAndLogin("fee-rule-http-invalid@fincore.dev");

        mockMvc.perform(post("/config/fee-rules")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "sourceId", INTERNAL_SALES_ID.toString(),
                                "paymentMethod", "HTTP_INVALID_TEST",
                                "percentageBp", 10_001,
                                "fixedAmountMinor", 0,
                                "roundingMode", "HALF_UP"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void deveResponder409AoCriarRegraJaAtivaViaHttp() throws Exception {
        String accessToken = createAdminAndLogin("fee-rule-http-conflict@fincore.dev");
        Map<String, Object> body = Map.of(
                "sourceId", INTERNAL_SALES_ID.toString(),
                "paymentMethod", "HTTP_CONFLICT_TEST",
                "percentageBp", 100,
                "fixedAmountMinor", 0,
                "roundingMode", "HALF_UP");

        mockMvc.perform(post("/config/fee-rules")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/config/fee-rules")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FEE_RULE_ALREADY_ACTIVE"));
    }

    @Test
    void administradorDeveAtualizarRegraViaHttp() throws Exception {
        String accessToken = createAdminAndLogin("fee-rule-http-update@fincore.dev");

        MvcResult createResult = mockMvc.perform(post("/config/fee-rules")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "sourceId", INTERNAL_SALES_ID.toString(),
                                "paymentMethod", "HTTP_UPDATE_TEST",
                                "percentageBp", 100,
                                "fixedAmountMinor", 0,
                                "roundingMode", "HALF_UP"))))
                .andReturn();

        String createdId = objectMapper.readTree(createResult.getResponse().getContentAsString()).path("id").asText();
        String eTag = createResult.getResponse().getHeader(HttpHeaders.ETAG);

        mockMvc.perform(put("/config/fee-rules/" + createdId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .header(HttpHeaders.IF_MATCH, eTag)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "percentageBp", 300, "fixedAmountMinor", 10, "roundingMode", "DOWN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.percentageBp").value(300));
    }

    private String createAdminAndLogin(String email) throws Exception {
        String rawPassword = "senha-correta-123";
        AppUser user = new AppUser(
                email, passwordEncoder.encode(rawPassword), "Admin", EnumSet.of(UserRole.ADMINISTRATOR), Instant.now());
        appUserRepository.save(user);

        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", rawPassword))))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("accessToken").asText();
    }
}
