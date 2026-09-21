package dev.fincore.ingestion.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.fincore.AbstractIntegrationTest;
import dev.fincore.identity.domain.AppUser;
import dev.fincore.identity.domain.UserRole;
import dev.fincore.identity.infrastructure.AppUserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Upload acima do limite de bytes (TDS 9.1) — limite baixo só neste teste, via propriedade
 * dinâmica, para não precisar montar um arquivo de 50 MB na suíte.
 */
@AutoConfigureMockMvc
class ImportControllerUploadSizeLimitIntegrationTest extends AbstractIntegrationTest {

    @DynamicPropertySource
    static void lowByteLimit(DynamicPropertyRegistry registry) {
        registry.add("fincore.ingestion.max-upload-bytes", () -> 100);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void deveResponder413SemPersistirNadaQuandoArquivoExcedeOLimite() throws Exception {
        String accessToken = createUserAndLogin("import-http-toolarge@fincore.dev", UserRole.RECONCILIATION_ANALYST);
        byte[] oversized = "x".repeat(500).getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "grande.csv", "text/csv", oversized);

        mockMvc.perform(multipart("/imports")
                        .file(file)
                        .param("sourceCode", "INTERNAL_SALES")
                        .param("referenceDate", "2026-11-01")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"));
    }

    private String createUserAndLogin(String email, UserRole role) throws Exception {
        String rawPassword = "senha-correta-123";
        AppUser user = new AppUser(email, passwordEncoder.encode(rawPassword), "Nome", EnumSet.of(role), Instant.now());
        appUserRepository.save(user);

        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", rawPassword))))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("accessToken").asText();
    }
}
