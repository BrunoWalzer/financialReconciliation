package dev.fincore.identity.application;

import java.time.Duration;

/**
 * O que {@code LoginUseCase} e {@code RefreshSessionUseCase} devolvem: o material para o
 * controller montar a resposta JSON (access token) e o cookie (refresh token) — nenhum
 * dos dois casos de uso sabe o que é um cookie ou uma resposta HTTP.
 *
 * <p>{@code refreshTokenTtl} é a duração, não o instante absoluto de expiração: o
 * controller usa isto direto como {@code maxAge} do cookie. Se fosse um instante
 * absoluto, o controller precisaria subtrair {@code Instant.now()} — e o relógio real,
 * ali, divergiria do {@link java.time.Clock} injetado que os testes substituem para
 * simular expiração sem esperar de verdade (regra 34 do Plano).
 */
public record SessionTokens(String accessToken, Duration accessTokenTtl, String refreshToken, Duration refreshTokenTtl) {
}
