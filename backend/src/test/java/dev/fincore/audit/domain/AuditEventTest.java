package dev.fincore.audit.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * {@link AuditEvent} sem Spring nem banco: só a construção do agregado e a garantia de
 * que nenhum método permite alterar um campo depois de criado.
 */
class AuditEventTest {

    @Test
    void deveGerarIdentificadorEInstanteNaConstrucao() {
        Instant before = Instant.now();

        AuditEvent event = new AuditEvent(
                ActorRef.system(), "TEST_EVENT", null, null, null, null, null, null, null);

        Instant after = Instant.now();

        assertThat(event.id()).isNotNull();
        assertThat(event.id().version()).isEqualTo(7);
        assertThat(event.occurredAt()).isBetween(before, after);
    }

    @Test
    void deveReconstruirOAtorAPartirDosCamposDenormalizados() {
        UUID userId = UUID.randomUUID();
        ActorRef actor = ActorRef.user(userId, "ana@fincore.dev");

        AuditEvent event = new AuditEvent(actor, "TEST_EVENT", null, null, null, null, null, null, null);

        assertThat(event.actor()).isEqualTo(actor);
    }

    @Test
    void devePreservarEntidadeAfetadaQuandoInformada() {
        UUID entityId = UUID.randomUUID();

        AuditEvent event = new AuditEvent(
                ActorRef.system(), "TEST_EVENT", "SOME_ENTITY", entityId, null, null, null, null, null);

        assertThat(event.entityType()).isEqualTo("SOME_ENTITY");
        assertThat(event.entityId()).isEqualTo(entityId);
    }

    @Test
    void devePreservarEstadoAntesEDepoisQuandoInformados() {
        AuditEvent event = new AuditEvent(
                ActorRef.system(),
                "TEST_EVENT",
                null,
                null,
                "{\"status\":\"OLD\"}",
                "{\"status\":\"NEW\"}",
                null,
                null,
                null);

        assertThat(event.beforeState()).isEqualTo("{\"status\":\"OLD\"}");
        assertThat(event.afterState()).isEqualTo("{\"status\":\"NEW\"}");
    }

    @Test
    void deveAceitarEstadoAntesEDepoisNulos() {
        AuditEvent event = new AuditEvent(
                ActorRef.system(), "TEST_EVENT", null, null, null, null, null, null, null);

        assertThat(event.beforeState()).isNull();
        assertThat(event.afterState()).isNull();
    }

    @Test
    void deveRejeitarAcaoEmBranco() {
        assertThatThrownBy(() -> new AuditEvent(
                        ActorRef.system(), "  ", null, null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("action");
    }

    @Test
    void deveRejeitarAtorNulo() {
        assertThatThrownBy(() -> new AuditEvent(null, "TEST_EVENT", null, null, null, null, null, null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void deveGerarIdentificadoresDiferentesParaEventosDiferentes() {
        AuditEvent first = new AuditEvent(ActorRef.system(), "TEST_EVENT", null, null, null, null, null, null, null);
        AuditEvent second = new AuditEvent(ActorRef.system(), "TEST_EVENT", null, null, null, null, null, null, null);

        assertThat(first.id()).isNotEqualTo(second.id());
    }
}
