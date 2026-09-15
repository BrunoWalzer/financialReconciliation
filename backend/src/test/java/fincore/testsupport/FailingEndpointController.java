package fincore.testsupport;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint que falha de proposito, para provar o tratamento de excecao nao prevista.
 *
 * <p>Mora fora de {@code dev.fincore} de proposito: o component scan da aplicacao cobre a
 * raiz {@code dev.fincore}, e uma classe de teste anotada com {@code @RestController}
 * dentro dela seria registrada em todo {@code @SpringBootTest}. Daqui, ela so existe onde
 * for importada explicitamente.
 */
@RestController
public class FailingEndpointController {

    public static final String PATH = "/internal-test/boom";

    /** A mensagem imita o que nunca pode vazar: SQL e nome de tabela. */
    @GetMapping(PATH)
    public String boom() {
        throw new IllegalStateException(
                "select * from financial_record where id = 42 -- violates fincore_record_immutable");
    }
}
