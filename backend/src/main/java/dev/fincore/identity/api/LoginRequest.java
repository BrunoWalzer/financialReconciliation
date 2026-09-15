package dev.fincore.identity.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Só e-mail e senha. Um campo {@code role} extra no corpo é ignorado pelo desserializador
 * (comportamento padrão do Jackson no Spring Boot) — papel nunca vem do cliente
 * (Implementation Plan M2, teste "papel não vem do corpo da requisição").
 */
public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {
}
