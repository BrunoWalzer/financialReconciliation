package dev.fincore.shared.identifier;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** {@link Uuid7} precisa produzir UUID versão 7, variante RFC 4122, sem colisão. */
class Uuid7Test {

    @Test
    void deveGerarUuidDeVersao7() {
        UUID id = Uuid7.generate();

        assertThat(id.version()).isEqualTo(7);
    }

    @Test
    void deveGerarUuidDeVarianteRfc4122() {
        UUID id = Uuid7.generate();

        assertThat(id.variant()).isEqualTo(2);
    }

    @Test
    void deveGerarIdentificadoresUnicosEmGrandeVolume() {
        Set<UUID> generated = new HashSet<>();

        IntStream.range(0, 10_000).forEach(i -> generated.add(Uuid7.generate()));

        assertThat(generated).hasSize(10_000);
    }

    @Test
    void deveCrescerOrdenadoNoTempoEmGrandeVolume() {
        // Sem depender do relógio real de forma arriscada (regra 34): não mede intervalo
        // nenhum, só confere que a sequência gerada nunca decresce — a propriedade que
        // justifica UUID v7 sobre v4 para localidade de índice (TDS 7).
        //
        // Só a fração de timestamp (os 48 bits mais altos do msb) precisa ser não
        // decrescente. Os 16 bits mais baixos do msb são versão + rand_a: dois UUIDs do
        // mesmo milissegundo têm essa parte aleatória, sem ordem relativa garantida entre
        // si — é o que o Javadoc de Uuid7 documenta, e comparar o msb inteiro pegaria
        // exatamente esse caso e falharia por engano.
        List<UUID> generated = IntStream.range(0, 5_000).mapToObj(i -> Uuid7.generate()).toList();

        for (int i = 1; i < generated.size(); i++) {
            assertThat(timestampPortion(generated.get(i))).isGreaterThanOrEqualTo(timestampPortion(generated.get(i - 1)));
        }
    }

    private static long timestampPortion(UUID uuid) {
        return uuid.getMostSignificantBits() >>> 16;
    }
}
