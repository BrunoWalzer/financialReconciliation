package dev.fincore.audit.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** {@link ActorRef} — o VO que identifica quem agiu (FD-7). */
class ActorRefTest {

    @Test
    void deveCriarAtorUsuarioComRotuloDenormalizado() {
        UUID userId = UUID.randomUUID();

        ActorRef actor = ActorRef.user(userId, "ana@fincore.dev");

        assertThat(actor.actorType()).isEqualTo(ActorType.USER);
        assertThat(actor.actorUserId()).isEqualTo(userId);
        assertThat(actor.actorLabel()).isEqualTo("ana@fincore.dev");
        assertThat(actor.actorRunId()).isNull();
    }

    @Test
    void deveCriarAtorSistemaSemUsuario() {
        ActorRef actor = ActorRef.system();

        assertThat(actor.actorType()).isEqualTo(ActorType.SYSTEM);
        assertThat(actor.actorUserId()).isNull();
        assertThat(actor.actorLabel()).isEqualTo("SYSTEM");
    }

    @Test
    void deveCriarAtorSistemaComExecucao() {
        UUID runId = UUID.randomUUID();

        ActorRef actor = ActorRef.system(runId);

        assertThat(actor.actorRunId()).isEqualTo(runId);
    }

    @Test
    void deveRejeitarAtorUsuarioSemIdentificador() {
        assertThatThrownBy(() -> new ActorRef(ActorType.USER, null, "ana@fincore.dev", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("actorUserId");
    }

    @Test
    void deveRejeitarRotuloVazio() {
        assertThatThrownBy(() -> new ActorRef(ActorType.SYSTEM, null, "  ", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("actorLabel");
    }

    @Test
    void deveRejeitarTipoDeAtorNulo() {
        assertThatThrownBy(() -> new ActorRef(null, null, "SYSTEM", null))
                .isInstanceOf(NullPointerException.class);
    }
}
