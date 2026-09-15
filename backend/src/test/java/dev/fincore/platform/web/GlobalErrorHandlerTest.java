package dev.fincore.platform.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fincore.testsupport.FailingEndpointController;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Formato unico de erro da API: RFC 9457, com {@code code} e {@code correlationId},
 * e sem nada do lado de dentro (TDS 20).
 *
 * <p>{@code excludeAutoConfiguration} (M2): sem isso, a fatia {@code @WebMvcTest}
 * auto-configuraria a segurança padrão do Spring Boot e exigiria autenticação antes que a
 * requisição alcançasse {@link FailingEndpointController}. {@code controllers =
 * FailingEndpointController.class} restringe a fatia a esse único controller — sem isso,
 * ela instanciaria todo {@code @RestController} do projeto, inclusive os de
 * {@code identity}/{@code audit}, que dependem de casos de uso que esta fatia não provê.
 */
@WebMvcTest(
        controllers = FailingEndpointController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
@ActiveProfiles("test")
@Import(FailingEndpointController.class)
class GlobalErrorHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void deveResponder404EmFormatoRfc9457QuandoARotaNaoExiste() throws Exception {
        mockMvc.perform(get("/rota-que-nao-existe"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("https://fincore.dev/errors/resource-not-found"))
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.instance").value("/rota-que-nao-existe"))
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @Test
    void deveResponder405QuandoOMetodoNaoEPermitido() throws Exception {
        mockMvc.perform(post(FailingEndpointController.PATH))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @Test
    void deveResponder500SemVazarOInternoQuandoUmaExcecaoNaoEPrevista() throws Exception {
        String body = mockMvc.perform(get(FailingEndpointController.PATH))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Assertions.assertThat(body)
                .as("o corpo nao carrega stack trace, SQL, nome de tabela nem de constraint")
                .doesNotContain("IllegalStateException")
                .doesNotContain("financial_record")
                .doesNotContain("fincore_record_immutable")
                .doesNotContain("select *")
                .doesNotContain("dev.fincore")
                .doesNotContain("at java.");
    }
}
