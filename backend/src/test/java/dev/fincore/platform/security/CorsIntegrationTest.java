package dev.fincore.platform.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.fincore.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

/**
 * CORS de origem única (TDS 21.3). {@code application-test.yml} fixa
 * {@code fincore.cors.allowed-origin=http://localhost:5173}.
 */
@AutoConfigureMockMvc
class CorsIntegrationTest extends AbstractIntegrationTest {

    private static final String ALLOWED_ORIGIN = "http://localhost:5173";
    private static final String DISALLOWED_ORIGIN = "http://evil.example.com";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void deveEcoarAOrigemPermitidaEmUmPreflight() throws Exception {
        mockMvc.perform(options("/auth/login")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    void naoDeveEcoarUmaOrigemNaoPermitida() throws Exception {
        mockMvc.perform(options("/auth/login")
                        .header(HttpHeaders.ORIGIN, DISALLOWED_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}
