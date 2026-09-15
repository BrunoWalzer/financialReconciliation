package dev.fincore.configuration.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

/**
 * {@code GET|PUT /config/tolerances/{sourcePairId}} de ponta a ponta (TDS 19.2) — a
 * operação mais sensível do sistema (Domain §27.2), com JWT real para exercitar
 * {@code GetCurrentUserUseCase} dentro do controller.
 */
@AutoConfigureMockMvc
class ToleranceConfigControllerIntegrationTest extends AbstractIntegrationTest {

    private static final UUID SOURCE_PAIR_ID = UUID.fromString("00000000-0000-7000-8000-000000000103");

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
    void administradorDeveLerToleranciaComETag() throws Exception {
        mockMvc.perform(get("/config/tolerances/" + SOURCE_PAIR_ID))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andExpect(jsonPath("$.sourcePairId").value(SOURCE_PAIR_ID.toString()));
    }

    @Test
    @WithMockUser(authorities = "AUDITOR")
    void auditorDeveReceber403AoLer() throws Exception {
        mockMvc.perform(get("/config/tolerances/" + SOURCE_PAIR_ID))
                .andExpect(status().isForbidden());
    }

    @Test
    void administradorDeveAtualizarComIfMatchValidoViaHttp() throws Exception {
        // Par de fontes próprio: evita mutar o par semeado por V3, do qual outros testes
        // (ex.: ConfigSnapshotFactoryIntegrationTest) esperam os valores originais.
        UUID independentPairId = createIndependentSourcePairWithTolerance();
        String accessToken = createAdminAndLogin("tolerance-http-ok@fincore.dev");
        String currentETag = readCurrentETag(accessToken, independentPairId);

        mockMvc.perform(put("/config/tolerances/" + independentPairId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .header(HttpHeaders.IF_MATCH, currentETag)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("absoluteAmountMinor", 99, "currency", "USD"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.absoluteAmountMinor").value(99))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(header().exists(HttpHeaders.ETAG));
    }

    @Test
    void deveResponder409ComIfMatchDesatualizadoViaHttp() throws Exception {
        String accessToken = createAdminAndLogin("tolerance-http-stale@fincore.dev");

        mockMvc.perform(put("/config/tolerances/" + SOURCE_PAIR_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .header(HttpHeaders.IF_MATCH, "\"999999\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("absoluteAmountMinor", 1, "currency", "BRL"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFIGURATION_VERSION_CONFLICT"));
    }

    @Test
    void deveResponder400SemIfMatch() throws Exception {
        String accessToken = createAdminAndLogin("tolerance-http-noifmatch@fincore.dev");

        mockMvc.perform(put("/config/tolerances/" + SOURCE_PAIR_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("absoluteAmountMinor", 1, "currency", "BRL"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deveResponder400ComValorNegativo() throws Exception {
        String accessToken = createAdminAndLogin("tolerance-http-invalid@fincore.dev");
        String currentETag = readCurrentETag(accessToken);

        mockMvc.perform(put("/config/tolerances/" + SOURCE_PAIR_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .header(HttpHeaders.IF_MATCH, currentETag)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("absoluteAmountMinor", -1, "currency", "BRL"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private String readCurrentETag(String accessToken) throws Exception {
        return readCurrentETag(accessToken, SOURCE_PAIR_ID);
    }

    private String readCurrentETag(String accessToken, UUID sourcePairId) throws Exception {
        MvcResult result = mockMvc.perform(get("/config/tolerances/" + sourcePairId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andReturn();
        return result.getResponse().getHeader(HttpHeaders.ETAG);
    }

    private UUID createIndependentSourcePairWithTolerance() {
        UUID leftId = UUID.randomUUID();
        UUID rightId = UUID.randomUUID();
        insertSource(leftId, "TEST_LEFT_" + leftId);
        insertSource(rightId, "TEST_RIGHT_" + rightId);
        UUID pairId = UUID.randomUUID();
        jdbc.update(
                "insert into source_pair (id, left_source_id, right_source_id, code) values (?, ?, ?, ?)",
                pairId, leftId, rightId, "TEST_PAIR_" + pairId);
        jdbc.update(
                """
                insert into tolerance_config (id, source_pair_id, absolute_amount_minor, currency, updated_at, version)
                values (?, ?, 2, 'BRL', now(), 0)
                """,
                UUID.randomUUID(), pairId);
        return pairId;
    }

    private void insertSource(UUID id, String code) {
        jdbc.update(
                """
                insert into source (id, code, name, timezone, decimal_separator, thousands_separator, date_formats, rounding_mode, active)
                values (?, ?, 'Nome', 'UTC', ',', '.', array['dd/MM/yyyy'], 'HALF_UP', true)
                """,
                id, code);
    }

    private String createAdminAndLogin(String email) throws Exception {
        String rawPassword = "senha-correta-123";
        AppUser user = new AppUser(
                email, passwordEncoder.encode(rawPassword), "Admin", EnumSet.of(UserRole.ADMINISTRATOR), Instant.now());
        appUserRepository.save(user);

        MvcResult result = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", rawPassword))))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("accessToken").asText();
    }
}
