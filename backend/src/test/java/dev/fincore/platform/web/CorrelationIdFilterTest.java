package dev.fincore.platform.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.fincore.shared.correlation.CorrelationId;
import fincore.testsupport.FailingEndpointController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * O identificador de correlacao atravessa requisicao, resposta e corpo de erro (TDS 26).
 *
 * <p>{@code excludeAutoConfiguration} (M2): sem isso, a fatia {@code @WebMvcTest}
 * auto-configuraria a segurança padrão do Spring Boot (toda rota autenticada), que
 * bloquearia a requisição antes de chegar ao {@code GlobalErrorHandler} — esta suíte
 * testa propagação de correlação, não autenticação. {@code addFilters = false} não serve
 * aqui: ele desligaria também o próprio {@code CorrelationIdFilter}, que é um filtro
 * comum, não parte da cadeia do Spring Security.
 *
 * <p>{@code controllers = FailingEndpointController.class} restringe a fatia a um único
 * controller arbitrário — sem isso, ela instanciaria todo {@code @RestController} do
 * projeto, inclusive os que dependem de beans que esta fatia não provê.
 */
@WebMvcTest(
        controllers = FailingEndpointController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
@ActiveProfiles("test")
class CorrelationIdFilterTest {

    private static final String ANY_PATH = "/rota-que-nao-existe";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void deveEcoarOIdentificadorQuandoOClienteEnviaUmValido() throws Exception {
        String sent = "01J9Z2K8QABCDEF0123456789";

        String body = mockMvc.perform(get(ANY_PATH).header(CorrelationId.HEADER, sent))
                .andExpect(header().string(CorrelationId.HEADER, sent))
                .andExpect(jsonPath("$.correlationId").value(sent))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).contains(sent);
    }

    @Test
    void deveGerarUmIdentificadorQuandoOClienteNaoEnviaNenhum() throws Exception {
        String generated = mockMvc.perform(get(ANY_PATH))
                .andExpect(header().exists(CorrelationId.HEADER))
                .andReturn()
                .getResponse()
                .getHeader(CorrelationId.HEADER);

        assertThat(generated).isNotBlank();
        assertThat(CorrelationId.isAcceptable(generated)).isTrue();
    }

    @Test
    void deveGerarIdentificadorDiferenteParaCadaRequisicao() throws Exception {
        String first = correlationIdOfANewRequest();
        String second = correlationIdOfANewRequest();

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void deveDescartarOIdentificadorRecebidoQuandoEleNaoTemFormatoAceito() throws Exception {
        // Vai para log e para cabecalho de resposta: aceitar texto arbitrario abriria
        // injecao de log e de cabecalho.
        String malicious = "abc\r\nX-Injected: 1";

        String echoed = mockMvc.perform(get(ANY_PATH).header(CorrelationId.HEADER, malicious))
                .andReturn()
                .getResponse()
                .getHeader(CorrelationId.HEADER);

        assertThat(echoed).isNotEqualTo(malicious);
        assertThat(CorrelationId.isAcceptable(echoed)).isTrue();
    }

    @Test
    void deveIncluirOIdentificadorNoCorpoDeErro() throws Exception {
        var response = mockMvc.perform(get(ANY_PATH)).andReturn().getResponse();

        String fromHeader = response.getHeader(CorrelationId.HEADER);
        String fromBody = objectMapper.readTree(response.getContentAsString()).path("correlationId").asText();

        assertThat(fromBody).isEqualTo(fromHeader);
    }

    private String correlationIdOfANewRequest() throws Exception {
        return mockMvc.perform(get(ANY_PATH)).andReturn().getResponse().getHeader(CorrelationId.HEADER);
    }
}
