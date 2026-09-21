package dev.fincore.ingestion.parser;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DocumentNormalizerTest {

    @Test
    void deveAceitarCpfValidoComMascara() {
        // CPF de teste com dígitos verificadores válidos.
        assertThat(DocumentNormalizer.normalize("111.444.777-35")).contains("11144477735");
    }

    @Test
    void deveAceitarCpfValidoSemMascara() {
        assertThat(DocumentNormalizer.normalize("11144477735")).contains("11144477735");
    }

    @Test
    void deveRejeitarCpfComDigitoVerificadorInvalido() {
        assertThat(DocumentNormalizer.normalize("11144477736")).isEmpty();
    }

    @Test
    void deveRejeitarCpfComTodosOsDigitosIguais() {
        assertThat(DocumentNormalizer.normalize("111.111.111-11")).isEmpty();
    }

    @Test
    void deveAceitarCnpjValidoComMascara() {
        assertThat(DocumentNormalizer.normalize("11.222.333/0001-81")).contains("11222333000181");
    }

    @Test
    void deveRejeitarCnpjComDigitoVerificadorInvalido() {
        assertThat(DocumentNormalizer.normalize("11.222.333/0001-82")).isEmpty();
    }

    @Test
    void deveRejeitarQuantidadeDeDigitosDesconhecida() {
        assertThat(DocumentNormalizer.normalize("12345")).isEmpty();
    }

    @Test
    void deveRejeitarVazioOuNulo() {
        assertThat(DocumentNormalizer.normalize("")).isEmpty();
        assertThat(DocumentNormalizer.normalize(null)).isEmpty();
    }
}
