package dev.fincore.evidence.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.fincore.AbstractIntegrationTest;
import dev.fincore.evidence.domain.Direction;
import dev.fincore.evidence.domain.FinancialRecord;
import dev.fincore.evidence.domain.RecordType;
import dev.fincore.evidence.infrastructure.FinancialRecordRepository;
import dev.fincore.identity.domain.AppUser;
import dev.fincore.identity.domain.UserRole;
import dev.fincore.identity.infrastructure.AppUserRepository;
import dev.fincore.shared.money.Currency;
import dev.fincore.shared.money.Money;
import fincore.testsupport.ImportBatchFixtures;
import java.time.Instant;
import java.time.LocalDate;
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

/** {@code GET|POST /records/{id}/annotations} de ponta a ponta (TDS 20.2). */
@AutoConfigureMockMvc
class RecordAnnotationControllerIntegrationTest extends AbstractIntegrationTest {

    private static final UUID INTERNAL_SALES_ID = UUID.fromString("00000000-0000-7000-8000-000000000101");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FinancialRecordRepository financialRecordRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void analistaDeveCriarAnotacaoViaHttp() throws Exception {
        FinancialRecord record = saveRecord();
        String accessToken = createAnalystAndLogin("annotation-http-create@fincore.dev");

        mockMvc.perform(post("/records/" + record.id() + "/annotations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("text", "confirmado com o cliente"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.text").value("confirmado com o cliente"))
                .andExpect(jsonPath("$.financialRecordId").value(record.id().toString()));
    }

    @Test
    void auditorDeveReceber403AoCriar() throws Exception {
        FinancialRecord record = saveRecord();
        String accessToken = createAuditorAndLogin("annotation-http-forbidden@fincore.dev");

        mockMvc.perform(post("/records/" + record.id() + "/annotations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("text", "tentativa"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void deveResponder400ComTextoEmBrancoViaHttp() throws Exception {
        FinancialRecord record = saveRecord();
        String accessToken = createAnalystAndLogin("annotation-http-blank@fincore.dev");

        mockMvc.perform(post("/records/" + record.id() + "/annotations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("text", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @WithMockUser(authorities = "AUDITOR")
    void deveListarAnotacoesViaHttp() throws Exception {
        FinancialRecord record = saveRecord();
        String accessToken = createAnalystAndLogin("annotation-http-list@fincore.dev");
        mockMvc.perform(post("/records/" + record.id() + "/annotations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("text", "primeira anotação"))));

        mockMvc.perform(get("/records/" + record.id() + "/annotations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].text").value("primeira anotação"));
    }

    private FinancialRecord saveRecord() {
        UUID importBatchId = ImportBatchFixtures.insertMinimal(jdbc, INTERNAL_SALES_ID);
        return financialRecordRepository.save(new FinancialRecord(
                INTERNAL_SALES_ID, importBatchId, 1, null, null, Direction.CREDIT, RecordType.SALE,
                new Money(500, Currency.BRL), null, null, LocalDate.of(2026, 9, 10), null, null, null, null, null,
                "linha", "i9".repeat(32), Instant.now()));
    }

    private String createAnalystAndLogin(String email) throws Exception {
        return createUserAndLogin(email, UserRole.RECONCILIATION_ANALYST);
    }

    private String createAuditorAndLogin(String email) throws Exception {
        return createUserAndLogin(email, UserRole.AUDITOR);
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
