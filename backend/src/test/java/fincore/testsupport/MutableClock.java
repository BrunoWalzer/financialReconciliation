package fincore.testsupport;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

/**
 * Um {@link Clock} que o teste avança manualmente — para simular expiração de token,
 * bloqueio de login e rotação de sessão sem esperar minutos reais (regra 34: nenhum
 * teste depende do relógio real).
 *
 * <p>Substitui o bean {@code Clock} real via {@code @TestConfiguration} + {@code @Primary}
 * nos testes que precisam controlar o tempo.
 */
public final class MutableClock extends Clock {

    private volatile Instant instant;
    private final ZoneId zone;

    public MutableClock(Instant initial) {
        this(initial, ZoneId.of("UTC"));
    }

    private MutableClock(Instant initial, ZoneId zone) {
        this.instant = initial;
        this.zone = zone;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new MutableClock(instant, zone);
    }

    @Override
    public Instant instant() {
        return instant;
    }

    public void advanceBy(java.time.Duration duration) {
        instant = instant.plus(duration);
    }
}
