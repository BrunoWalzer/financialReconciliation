package dev.fincore.ingestion.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.fincore.AbstractIntegrationTest;
import dev.fincore.shared.identifier.Uuid7;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Limite de linhas (Implementation Plan FD-10, OD-5: 500 mil, "rejeitados antes de
 * qualquer parsing" — TDS 9.1). Limite baixo só neste teste, via propriedade dinâmica, para
 * não gerar um arquivo de centenas de milhares de linhas na suíte (seção 31 do prompt do
 * M5 permite isso quando o teste no limite real pesaria demais).
 */
class ImportFileUseCaseLimitsIntegrationTest extends AbstractIntegrationTest {

    @DynamicPropertySource
    static void lowLineLimit(DynamicPropertyRegistry registry) {
        registry.add("fincore.ingestion.max-upload-lines", () -> 5);
    }

    @Autowired
    private ImportFileUseCase importFileUseCase;

    @Test
    @WithMockUser(authorities = "RECONCILIATION_ANALYST")
    void deveRejeitarArquivoAcimaDoLimiteDeLinhasAntesDeQualquerParsing() {
        StringBuilder content = new StringBuilder(
                "pedido_id;nsu;data_hora;valor_bruto;meio_pagamento;documento_cliente;tipo;descricao\n");
        for (int i = 0; i < 10; i++) {
            content.append("PED-").append(i).append(";NSU").append(i)
                    .append(";10/09/2026 10:00:00;500,00;CREDITO;;VENDA;x\n");
        }

        ImportFileCommand command = new ImportFileCommand(
                "INTERNAL_SALES", "grande-" + Uuid7.generate() + ".csv",
                content.toString().getBytes(StandardCharsets.UTF_8), LocalDate.of(2026, 9, 30), null, null);

        assertThatThrownBy(() -> importFileUseCase.execute(command, Uuid7.generate(), "analista@fincore.dev"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limite");
    }
}
