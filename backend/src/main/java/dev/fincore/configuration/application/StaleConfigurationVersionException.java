package dev.fincore.configuration.application;

/**
 * {@code If-Match} não bate com a versão corrente (TDS 19.1) — seja porque o cliente leu
 * um valor velho, seja porque outra transação venceu a corrida entre a leitura e esta
 * escrita (nesse segundo caso, é o próprio optimistic locking do JPA que dispara,
 * traduzido para esta mesma exceção — ver os casos de uso de atualização).
 */
public class StaleConfigurationVersionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public StaleConfigurationVersionException() {
        super("versão informada em If-Match não confere com a versão atual");
    }
}
