package dev.fincore.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Bloqueio progressivo de login por e-mail (TDS 7.1, 21.4) — a defesa durável contra
 * força bruta; sobrevive a reinício da aplicação porque mora no banco, não em memória.
 *
 * <p>Chave é o e-mail submetido, exista ou não o usuário: a contagem de falhas precisa
 * existir para os dois casos, ou a diferença de comportamento entre eles já vazaria se o
 * e-mail é cadastrado (Domain §27.4 — "falha de autenticação não revela se o usuário
 * existe").
 *
 * <p><b>Os números concretos abaixo não estão fixados em nenhum documento-fonte.</b> O
 * Implementation Plan e o Technical Design especificam a existência do mecanismo e um só
 * número testável — "5 falhas → bloqueio, persiste após reinício" — mas não a duração do
 * bloqueio nem a progressão. Esta classe implementa a menor decisão possível dentro do
 * que foi pedido: limiar de 5 falhas; bloqueio inicial de 15 minutos; dobra a cada 5
 * falhas adicionais (bloqueio persistente e a tentativa não muda isso — falhar durante o
 * bloqueio ainda soma, o que é o comportamento seguro); teto de 24 horas.
 */
@Entity
@Table(name = "login_throttle")
public class LoginThrottle {

    private static final int FAILURE_THRESHOLD = 5;
    private static final Duration BASE_LOCKOUT = Duration.ofMinutes(15);
    private static final Duration MAX_LOCKOUT = Duration.ofHours(24);

    @Id
    @Column(name = "email", nullable = false, updatable = false)
    private String email;

    @Column(name = "failed_count", nullable = false)
    private int failedCount;

    @Column(name = "first_failed_at")
    private Instant firstFailedAt;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    /** Exigido pelo JPA. Nunca chamado pela aplicação. */
    protected LoginThrottle() {
    }

    public LoginThrottle(String email) {
        this.email = Objects.requireNonNull(email, "email é obrigatório");
        this.failedCount = 0;
    }

    public String email() {
        return email;
    }

    public int failedCount() {
        return failedCount;
    }

    public Instant lockedUntil() {
        return lockedUntil;
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    /**
     * Uma tentativa falhou. Acima do limiar, bloqueia — e cada múltiplo adicional do
     * limiar dobra a duração do bloqueio em relação à anterior.
     */
    public void recordFailure(Instant now) {
        if (firstFailedAt == null) {
            firstFailedAt = now;
        }
        failedCount++;
        if (failedCount >= FAILURE_THRESHOLD) {
            int cycles = (failedCount - FAILURE_THRESHOLD) / FAILURE_THRESHOLD;
            Duration lockout = BASE_LOCKOUT.multipliedBy(1L << Math.min(cycles, 10));
            if (lockout.compareTo(MAX_LOCKOUT) > 0) {
                lockout = MAX_LOCKOUT;
            }
            lockedUntil = now.plus(lockout);
        }
    }

    /** Login bem-sucedido: a folha em branco de novo. */
    public void recordSuccess() {
        failedCount = 0;
        firstFailedAt = null;
        lockedUntil = null;
    }
}
