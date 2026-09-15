package dev.fincore.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.fincore.AbstractIntegrationTest;
import dev.fincore.identity.domain.AppUser;
import dev.fincore.identity.domain.RefreshToken;
import dev.fincore.identity.domain.UserRole;
import dev.fincore.identity.infrastructure.AppUserRepository;
import dev.fincore.identity.infrastructure.JwtProperties;
import dev.fincore.identity.infrastructure.RefreshTokenRepository;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Reuso de refresh token sob concorrência real — o quinto cenário obrigatório de
 * threads reais do TDS 8.2 ("duas transações reivindicando o mesmo... reuso de refresh
 * token") e da tabela de concorrência do Implementation Plan M2.
 *
 * <p>Duas threads apresentam o MESMO refresh token simultaneamente. Exatamente uma pode
 * vencer — sem a comparação-e-troca em {@code RefreshTokenRepository.claimForRotation},
 * as duas venceriam, cada uma gerando um filho válido a partir de um token de uso único.
 *
 * <p>Teste de concorrência instável é defeito, não ruído (TDS 8.2) — {@code @RepeatedTest}
 * exercita a corrida várias vezes; instabilidade apareceria como falha intermitente.
 */
class RefreshTokenConcurrencyTest extends AbstractIntegrationTest {

    @Autowired
    private RefreshSessionUseCase refreshSessionUseCase;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private JdbcTemplate jdbc;

    @RepeatedTest(20)
    void exatamenteUmaThreadDeveVencerAoReapresentarOMesmoRefreshTokenSimultaneamente() throws Exception {
        AppUser user = createUser();
        RefreshToken.Issued issued = issueToken(user);

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger reuseDetected = new AtomicInteger();

        try {
            List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    awaitUninterruptibly(start);
                    try {
                        refreshSessionUseCase.execute(issued.rawSecret(), "junit", "127.0.0.1");
                        succeeded.incrementAndGet();
                    } catch (RefreshTokenReuseDetectedException e) {
                        reuseDetected.incrementAndGet();
                    }
                }));
            }

            ready.await(10, TimeUnit.SECONDS);
            start.countDown();
            for (java.util.concurrent.Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdown();
        }

        assertThat(succeeded.get()).as("exatamente uma chamada rotaciona com sucesso").isEqualTo(1);
        assertThat(reuseDetected.get()).as("a outra é rejeitada como reuso").isEqualTo(1);

        // Zero, não um: a família inteira é revogada quando reuso é detectado — TDS 21.1
        // não abre exceção para o ramo que "venceu" a corrida por chegar primeiro. O
        // sistema não consegue distinguir essa corrida benigna de um roubo de token
        // de verdade, e a política é queimar a sessão inteira e forçar novo login. Sem a
        // comparação-e-troca em claimForRotation, este total seria 2 — dois filhos
        // válidos e ativos a partir de um token de uso único, o bug que este teste existe
        // para pegar.
        Integer activeCount = jdbc.queryForObject(
                "select count(*) from refresh_token where user_id = ? and revoked_at is null",
                Integer.class,
                user.id());
        assertThat(activeCount).isEqualTo(0);

        Map<String, Object> parent = jdbc.queryForMap(
                "select revoked_at, replaced_by_id from refresh_token where id = ?", issued.token().id());
        assertThat(parent.get("revoked_at")).isNotNull();
        assertThat(parent.get("replaced_by_id")).isNotNull();
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private AppUser createUser() {
        AppUser user = new AppUser(
                "concurrency-" + java.util.UUID.randomUUID() + "@fincore.dev",
                passwordEncoder.encode("qualquer-senha"),
                "Nome",
                EnumSet.of(UserRole.AUDITOR),
                Instant.now());
        return appUserRepository.save(user);
    }

    private RefreshToken.Issued issueToken(AppUser user) {
        RefreshToken.Issued issued = RefreshToken.issue(
                user.id(), jwtProperties.refreshTtl(), Instant.now(), "junit", "127.0.0.1");
        refreshTokenRepository.save(issued.token());
        return issued;
    }
}
