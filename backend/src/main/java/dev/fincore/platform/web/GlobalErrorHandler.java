package dev.fincore.platform.web;

import dev.fincore.shared.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Tratamento central de erro da API: toda falha sai como {@code application/problem+json}
 * no formato RFC 9457, com {@code code} e {@code correlationId} (TDS 20).
 *
 * <p>Duas garantias sustentam este ponto único: o corpo nunca carrega detalhe interno, e
 * nenhuma exceção escapa sem {@code correlationId} — inclusive a de status 500.
 *
 * <p>Os textos de {@code detail} são fixos por classe de falha. A mensagem da exceção não
 * é reaproveitada: ela é escrita para quem opera o sistema, não para quem o consome, e é
 * por esse caminho que nome de tabela e SQL costumam vazar.
 */
@RestControllerAdvice
public class GlobalErrorHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalErrorHandler.class);

    /**
     * Falhas que o Spring MVC classifica antes do controller: rota inexistente, método não
     * permitido, corpo ilegível. Reescritas no formato do FINCORE.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception,
            @Nullable Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {

        ErrorCode code = ErrorCode.forStatus(statusCode);
        ProblemDetail problem = ProblemDetailFactory.create(code, detailFor(code), instanceOf(request));
        return super.handleExceptionInternal(exception, problem, headers, statusCode, request);
    }

    /** Qualquer coisa não prevista. O cliente recebe 500 e o correlationId; o resto vai para o log. */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("Falha não tratada em {} {}", request.getMethod(), request.getRequestURI(), exception);

        ProblemDetail problem = ProblemDetailFactory.create(
                ErrorCode.INTERNAL_ERROR,
                detailFor(ErrorCode.INTERNAL_ERROR),
                request.getRequestURI());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }

    private static String detailFor(ErrorCode code) {
        return switch (code) {
            case RESOURCE_NOT_FOUND -> "O recurso solicitado não existe.";
            case METHOD_NOT_ALLOWED -> "O método HTTP não é permitido para este recurso.";
            case UNSUPPORTED_MEDIA_TYPE -> "O tipo de conteúdo enviado não é suportado.";
            case VALIDATION_FAILED -> "A requisição não pôde ser interpretada.";
            case INTERNAL_ERROR -> "Ocorreu uma falha inesperada. Informe o correlationId ao suporte.";
        };
    }

    @Nullable
    private static String instanceOf(WebRequest request) {
        return request instanceof ServletWebRequest servletRequest
                ? servletRequest.getRequest().getRequestURI()
                : null;
    }
}
