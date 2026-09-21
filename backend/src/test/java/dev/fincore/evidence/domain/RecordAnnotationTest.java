package dev.fincore.evidence.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.fincore.shared.identifier.Uuid7;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RecordAnnotationTest {

    private static final Instant NOW = Instant.parse("2026-09-11T12:00:00Z");

    @Test
    void deveCriarComIdentificadorGerado() {
        RecordAnnotation annotation = new RecordAnnotation(Uuid7.generate(), Uuid7.generate(), "confirmado com o cliente", NOW);

        assertThat(annotation.id()).isNotNull();
        assertThat(annotation.text()).isEqualTo("confirmado com o cliente");
        assertThat(annotation.createdAt()).isEqualTo(NOW);
    }

    @Test
    void deveRejeitarTextoEmBranco() {
        assertThatThrownBy(() -> new RecordAnnotation(Uuid7.generate(), Uuid7.generate(), " ", NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deveRejeitarFinancialRecordIdNulo() {
        assertThatThrownBy(() -> new RecordAnnotation(null, Uuid7.generate(), "texto", NOW))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void deveRejeitarAuthorIdNulo() {
        assertThatThrownBy(() -> new RecordAnnotation(Uuid7.generate(), null, "texto", NOW))
                .isInstanceOf(NullPointerException.class);
    }
}
