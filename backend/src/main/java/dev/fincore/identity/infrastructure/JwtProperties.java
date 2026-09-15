package dev.fincore.identity.infrastructure;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code fincore.jwt.*} (TDS 21.1, 27). Sem padrão para {@code secret}: um segredo
 * previsível em produção anula a assinatura — cada perfil decide se dá um valor de
 * conveniência (ver {@code application-local.yml}, {@code application-test.yml}).
 */
@ConfigurationProperties(prefix = "fincore.jwt")
public record JwtProperties(String secret, Duration accessTtl, Duration refreshTtl) {
}
