package dev.fincore;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base de todo teste que toca o banco.
 *
 * <p>O PostgreSQL e o mesmo da producao, na mesma versao: a correcao financeira do FINCORE
 * mora em constraints (TDS P2), e um banco em memoria aceitaria calado o que o PostgreSQL
 * recusa — provando o oposto do que o teste pretende.
 *
 * <p>O contêiner e um singleton estatico iniciado uma vez por JVM e reaproveitado por toda
 * a suite. Nao ha {@code stop()}: o Ryuk do Testcontainers remove o contêiner no fim do
 * processo, e parar entre classes custaria dezenas de segundos por suite.
 */
@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    static {
        POSTGRES.start();
    }
}
