package dev.fincore.identity.application;

/**
 * E-mail inexistente, senha errada, usuário inativo ou conta bloqueada — tudo a mesma
 * exceção, de propósito. Usuário inexistente e senha errada precisam produzir resposta e
 * tempo indistinguíveis (Domain §27.4); ter um único tipo, sem subclasses reveladoras, é
 * o que torna impossível ao controller responder diferente por engano.
 */
public class InvalidCredentialsException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidCredentialsException() {
        super("credenciais inválidas");
    }
}
