package dev.fincore.identity.infrastructure;

/**
 * O access token apresentado não pode ser aceito: assinatura inválida, formato
 * malformado ou expirado. {@code RestAuthenticationEntryPoint} traduz isto em
 * {@code 401 UNAUTHENTICATED} — o motivo exato nunca é exposto ao cliente.
 */
public class InvalidAccessTokenException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidAccessTokenException(Throwable cause) {
        super("access token inválido", cause);
    }
}
