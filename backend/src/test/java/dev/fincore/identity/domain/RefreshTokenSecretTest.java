package dev.fincore.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** {@link RefreshTokenSecret} — geração e hash do segredo opaco (TDS 21.1). */
class RefreshTokenSecretTest {

    @Test
    void deveGerarSegredosUnicos() {
        Set<String> generated = new HashSet<>();
        IntStream.range(0, 1_000).forEach(i -> generated.add(RefreshTokenSecret.generate()));

        assertThat(generated).hasSize(1_000);
    }

    @Test
    void deveGerarOMesmoHashParaOMesmoSegredo() {
        String secret = RefreshTokenSecret.generate();

        assertThat(RefreshTokenSecret.hash(secret)).isEqualTo(RefreshTokenSecret.hash(secret));
    }

    @Test
    void deveGerarHashesDiferentesParaSegredosDiferentes() {
        String first = RefreshTokenSecret.generate();
        String second = RefreshTokenSecret.generate();

        assertThat(RefreshTokenSecret.hash(first)).isNotEqualTo(RefreshTokenSecret.hash(second));
    }

    @Test
    void oHashNuncaEIgualAoSegredoEmClaro() {
        String secret = RefreshTokenSecret.generate();

        assertThat(RefreshTokenSecret.hash(secret)).isNotEqualTo(secret);
    }
}
