package dev.fincore.configuration.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.fincore.AbstractIntegrationTest;
import dev.fincore.configuration.domain.FeeRule;
import dev.fincore.configuration.domain.RoundingMode;
import dev.fincore.shared.configuration.RunConfigSnapshot;
import fincore.testsupport.MutableClock;
import java.time.Clock;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/**
 * {@link ConfigSnapshotFactory} contra PostgreSQL real — os três requisitos obrigatórios do
 * Implementation Plan M3 para "Reprodutibilidade histórica": (1) duas capturas sem
 * alteração intermediária são iguais; (2) o snapshot serializado/desserializado é igual ao
 * original; (3) o snapshot capturado não muda quando a configuração muda depois.
 */
@Import(ConfigSnapshotFactoryIntegrationTest.MutableClockConfig.class)
class ConfigSnapshotFactoryIntegrationTest extends AbstractIntegrationTest {

    // UUIDs fixos do seed de referência V3 (ver V3__configuration.sql).
    private static final UUID SOURCE_PAIR_ID = UUID.fromString("00000000-0000-7000-8000-000000000103");
    private static final UUID ACQUIRER_SOURCE_ID = UUID.fromString("00000000-0000-7000-8000-000000000102");

    @TestConfiguration
    static class MutableClockConfig {
        @Bean
        @Primary
        Clock testClock() {
            return new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        }
    }

    @Autowired
    private ConfigSnapshotFactory configSnapshotFactory;

    @Autowired
    private FeeRuleRepository feeRuleRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void duasCapturasSemAlteracaoIntermediariaProduzemSnapshotsIguais() {
        RunConfigSnapshot first = configSnapshotFactory.capture(SOURCE_PAIR_ID);
        RunConfigSnapshot second = configSnapshotFactory.capture(SOURCE_PAIR_ID);

        assertThat(second).isEqualTo(first);
    }

    @Test
    void snapshotSerializadoEDesserializadoEIgualAoOriginal() throws Exception {
        RunConfigSnapshot original = configSnapshotFactory.capture(SOURCE_PAIR_ID);

        String json = objectMapper.writeValueAsString(original);
        RunConfigSnapshot roundTripped = objectMapper.readValue(json, RunConfigSnapshot.class);

        assertThat(roundTripped).isEqualTo(original);
    }

    @Test
    void snapshotCapturadoNaoMudaQuandoAConfiguracaoMudaDepois() {
        RunConfigSnapshot before = configSnapshotFactory.capture(SOURCE_PAIR_ID);

        FeeRule newRule = new FeeRule(ACQUIRER_SOURCE_ID, "PIX", 999, 0L, RoundingMode.HALF_UP);
        feeRuleRepository.save(newRule);

        assertThat(before.feeRules()).noneMatch(fr -> fr.percentageBp() == 999);

        RunConfigSnapshot after = configSnapshotFactory.capture(SOURCE_PAIR_ID);
        assertThat(after.feeRules()).anyMatch(fr -> fr.percentageBp() == 999);
        assertThat(before).isNotEqualTo(after);
    }

    @Test
    void deveRefletirValoresCorrentesDoSeedDeReferencia() {
        RunConfigSnapshot snapshot = configSnapshotFactory.capture(SOURCE_PAIR_ID);

        assertThat(snapshot.tolerance().absoluteMinor()).isEqualTo(2);
        assertThat(snapshot.tolerance().currency()).isEqualTo("BRL");
        assertThat(snapshot.settlementWindows()).hasSize(2);
        assertThat(snapshot.sources()).extracting(RunConfigSnapshot.SourceSnapshot::code)
                .containsExactlyInAnyOrder("INTERNAL_SALES", "ACQUIRER_SETTLEMENT");
        assertThat(snapshot.capturedAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void deveFalharParaSourcePairInexistente() {
        assertThatThrownBy(() -> configSnapshotFactory.capture(UUID.randomUUID()))
                .isInstanceOf(NoSuchElementException.class);
    }
}
