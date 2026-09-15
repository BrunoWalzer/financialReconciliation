package dev.fincore.configuration.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.fincore.shared.identifier.Uuid7;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** {@link SourcePair} — duas fontes distintas cuja evidência será conciliada (TDS 7.3). */
class SourcePairTest {

    @Test
    void deveCriarComIdentificadorGerado() {
        UUID left = Uuid7.generate();
        UUID right = Uuid7.generate();

        SourcePair pair = new SourcePair(left, right, "INTERNAL_SALES_X_ACQUIRER_SETTLEMENT");

        assertThat(pair.id()).isNotNull();
        assertThat(pair.leftSourceId()).isEqualTo(left);
        assertThat(pair.rightSourceId()).isEqualTo(right);
    }

    @Test
    void deveRejeitarLadosIguais() {
        UUID same = Uuid7.generate();

        assertThatThrownBy(() -> new SourcePair(same, same, "QUALQUER"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("distintas");
    }

    @Test
    void deveRejeitarCodigoEmBranco() {
        assertThatThrownBy(() -> new SourcePair(Uuid7.generate(), Uuid7.generate(), " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("code");
    }

    @Test
    void deveRejeitarLadosNulos() {
        UUID right = Uuid7.generate();

        assertThatThrownBy(() -> new SourcePair(null, right, "QUALQUER"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SourcePair(right, null, "QUALQUER"))
                .isInstanceOf(NullPointerException.class);
    }
}
