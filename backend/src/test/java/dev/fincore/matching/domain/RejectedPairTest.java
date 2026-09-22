package dev.fincore.matching.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * A ordem canônica precisa concordar com {@code CHECK (record_a_id < record_b_id)} do
 * PostgreSQL (I-10, V7) — bug real encontrado por um teste de integração intermitente
 * contra Postgres real (ver relatório do M9, seção Desvios).
 */
class RejectedPairTest {

    @Test
    void ordemCanonicaConcordaComAOrdenacaoNativaDoTipoUuidDoPostgres() {
        // UUID.compareTo() do Java compara mostSigBits como long COM sinal; o tipo uuid do
        // PostgreSQL compara os 16 bytes SEM sinal. Este par discorda entre as duas: o byte
        // mais significativo de "x" tem o bit alto ligado (0x80), o que o Java enxerga como
        // um long negativo (portanto "menor"), mas o Postgres, comparando bytes sem sinal,
        // enxerga como o MAIOR dos dois.
        UUID x = UUID.fromString("80000000-0000-0000-0000-000000000000");
        UUID y = UUID.fromString("00000000-0000-0001-0000-000000000000");

        assertThat(x.compareTo(y)).as("UUID.compareTo() do Java diz que x < y").isLessThan(0);

        // A ordem canônica de RejectedPair precisa ser a do Postgres (x > y), não a do Java
        // — ou seja, a(y) < b(x).
        RejectedPair pair = RejectedPair.of(x, y);

        assertThat(pair.a()).isEqualTo(y);
        assertThat(pair.b()).isEqualTo(x);
    }

    @Test
    void ordemCanonicaEEstavelIndependenteDeQualLadoEPassadoPrimeiro() {
        UUID x = UUID.fromString("80000000-0000-0000-0000-000000000000");
        UUID y = UUID.fromString("00000000-0000-0001-0000-000000000000");

        assertThat(RejectedPair.of(x, y)).isEqualTo(RejectedPair.of(y, x));
    }

    @Test
    void doisUuidsIguaisFormamUmParTrivial() {
        UUID id = UUID.randomUUID();

        RejectedPair pair = RejectedPair.of(id, id);

        assertThat(pair.a()).isEqualTo(id);
        assertThat(pair.b()).isEqualTo(id);
    }
}
