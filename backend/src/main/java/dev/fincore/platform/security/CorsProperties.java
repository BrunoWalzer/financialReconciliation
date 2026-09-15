package dev.fincore.platform.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code fincore.cors.*} (TDS 21.3). Sem padrão: nunca {@code "*"}. */
@ConfigurationProperties(prefix = "fincore.cors")
public record CorsProperties(String allowedOrigin) {
}
