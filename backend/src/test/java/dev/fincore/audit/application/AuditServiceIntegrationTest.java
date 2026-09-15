package dev.fincore.audit.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.fincore.AbstractIntegrationTest;
import dev.fincore.audit.domain.ActorRef;
import dev.fincore.audit.domain.AuditEvent;
import dev.fincore.audit.infrastructure.AuditEventRepository;
import dev.fincore.shared.correlation.CorrelationId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@link AuditService} contra PostgreSQL real: persistência, ator, correlação e
 * participação na transação do chamador (TDS 16.1, critério de aceite 3 do M1).
 */
class AuditServiceIntegrationTest extends AbstractIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private AuditService auditService;

    @Autowired
    private AuditEventRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void devePersistirUmEventoValido() {
        UUID entityId = UUID.randomUUID();
        AuditEventRequest request = AuditEventRequest.of(ActorRef.system(), "TEST_EVENT", "SOME_ENTITY", entityId);

        AuditEvent recorded = auditService.record(request);

        Optional<AuditEvent> found = repository.findById(recorded.id());
        assertThat(found).isPresent();
        assertThat(found.get().action()).isEqualTo("TEST_EVENT");
        assertThat(found.get().entityType()).isEqualTo("SOME_ENTITY");
        assertThat(found.get().entityId()).isEqualTo(entityId);
    }

    @Test
    void devePersistirEventoDeAtorSistemaSemUsuario() {
        AuditEvent recorded = auditService.record(AuditEventRequest.of(ActorRef.system(), "TEST_EVENT", null, null));

        AuditEvent found = repository.findById(recorded.id()).orElseThrow();

        assertThat(found.actor().actorType()).isEqualTo(dev.fincore.audit.domain.ActorType.SYSTEM);
        assertThat(found.actor().actorUserId()).isNull();
        assertThat(found.actor().actorLabel()).isEqualTo("SYSTEM");
    }

    @Test
    void devePersistirEventoDeAtorUsuarioComIdentificador() {
        UUID userId = UUID.randomUUID();
        ActorRef actor = ActorRef.user(userId, "ana@fincore.dev");

        AuditEvent recorded = auditService.record(AuditEventRequest.of(actor, "TEST_EVENT", null, null));

        AuditEvent found = repository.findById(recorded.id()).orElseThrow();
        assertThat(found.actor()).isEqualTo(actor);
    }

    @Test
    void deveAceitarEstadoAntesEDepoisNulos() {
        AuditEvent recorded = auditService.record(AuditEventRequest.of(ActorRef.system(), "TEST_EVENT", null, null));

        AuditEvent found = repository.findById(recorded.id()).orElseThrow();

        assertThat(found.beforeState()).isNull();
        assertThat(found.afterState()).isNull();
    }

    @Test
    void deveAceitarEstadoAntesEDepoisNaoNulos() throws Exception {
        AuditEventRequest request = new AuditEventRequest(
                ActorRef.system(),
                "TEST_EVENT",
                "SOME_ENTITY",
                UUID.randomUUID(),
                "{\"status\":\"OLD\"}",
                "{\"status\":\"NEW\"}",
                null,
                null,
                null);

        AuditEvent recorded = auditService.record(request);

        AuditEvent found = repository.findById(recorded.id()).orElseThrow();
        // jsonb é um formato binário normalizado, não texto literal: o Postgres pode
        // devolver "{"status": "OLD"}" (com espaço) para a mesma entrada — comparar a
        // árvore JSON, não a string bruta, é o jeito correto de verificar o round-trip.
        assertThat(JSON.readTree(found.beforeState())).isEqualTo(JSON.readTree("{\"status\":\"OLD\"}"));
        assertThat(JSON.readTree(found.afterState())).isEqualTo(JSON.readTree("{\"status\":\"NEW\"}"));
    }

    @Test
    void devePersistirEnderecoIpQuandoInformado() {
        AuditEventRequest request = new AuditEventRequest(
                ActorRef.system(), "TEST_EVENT", null, null, null, null, null, null, "203.0.113.10");

        AuditEvent recorded = auditService.record(request);

        AuditEvent found = repository.findById(recorded.id()).orElseThrow();
        assertThat(found.ipAddress()).isEqualTo("203.0.113.10");
    }

    @Test
    void devePreservarOCorrelationIdExplicito() {
        AuditEventRequest request = new AuditEventRequest(
                ActorRef.system(), "TEST_EVENT", null, null, null, null, null, "correlacao-explicita-001", null);

        AuditEvent recorded = auditService.record(request);

        assertThat(recorded.correlationId()).isEqualTo("correlacao-explicita-001");
    }

    @Test
    void deveUsarOCorrelationIdDaRequisicaoEmCursoQuandoNaoInformado() {
        MDC.put(CorrelationId.MDC_KEY, "correlacao-do-mdc-002");
        try {
            AuditEvent recorded =
                    auditService.record(AuditEventRequest.of(ActorRef.system(), "TEST_EVENT", null, null));

            assertThat(recorded.correlationId()).isEqualTo("correlacao-do-mdc-002");
        } finally {
            MDC.remove(CorrelationId.MDC_KEY);
        }
    }

    @Test
    void deveDeixarCorrelationIdNuloQuandoNaoInformadoENaoHaRequisicaoEmCurso() {
        AuditEvent recorded = auditService.record(AuditEventRequest.of(ActorRef.system(), "TEST_EVENT", null, null));

        assertThat(recorded.correlationId()).isNull();
    }

    @Test
    void naoDeveDeixarEventoQuandoATransacaoDoChamadorReverte() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        UUID[] recordedId = new UUID[1];

        transactionTemplate.execute(status -> {
            AuditEvent event = auditService.record(AuditEventRequest.of(ActorRef.system(), "TEST_EVENT", null, null));
            recordedId[0] = event.id();
            status.setRollbackOnly();
            return null;
        });

        assertThat(repository.findById(recordedId[0]))
                .as("auditoria precisa participar da transacao do chamador (TDS 16.1) — revertida a transacao, o evento nao pode sobreviver")
                .isEmpty();
    }

    @Test
    void deveManterOEventoQuandoATransacaoDoChamadorConfirma() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        UUID[] recordedId = new UUID[1];

        transactionTemplate.execute(status -> {
            AuditEvent event = auditService.record(AuditEventRequest.of(ActorRef.system(), "TEST_EVENT", null, null));
            recordedId[0] = event.id();
            return null;
        });

        assertThat(repository.findById(recordedId[0])).isPresent();
    }
}
