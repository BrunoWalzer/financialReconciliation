package dev.fincore.shared.correlation;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;

/**
 * Identificador de correlação que atravessa requisição, log e resposta de erro (TDS 26).
 *
 * <p>Não há tracing distribuído no FINCORE: há um serviço só, e o {@code correlationId}
 * basta para reconstruir o que aconteceu.
 */
public final class CorrelationId {

    /** Cabeçalho aceito na entrada e sempre ecoado na saída. */
    public static final String HEADER = "X-Correlation-Id";

    /** Chave no MDC — aparece em todo log estruturado. */
    public static final String MDC_KEY = "correlationId";

    /**
     * Um identificador recebido de fora só é aceito se couber neste formato. O valor vai
     * para log e para cabeçalho de resposta; aceitar texto arbitrário abriria injeção de
     * log e de cabeçalho.
     */
    private static final Pattern ACCEPTED = Pattern.compile("[A-Za-z0-9._:-]{1,64}");

    private CorrelationId() {
    }

    public static String generate() {
        return UUID.randomUUID().toString();
    }

    public static boolean isAcceptable(String candidate) {
        return candidate != null && ACCEPTED.matcher(candidate).matches();
    }

    /** O identificador da requisição em curso, quando houver uma. */
    public static Optional<String> current() {
        return Optional.ofNullable(MDC.get(MDC_KEY));
    }
}
