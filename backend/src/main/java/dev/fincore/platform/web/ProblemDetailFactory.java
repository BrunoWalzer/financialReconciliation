package dev.fincore.platform.web;

import dev.fincore.shared.correlation.CorrelationId;
import dev.fincore.shared.error.ErrorCode;
import java.net.URI;
import org.springframework.http.ProblemDetail;

/**
 * Monta o corpo de erro no formato RFC 9457 usado por toda a API (TDS 20).
 *
 * <p>O corpo carrega {@code type}, {@code title}, {@code status}, {@code detail},
 * {@code instance}, {@code code} e {@code correlationId} — e nada mais. Stack trace,
 * nome de tabela, SQL e nome de constraint nunca saem daqui.
 */
final class ProblemDetailFactory {

    private static final String TYPE_BASE = "https://fincore.dev/errors/";

    private ProblemDetailFactory() {
    }

    static ProblemDetail create(ErrorCode code, String detail, String instance) {
        ProblemDetail problem = ProblemDetail.forStatus(code.status());
        problem.setType(URI.create(TYPE_BASE + code.slug()));
        problem.setTitle(code.title());
        problem.setDetail(detail);
        if (instance != null) {
            problem.setInstance(URI.create(instance));
        }
        problem.setProperty("code", code.name());
        problem.setProperty("correlationId", CorrelationId.current().orElse(null));
        return problem;
    }
}
