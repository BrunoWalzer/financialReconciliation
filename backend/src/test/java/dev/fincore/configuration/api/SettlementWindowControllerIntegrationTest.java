package dev.fincore.configuration.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
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

/** {@code GET|PUT /config/settlement-windows} de ponta a ponta (TDS 19.2). Sem POST no M3. */
@AutoConfigureMockMvc
class SettlementWindowControllerIntegrationTest extends AbstractIntegrationTest {

    private static final UUID SOURCE_PAIR_ID = UUID.fromString("00000000-0000-7000-8000-000000000103");

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
    void administradorDeveListarAsDuasJanelasSemeadas() throws Exception {
        mockMvc.perform(get("/config/settlement-windows").param("sourcePairId", SOURCE_PAIR_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @WithMockUser(authorities = "AUDITOR")
    void auditorDeveReceber403AoListar() throws Exception {
        mockMvc.perform(get("/config/settlement-windows").param("sourcePairId", SOURCE_PAIR_ID.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void administradorDeveAtualizarJanelaCreditCardViaHttp() throws Exception {
        String accessToken = createAdminAndLogin("settlement-http-update@fincore.dev");

        MvcResult listResult = mockMvc.perform(get("/config/settlement-windows")
                        .param("sourcePairId", SOURCE_PAIR_ID.toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andReturn();
        JsonNode windows = objectMapper.readTree(listResult.getResponse().getContentAsString());
        JsonNode creditCardWindow = findByPaymentMethod(windows, "CREDIT_CARD");
        String windowId = creditCardWindow.path("id").asText();
        String eTag = "\"" + creditCardWindow.path("version").asLong() + "\"";

        mockMvc.perform(put("/config/settlement-windows/" + windowId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .header(HttpHeaders.IF_MATCH, eTag)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("minDays", 2, "maxDays", 20))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minDays").value(2))
                .andExpect(jsonPath("$.maxDays").value(20))
                .andExpect(header().exists(HttpHeaders.ETAG));
    }

    @Test
    void deveResponder400ComMaxDaysMenorQueMinDaysViaHttp() throws Exception {
        String accessToken = createAdminAndLogin("settlement-http-invalid@fincore.dev");

        MvcResult listResult = mockMvc.perform(get("/config/settlement-windows")
                        .param("sourcePairId", SOURCE_PAIR_ID.toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andReturn();
        JsonNode windows = objectMapper.readTree(listResult.getResponse().getContentAsString());
        JsonNode defaultWindow = findByPaymentMethod(windows, null);

        mockMvc.perform(put("/config/settlement-windows/" + defaultWindow.path("id").asText())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .header(HttpHeaders.IF_MATCH, "\"" + defaultWindow.path("version").asLong() + "\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("minDays", 10, "maxDays", 1))))
                // A validação de min<=max é invariante de domínio (IllegalArgumentException),
                // não @Valid do request — GlobalErrorHandler trata como 400 genérico.
                .andExpect(status().isBadRequest());
    }

    private static JsonNode findByPaymentMethod(JsonNode windows, String paymentMethod) {
        for (JsonNode window : windows) {
            JsonNode pm = window.get("paymentMethod");
            boolean isNull = pm == null || pm.isNull();
            if (paymentMethod == null && isNull) {
                return window;
            }
            if (paymentMethod != null && !isNull && paymentMethod.equals(pm.asText())) {
                return window;
            }
        }
        throw new IllegalStateException("janela não encontrada para paymentMethod=" + paymentMethod);
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
