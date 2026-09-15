package dev.fincore;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

/** O contexto Spring sobe contra um PostgreSQL real, com as migrations aplicadas. */
class ApplicationContextIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void deveSubirOContextoQuandoOBancoEstaDisponivel() {
        assertThat(context).isNotNull();
        assertThat(context.getBean(FincoreApplication.class)).isNotNull();
    }
}
