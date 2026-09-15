package dev.fincore.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** {@link LoginThrottle} — bloqueio progressivo por e-mail (TDS 21.4). */
class LoginThrottleTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void naoDeveEstarBloqueadaAntesDoLimiarDeFalhas() {
        LoginThrottle throttle = new LoginThrottle("ana@fincore.dev");

        for (int i = 0; i < 4; i++) {
            throttle.recordFailure(NOW);
        }

        assertThat(throttle.failedCount()).isEqualTo(4);
        assertThat(throttle.isLocked(NOW)).isFalse();
    }

    @Test
    void deveBloquearAoAtingirCincoFalhas() {
        LoginThrottle throttle = new LoginThrottle("ana@fincore.dev");

        for (int i = 0; i < 5; i++) {
            throttle.recordFailure(NOW);
        }

        assertThat(throttle.isLocked(NOW)).isTrue();
        assertThat(throttle.lockedUntil()).isAfter(NOW);
    }

    @Test
    void deveDesbloquearDepoisQueOBloqueioExpira() {
        LoginThrottle throttle = new LoginThrottle("ana@fincore.dev");
        for (int i = 0; i < 5; i++) {
            throttle.recordFailure(NOW);
        }

        Instant muitoDepois = throttle.lockedUntil().plusSeconds(1);

        assertThat(throttle.isLocked(muitoDepois)).isFalse();
    }

    @Test
    void deveDobrarADuracaoDoBloqueioACadaCincoFalhasAdicionais() {
        LoginThrottle throttle = new LoginThrottle("ana@fincore.dev");
        for (int i = 0; i < 5; i++) {
            throttle.recordFailure(NOW);
        }
        Instant primeiroBloqueio = throttle.lockedUntil();

        for (int i = 0; i < 5; i++) {
            throttle.recordFailure(NOW);
        }
        Instant segundoBloqueio = throttle.lockedUntil();

        // O segundo bloqueio, contado a partir do mesmo NOW, dura o dobro do primeiro.
        long primeiraDuracaoSegundos = primeiroBloqueio.getEpochSecond() - NOW.getEpochSecond();
        long segundaDuracaoSegundos = segundoBloqueio.getEpochSecond() - NOW.getEpochSecond();
        assertThat(segundaDuracaoSegundos).isEqualTo(primeiraDuracaoSegundos * 2);
    }

    @Test
    void deveZerarContagemAposSucesso() {
        LoginThrottle throttle = new LoginThrottle("ana@fincore.dev");
        for (int i = 0; i < 5; i++) {
            throttle.recordFailure(NOW);
        }

        throttle.recordSuccess();

        assertThat(throttle.failedCount()).isZero();
        assertThat(throttle.isLocked(NOW)).isFalse();
        assertThat(throttle.lockedUntil()).isNull();
    }
}
