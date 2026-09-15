package dev.fincore.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import dev.fincore.AbstractIntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** O pipeline de migration esta operante e o schema tem exatamente as tabelas dos milestones entregues. */
class FlywayMigrationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void deveAplicarAsMigrationsBaseQuandoOContextoSobe() {
        List<Map<String, Object>> applied = jdbc.queryForList(
                "select version, script, success from flyway_schema_history order by installed_rank");

        assertThat(applied)
                .as("V0 precisa constar no historico do Flyway")
                .anySatisfy(row -> {
                    assertThat(row.get("version")).isEqualTo("0");
                    assertThat(row.get("script")).isEqualTo("V0__extensions.sql");
                    assertThat(row.get("success")).isEqualTo(true);
                });

        assertThat(applied)
                .as("V1 precisa constar no historico do Flyway")
                .anySatisfy(row -> {
                    assertThat(row.get("version")).isEqualTo("1");
                    assertThat(row.get("script")).isEqualTo("V1__audit.sql");
                    assertThat(row.get("success")).isEqualTo(true);
                });
    }

    @Test
    void deveManterOSchemaSemTabelaAlemDasEntreguesAteOM1() {
        List<String> tables = jdbc.queryForList(
                """
                select table_name
                  from information_schema.tables
                 where table_schema = 'public'
                   and table_type = 'BASE TABLE'
                """,
                String.class);

        assertThat(tables)
                .as("ate o M1, a unica tabela de negocio e audit_event; nenhuma tabela financeira existe ainda")
                .containsExactlyInAnyOrder("flyway_schema_history", "audit_event");
    }
}
