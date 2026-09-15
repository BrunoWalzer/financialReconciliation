package dev.fincore.identity.application;

/**
 * Um refresh token já revogado (rotacionado, deslogado, ou vítima de uma detecção
 * anterior) foi apresentado de novo — o sinal clássico de token roubado (TDS 21.1).
 *
 * <p>Lançada depois que {@code RefreshSessionUseCase} já revogou a família inteira e
 * gravou a auditoria: {@code @Transactional(noRollbackFor = ...)} garante que essa
 * escrita sobrevive a esta exceção. A resposta ao cliente continua genérica — {@code 401},
 * igual a qualquer outro refresh inválido.
 */
public class RefreshTokenReuseDetectedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public RefreshTokenReuseDetectedException() {
        super("reuso de refresh token detectado");
    }
}
