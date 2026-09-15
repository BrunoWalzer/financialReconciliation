package dev.fincore.infrastructure;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.fincore.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** A superficie operacional do M0: saude, informacao e metricas. Nao ha outra API. */
@AutoConfigureMockMvc
// O Spring Boot desliga os exportadores de metrica em teste; sem isto o endpoint
// /actuator/prometheus nao existe e o teste provaria menos do que promete.
@AutoConfigureObservability
@TestPropertySource(properties = "management.endpoint.health.show-details=always")
class ActuatorEndpointsIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void deveResponderUpQuandoOBancoEstaAcessivel() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.db.status").value("UP"));
    }

    @Test
    void deveExporLivenessEReadinessSeparadamente() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void deveExporVersaoERuleSetVersionEmInfo() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.build.version").exists())
                .andExpect(jsonPath("$.fincore.ruleSetVersion").exists());
    }

    @Test
    void deveExporMetricasEmFormatoPrometheus() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("jvm_memory_used_bytes")));
    }
}
