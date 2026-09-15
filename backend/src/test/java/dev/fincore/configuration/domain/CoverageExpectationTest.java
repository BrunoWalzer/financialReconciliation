package dev.fincore.configuration.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.fincore.shared.identifier.Uuid7;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** {@link CoverageExpectation} — periodicidade esperada de chegada de dados (TDS 7.3). */
class CoverageExpectationTest {

    @Test
    void deveCriarAtivaComVersaoZero() {
        UUID sourceId = Uuid7.generate();
        CoverageExpectation expectation = new CoverageExpectation(sourceId, CoverageSchedule.BUSINESS_DAYS, 1);

        assertThat(expectation.id()).isNotNull();
        assertThat(expectation.sourceId()).isEqualTo(sourceId);
        assertThat(expectation.schedule()).isEqualTo(CoverageSchedule.BUSINESS_DAYS);
        assertThat(expectation.graceDays()).isEqualTo(1);
        assertThat(expectation.active()).isTrue();
        assertThat(expectation.version()).isZero();
    }

    @Test
    void deveRejeitarScheduleNulo() {
        assertThatThrownBy(() -> new CoverageExpectation(Uuid7.generate(), null, 1))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void deveRejeitarSourceIdNulo() {
        assertThatThrownBy(() -> new CoverageExpectation(null, CoverageSchedule.DAILY, 0))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void deveAtualizarScheduleEGraceDays() {
        CoverageExpectation expectation = new CoverageExpectation(Uuid7.generate(), CoverageSchedule.DAILY, 0);

        expectation.update(CoverageSchedule.WEEKLY, 2);

        assertThat(expectation.schedule()).isEqualTo(CoverageSchedule.WEEKLY);
        assertThat(expectation.graceDays()).isEqualTo(2);
    }

    @Test
    void deveRejeitarAtualizacaoComScheduleNulo() {
        CoverageExpectation expectation = new CoverageExpectation(Uuid7.generate(), CoverageSchedule.DAILY, 0);

        assertThatThrownBy(() -> expectation.update(null, 2))
                .isInstanceOf(NullPointerException.class);
    }
}
