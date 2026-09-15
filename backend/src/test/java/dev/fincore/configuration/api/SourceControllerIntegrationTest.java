package dev.fincore.configuration.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.fincore.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/** {@code GET /config/sources} de ponta a ponta (TDS 19.2) — só leitura, sem endpoint de mutação no M3. */
@AutoConfigureMockMvc
class SourceControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(authorities = "ADMINISTRATOR")
    void administradorDeveListarFontesSemeadas() throws Exception {
        mockMvc.perform(get("/config/sources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code=='INTERNAL_SALES')]").exists())
                .andExpect(jsonPath("$[?(@.code=='ACQUIRER_SETTLEMENT')]").exists());
    }

    @Test
    @WithMockUser(authorities = "AUDITOR")
    void auditorDeveReceber403() throws Exception {
        mockMvc.perform(get("/config/sources"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void semAutenticacaoDeveReceber401() throws Exception {
        mockMvc.perform(get("/config/sources"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }
}
