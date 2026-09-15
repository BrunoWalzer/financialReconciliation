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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** {@code GET|PUT /config/coverage-expectations} de ponta a ponta (TDS 19.2). Sem POST no M3. */
@AutoConfigureMockMvc
class CoverageExpectationControllerIntegrationTest extends AbstractIntegrationTest {

    private static final UUID ACQUIRER_SETTLEMENT_ID = UUID.fromString("00000000-0000-7000-8000-000000000102");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @WithMockUser(authorities = "ADMINISTRATOR")
    void administradorDeveLerExpectativaSemeada() throws Exception {
        mockMvc.perform(get("/config/coverage-expectations").param("sourceId", ACQUIRER_SETTLEMENT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schedule").value("BUSINESS_DAYS"))
                .andExpect(jsonPath("$.graceDays").value(1));
    }

    @Test
    @WithMockUser(authorities = "AUDITOR")
    void auditorDeveReceber403AoLer() throws Exception {
        mockMvc.perform(get("/config/coverage-expectations").param("sourceId", ACQUIRER_SETTLEMENT_ID.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ADMINISTRATOR")
    void deveResponder404ParaFonteSemExpectativa() throws Exception {
        mockMvc.perform(get("/config/coverage-expectations").param("sourceId", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void administradorDeveAtualizarViaHttp() throws Exception {
        // Fonte e expectativa próprias: evita mutar a linha semeada por V3 para
        // ACQUIRER_SETTLEMENT, que administradorDeveLerExpectativaSemeada lê esperando
        // BUSINESS_DAYS/1 originais.
        UUID sourceId = createIndependentSourceWithCoverageExpectation();
        String accessToken = createAdminAndLogin("coverage-http-update@fincore.dev");

        MvcResult getResult = mockMvc.perform(get("/config/coverage-expectations")
                        .param("sourceId", sourceId.toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andReturn();
        long version = objectMapper.readTree(getResult.getResponse().getContentAsString()).path("version").asLong();
        String id = objectMapper.readTree(getResult.getResponse().getContentAsString()).path("id").asText();

        mockMvc.perform(put("/config/coverage-expectations/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .header(HttpHeaders.IF_MATCH, "\"" + version + "\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("schedule", "DAILY", "graceDays", 3))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schedule").value("DAILY"))
                .andExpect(jsonPath("$.graceDays").value(3))
                .andExpect(header().exists(HttpHeaders.ETAG));
    }

    @Test
    void deveResponder409ComIfMatchDesatualizadoViaHttp() throws Exception {
        UUID sourceId = createIndependentSourceWithCoverageExpectation();
        String accessToken = createAdminAndLogin("coverage-http-stale@fincore.dev");

        MvcResult getResult = mockMvc.perform(get("/config/coverage-expectations")
                        .param("sourceId", sourceId.toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andReturn();
        String id = objectMapper.readTree(getResult.getResponse().getContentAsString()).path("id").asText();

        mockMvc.perform(put("/config/coverage-expectations/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .header(HttpHeaders.IF_MATCH, "\"999999\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("schedule", "DAILY", "graceDays", 3))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFIGURATION_VERSION_CONFLICT"));
    }

    private UUID createIndependentSourceWithCoverageExpectation() {
        UUID sourceId = UUID.randomUUID();
        jdbc.update(
                """
                insert into source (id, code, name, timezone, decimal_separator, thousands_separator, date_formats, rounding_mode, active)
                values (?, ?, 'Nome', 'UTC', ',', '.', array['dd/MM/yyyy'], 'HALF_UP', true)
                """,
                sourceId, "TEST_SOURCE_" + sourceId);
        jdbc.update(
                "insert into coverage_expectation (id, source_id, schedule, grace_days, active, version) values (?, ?, 'BUSINESS_DAYS', 1, true, 0)",
                UUID.randomUUID(), sourceId);
        return sourceId;
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
