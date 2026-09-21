package dev.fincore.evidence.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.fincore.AbstractIntegrationTest;
import dev.fincore.evidence.domain.Direction;
import dev.fincore.evidence.domain.FinancialRecord;
import dev.fincore.evidence.domain.RecordType;
import dev.fincore.evidence.infrastructure.FinancialRecordRepository;
import dev.fincore.shared.money.Currency;
import dev.fincore.shared.money.Money;
import fincore.testsupport.ImportBatchFixtures;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/** {@code GET /records} · {@code GET /records/{id}} de ponta a ponta (TDS 20.2). */
@AutoConfigureMockMvc
class FinancialRecordControllerIntegrationTest extends AbstractIntegrationTest {

    private static final UUID INTERNAL_SALES_ID = UUID.fromString("00000000-0000-7000-8000-000000000101");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FinancialRecordRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @WithMockUser(authorities = "AUDITOR")
    void deveBuscarPorSourceCodeERetornarMoneyComoMinorEMoeda() throws Exception {
        UUID importBatchId = ImportBatchFixtures.insertMinimal(jdbc, INTERNAL_SALES_ID);
        FinancialRecord record = repository.save(new FinancialRecord(
                INTERNAL_SALES_ID, importBatchId, 1, "PED-HTTP-" + UUID.randomUUID(), null, Direction.CREDIT,
                RecordType.SALE, new Money(49_000, Currency.BRL), null, null, LocalDate.of(2026, 9, 10), null,
                null, null, null, null, "linha", "g7".repeat(32), Instant.now()));

        mockMvc.perform(get("/records")
                        .param("sourceCode", "INTERNAL_SALES")
                        .param("externalId", record.externalId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.content[0].grossAmount.minor").value(49_000))
                .andExpect(jsonPath("$.content[0].grossAmount.currency").value("BRL"));
    }

    @Test
    @WithMockUser(authorities = "AUDITOR")
    void deveResponder400ParaSourceCodeDesconhecido() throws Exception {
        mockMvc.perform(get("/records").param("sourceCode", "NAO_EXISTE"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void semAutenticacaoDeveReceber401() throws Exception {
        mockMvc.perform(get("/records"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @WithMockUser(authorities = "RECONCILIATION_ANALYST")
    void deveBuscarPorIdComTodosOsCampos() throws Exception {
        UUID importBatchId = ImportBatchFixtures.insertMinimal(jdbc, INTERNAL_SALES_ID);
        FinancialRecord record = repository.save(new FinancialRecord(
                INTERNAL_SALES_ID, importBatchId, 3, null, "NSU-DETAIL", Direction.CREDIT, RecordType.SALE,
                new Money(1_000, Currency.BRL), null, null, LocalDate.of(2026, 9, 11), null, "12345678900",
                "PIX", "descrição", "DESCRICAO", "linha bruta original", "h8".repeat(32), Instant.now()));

        mockMvc.perform(get("/records/" + record.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(record.id().toString()))
                .andExpect(jsonPath("$.lineNumber").value(3))
                .andExpect(jsonPath("$.correlationKey").value("NSU-DETAIL"))
                .andExpect(jsonPath("$.direction").value("CREDIT"))
                .andExpect(jsonPath("$.recordType").value("SALE"))
                .andExpect(jsonPath("$.rawLine").value("linha bruta original"))
                .andExpect(jsonPath("$.flags", org.hamcrest.Matchers.hasSize(0)));
    }

    @Test
    @WithMockUser(authorities = "AUDITOR")
    void flagsDeIntegridadeAparecemNoDetalheDoRegistro() throws Exception {
        // Implementation Plan M7: "Flags aparecem em GET /records/{id}" — não na listagem.
        UUID importBatchId = ImportBatchFixtures.insertMinimal(jdbc, INTERNAL_SALES_ID);
        FinancialRecord record = repository.save(new FinancialRecord(
                INTERNAL_SALES_ID, importBatchId, 1, "PED-FLAG-HTTP-" + UUID.randomUUID(), null, Direction.CREDIT,
                RecordType.SALE, new Money(500, Currency.BRL), null, null, LocalDate.of(2026, 9, 10), null,
                null, null, null, null, "linha", "j9".repeat(32), Instant.now()));
        jdbc.update(
                "insert into record_integrity_flag (id, financial_record_id, flag_type, detected_at, detected_by_batch_id) "
                        + "values (?, ?, 'POSSIBLE_DUPLICATE', ?, ?)",
                UUID.randomUUID(), record.id(), java.sql.Timestamp.from(Instant.now()), importBatchId);

        mockMvc.perform(get("/records/" + record.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flags", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.flags[0].flagType").value("POSSIBLE_DUPLICATE"));
    }

    @Test
    @WithMockUser(authorities = "AUDITOR")
    void deveResponder404ParaIdInexistente() throws Exception {
        mockMvc.perform(get("/records/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }
}
