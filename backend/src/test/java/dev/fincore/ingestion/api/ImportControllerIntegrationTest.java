package dev.fincore.ingestion.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** {@code /imports} de ponta a ponta (TDS 20.2) — upload síncrono, sem {@code 202}. */
@AutoConfigureMockMvc
class ImportControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String HEADER =
            "pedido_id;nsu;data_hora;valor_bruto;meio_pagamento;documento_cliente;tipo;descricao";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void analistaDeveFazerUploadESincronoDevolveResultadoCompleto() throws Exception {
        String accessToken = createUserAndLogin("import-http-ok@fincore.dev", UserRole.RECONCILIATION_ANALYST);
        String content = HEADER + "\nPED-HTTP-1;NSU1;10/09/2026 10:00:00;500,00;CREDITO;;VENDA;x\n";
        MockMultipartFile file = new MockMultipartFile("file", "vendas.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/imports")
                        .file(file)
                        .param("sourceCode", "INTERNAL_SALES")
                        .param("referenceDate", "2026-09-10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceCode").value("INTERNAL_SALES"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.totalLines").value(1))
                .andExpect(jsonPath("$.acceptedCount").value(1))
                .andExpect(jsonPath("$.rejectedCount").value(0));
    }

    @Test
    void auditorDeveReceber403NoUpload() throws Exception {
        String accessToken = createUserAndLogin("import-http-auditor@fincore.dev", UserRole.AUDITOR);
        MockMultipartFile file = new MockMultipartFile("file", "vendas.csv", "text/csv", (HEADER + "\n").getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/imports")
                        .file(file)
                        .param("sourceCode", "INTERNAL_SALES")
                        .param("referenceDate", "2026-09-10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void administradorDeveReceber403NoUpload() throws Exception {
        String accessToken = createUserAndLogin("import-http-admin@fincore.dev", UserRole.ADMINISTRATOR);
        MockMultipartFile file = new MockMultipartFile("file", "vendas.csv", "text/csv", (HEADER + "\n").getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/imports")
                        .file(file)
                        .param("sourceCode", "INTERNAL_SALES")
                        .param("referenceDate", "2026-09-10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void semAutenticacaoDeveReceber401NoUpload() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "vendas.csv", "text/csv", (HEADER + "\n").getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/imports")
                        .file(file)
                        .param("sourceCode", "INTERNAL_SALES")
                        .param("referenceDate", "2026-09-10"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void deveResponder409ParaArquivoDuplicadoViaHttp() throws Exception {
        String accessToken = createUserAndLogin("import-http-dup@fincore.dev", UserRole.RECONCILIATION_ANALYST);
        String content = HEADER + "\nPED-HTTP-DUP;NSU1;10/09/2026 10:00:00;500,00;CREDITO;;VENDA;x\n";
        MockMultipartFile file = new MockMultipartFile("file", "vendas.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/imports")
                        .file(file)
                        .param("sourceCode", "INTERNAL_SALES")
                        .param("referenceDate", "2026-10-01")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());

        MockMultipartFile file2 = new MockMultipartFile("file", "vendas.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/imports")
                        .file(file2)
                        .param("sourceCode", "INTERNAL_SALES")
                        .param("referenceDate", "2026-10-01")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_FILE"))
                .andExpect(jsonPath("$.originalImportBatchId").exists());
    }

    @Test
    void deveListarEBuscarPorIdEBaixarOArquivo() throws Exception {
        // AUDITOR não escreve, mas lê (TDS 20.2: "todos leem") — login próprio só para as
        // consultas, upload feito por um RECONCILIATION_ANALYST separado.
        String analystToken = createUserAndLogin("import-http-read-analyst@fincore.dev", UserRole.RECONCILIATION_ANALYST);
        String auditorToken = createUserAndLogin("import-http-read-auditor@fincore.dev", UserRole.AUDITOR);
        String content = HEADER + "\nPED-HTTP-READ;NSU1;10/09/2026 10:00:00;500,00;CREDITO;;VENDA;x\n";
        MockMultipartFile file = new MockMultipartFile("file", "vendas.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));

        MvcResult uploadResult = mockMvc.perform(multipart("/imports")
                        .file(file)
                        .param("sourceCode", "INTERNAL_SALES")
                        .param("referenceDate", "2026-10-02")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + analystToken))
                .andReturn();
        String batchId = objectMapper.readTree(uploadResult.getResponse().getContentAsString()).path("id").asText();

        mockMvc.perform(get("/imports").header(HttpHeaders.AUTHORIZATION, "Bearer " + auditorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());

        mockMvc.perform(get("/imports/" + batchId).header(HttpHeaders.AUTHORIZATION, "Bearer " + auditorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(batchId));

        mockMvc.perform(get("/imports/" + batchId + "/rejected-records").header(HttpHeaders.AUTHORIZATION, "Bearer " + auditorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());

        mockMvc.perform(get("/imports/" + batchId + "/file").header(HttpHeaders.AUTHORIZATION, "Bearer " + auditorToken))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().bytes(
                        content.getBytes(StandardCharsets.UTF_8)));
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
