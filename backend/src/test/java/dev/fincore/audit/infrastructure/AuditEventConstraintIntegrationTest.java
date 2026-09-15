package dev.fincore.audit.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.fincore.AbstractIntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * {@code audit_event} ao nível do banco: estrutura, CHECK constraints e o trigger de
 * imutabilidade (I-2) — tudo executado como SQL real contra PostgreSQL via
 * Testcontainers. Nenhuma dessas garantias é simulada em Java: se a migration ou o
 * trigger forem removidos, é aqui que a suíte quebra.
 */
class AuditEventConstraintIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void deveExporAsColunasEssenciaisComATipagemCorreta() {
        List<Map<String, Object>> columns = jdbc.queryForList(
                """
                select column_name, data_type, is_nullable
                  from information_schema.columns
                 where table_schema = 'public' and table_name = 'audit_event'
                """);

        Map<String, Map<String, Object>> byName =
                columns.stream().collect(Collectors.toMap(c -> (String) c.get("column_name"), c -> c));

        assertThat(byName)
                .as("todas as colunas da TDS 7.2 precisam existir")
                .containsKeys(
                        "id", "occurred_at", "actor_type", "actor_user_id", "actor_label", "actor_run_id",
                        "action", "entity_type", "entity_id", "before_state", "after_state",
                        "justification", "correlation_id", "ip_address");

        assertColumn(byName, "id", "uuid", "NO");
        assertColumn(byName, "occurred_at", "timestamp with time zone", "NO");
        assertColumn(byName, "actor_type", "text", "NO");
        assertColumn(byName, "actor_user_id", "uuid", "YES");
        assertColumn(byName, "actor_label", "text", "NO");
        assertColumn(byName, "actor_run_id", "uuid", "YES");
        assertColumn(byName, "action", "text", "NO");
        assertColumn(byName, "entity_type", "text", "YES");
        assertColumn(byName, "entity_id", "uuid", "YES");
        assertColumn(byName, "before_state", "jsonb", "YES");
        assertColumn(byName, "after_state", "jsonb", "YES");
        assertColumn(byName, "justification", "text", "YES");
        assertColumn(byName, "correlation_id", "text", "YES");
        assertColumn(byName, "ip_address", "inet", "YES");
    }

    private static void assertColumn(
            Map<String, Map<String, Object>> columns, String name, String expectedType, String expectedNullable) {
        Map<String, Object> column = columns.get(name);
        assertThat(column.get("data_type")).as("tipo de " + name).isEqualTo(expectedType);
        assertThat(column.get("is_nullable")).as("nulidade de " + name).isEqualTo(expectedNullable);
    }

    @Test
    void deveInserirUmEventoValido() {
        UUID id = insertMinimalEvent("SYSTEM", null, "SYSTEM", "TEST_EVENT");

        Integer count = jdbc.queryForObject("select count(*) from audit_event where id = ?", Integer.class, id);

        assertThat(count).isEqualTo(1);
    }

    @Test
    void deveRejeitarAtorTypeForaDoConjuntoPermitido() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> jdbc.update(
                        """
                        insert into audit_event (id, occurred_at, actor_type, actor_label, action)
                        values (?, now(), 'ROBOT', 'SYSTEM', 'TEST_EVENT')
                        """,
                        id))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_audit_event_actor_type");
    }

    @Test
    void deveRejeitarAtorUsuarioSemIdentificador() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> jdbc.update(
                        """
                        insert into audit_event (id, occurred_at, actor_type, actor_label, action)
                        values (?, now(), 'USER', 'ana@fincore.dev', 'TEST_EVENT')
                        """,
                        id))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_audit_event_actor_user_id_required");
    }

    @Test
    void deveRejeitarUpdateDeEventoExistente() {
        UUID id = insertMinimalEvent("SYSTEM", null, "SYSTEM", "TEST_EVENT");

        assertThatThrownBy(() -> jdbc.update("update audit_event set action = 'CHANGED' where id = ?", id))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("imutável");
    }

    @Test
    void deveRejeitarDeleteDeEventoExistente() {
        UUID id = insertMinimalEvent("SYSTEM", null, "SYSTEM", "TEST_EVENT");

        assertThatThrownBy(() -> jdbc.update("delete from audit_event where id = ?", id))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("imutável");
    }

    @Test
    void deveManterOEventoIntactoAposTentativaDeUpdateRejeitada() {
        UUID id = insertMinimalEvent("SYSTEM", null, "SYSTEM", "ORIGINAL_ACTION");

        assertThatThrownBy(() -> jdbc.update("update audit_event set action = 'CHANGED' where id = ?", id))
                .isInstanceOf(DataAccessException.class);

        String action = jdbc.queryForObject("select action from audit_event where id = ?", String.class, id);
        assertThat(action).isEqualTo("ORIGINAL_ACTION");
    }

    @Test
    void deveManterOEventoIntactoAposTentativaDeDeleteRejeitada() {
        UUID id = insertMinimalEvent("SYSTEM", null, "SYSTEM", "TEST_EVENT");

        assertThatThrownBy(() -> jdbc.update("delete from audit_event where id = ?", id))
                .isInstanceOf(DataAccessException.class);

        Integer count = jdbc.queryForObject("select count(*) from audit_event where id = ?", Integer.class, id);
        assertThat(count).isEqualTo(1);
    }

    private UUID insertMinimalEvent(String actorType, UUID actorUserId, String actorLabel, String action) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                insert into audit_event (id, occurred_at, actor_type, actor_user_id, actor_label, action)
                values (?, now(), ?, ?, ?, ?)
                """,
                id, actorType, actorUserId, actorLabel, action);
        return id;
    }
}
