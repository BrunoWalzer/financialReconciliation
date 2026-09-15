package dev.fincore.identity.application;

/**
 * O refresh token apresentado não existe ou expirou naturalmente — nada a revogar em
 * cascata aqui (isso é {@link RefreshTokenReuseDetectedException}). Resposta ao cliente é
 * a mesma nos dois casos: {@code 401}, genérica.
 */
public class RefreshTokenInvalidException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public RefreshTokenInvalidException() {
        super("refresh token inválido");
    }
}
