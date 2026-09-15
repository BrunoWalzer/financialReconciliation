package dev.fincore.configuration.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/** {@link Source} — nasce ativo, com identificador gerado, sem valor financeiro (Domain §5; TDS 7.3). */
class SourceTest {

    @Test
    void deveCriarComIdentificadorGeradoEAtiva() {
        Source source = new Source(
                "INTERNAL_SALES", "Vendas Internas", "America/Sao_Paulo", ",", ".",
                List.of("dd/MM/yyyy"), RoundingMode.HALF_UP);

        assertThat(source.id()).isNotNull();
        assertThat(source.id().version()).isEqualTo(7);
        assertThat(source.active()).isTrue();
        assertThat(source.dateFormats()).containsExactly("dd/MM/yyyy");
    }

    @Test
    void deveRejeitarCodigoEmBranco() {
        assertThatThrownBy(() -> new Source(
                        " ", "Vendas Internas", "America/Sao_Paulo", ",", ".",
                        List.of("dd/MM/yyyy"), RoundingMode.HALF_UP))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("code");
    }

    @Test
    void deveRejeitarListaDeFormatosDeDataVazia() {
        assertThatThrownBy(() -> new Source(
                        "INTERNAL_SALES", "Vendas Internas", "America/Sao_Paulo", ",", ".",
                        List.of(), RoundingMode.HALF_UP))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dateFormats");
    }

    @Test
    void deveRejeitarRoundingModeNulo() {
        assertThatThrownBy(() -> new Source(
                        "INTERNAL_SALES", "Vendas Internas", "America/Sao_Paulo", ",", ".",
                        List.of("dd/MM/yyyy"), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void dateFormatsDeveSerDefensivo() {
        List<String> mutable = new java.util.ArrayList<>(List.of("dd/MM/yyyy"));
        Source source = new Source(
                "INTERNAL_SALES", "Vendas Internas", "America/Sao_Paulo", ",", ".",
                mutable, RoundingMode.HALF_UP);

        mutable.add("yyyy-MM-dd");

        assertThat(source.dateFormats()).containsExactly("dd/MM/yyyy");
        assertThatThrownBy(() -> source.dateFormats().add("outro"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
