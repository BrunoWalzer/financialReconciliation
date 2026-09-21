package dev.fincore.platform.web;

import dev.fincore.configuration.application.FeeRuleAlreadyActiveException;
import dev.fincore.configuration.application.StaleConfigurationVersionException;
import dev.fincore.identity.application.InvalidCredentialsException;
import dev.fincore.identity.application.RefreshTokenInvalidException;
import dev.fincore.identity.application.RefreshTokenReuseDetectedException;
import dev.fincore.ingestion.application.DuplicateFileException;
import dev.fincore.ingestion.application.ImportBatchNotRetryableException;
import dev.fincore.ingestion.application.UploadTooLargeException;
import dev.fincore.shared.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
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

    /**
     * Falha de {@code @Valid} num corpo de requisição (TDS 20: 400 com {@code errors[]}).
     * É a mais específica das exceções que {@link ResponseEntityExceptionHandler} já
     * trata — precisa de override próprio porque {@code errors[]} não existe no caminho
     * genérico de {@link #handleExceptionInternal}.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {

        List<ValidationError> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(GlobalErrorHandler::toValidationError)
                .toList();

        ProblemDetail problem = ProblemDetailFactory.create(
                ErrorCode.VALIDATION_FAILED, detailFor(ErrorCode.VALIDATION_FAILED), instanceOf(request));
        problem.setProperty("errors", errors);
        return super.handleExceptionInternal(exception, problem, headers, statusCode, request);
    }

    /** Credenciais inválidas em login: mesma resposta para e-mail inexistente e senha errada (Domain §27.4). */
    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<ProblemDetail> handleInvalidCredentials(InvalidCredentialsException exception, HttpServletRequest request) {
        return unauthenticated(request);
    }

    /** Refresh token ausente, expirado, ou reuso de um já revogado — resposta idêntica nos três casos. */
    @ExceptionHandler({RefreshTokenInvalidException.class, RefreshTokenReuseDetectedException.class})
    ResponseEntity<ProblemDetail> handleInvalidRefreshToken(RuntimeException exception, HttpServletRequest request) {
        return unauthenticated(request);
    }

    /** Recurso inexistente resolvido dentro de um caso de uso — não pelo roteamento do MVC. */
    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<ProblemDetail> handleNotFound(NoSuchElementException exception, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetailFactory.create(
                ErrorCode.RESOURCE_NOT_FOUND, detailFor(ErrorCode.RESOURCE_NOT_FOUND), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    /** {@code If-Match} não confere — pelo valor que o cliente enviou, ou porque outra transação venceu a corrida. */
    @ExceptionHandler(StaleConfigurationVersionException.class)
    ResponseEntity<ProblemDetail> handleStaleVersion(StaleConfigurationVersionException exception, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetailFactory.create(
                ErrorCode.CONFIGURATION_VERSION_CONFLICT,
                detailFor(ErrorCode.CONFIGURATION_VERSION_CONFLICT),
                request.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    /** Já existe regra de taxa ativa para a mesma fonte e meio de pagamento ({@code uq_fee_rule_active_source_payment_method}). */
    @ExceptionHandler(FeeRuleAlreadyActiveException.class)
    ResponseEntity<ProblemDetail> handleFeeRuleAlreadyActive(FeeRuleAlreadyActiveException exception, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetailFactory.create(
                ErrorCode.FEE_RULE_ALREADY_ACTIVE, detailFor(ErrorCode.FEE_RULE_ALREADY_ACTIVE), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    /**
     * Invariante de domínio violado (ex.: {@code SettlementWindow} com {@code maxDays} <
     * {@code minDays}) — validação que {@code @Valid} não alcança porque cruza campos.
     * Sem isso, a exceção cairia no handler genérico e voltaria como 500 para uma entrada
     * de cliente perfeitamente identificável como inválida.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException exception, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetailFactory.create(
                ErrorCode.VALIDATION_FAILED, detailFor(ErrorCode.VALIDATION_FAILED), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    /**
     * {@code @PreAuthorize} negado dentro de um caso de uso (TDS 21.2). O comentário de
     * {@code RestAccessDeniedHandler} presume que {@code ExceptionTranslationFilter}
     * intercepta esta exceção "nos dois casos" (cadeia HTTP e método), mas isso só vale
     * quando ela escapa de toda a {@code FilterChain}: uma negação dentro de um caso de
     * uso é resolvida pelo próprio {@code DispatcherServlet} antes de chegar lá, e caía no
     * handler genérico como 500 — descoberto pelos primeiros testes deste milestone que
     * exercitam um endpoint {@code @PreAuthorize} de ponta a ponta via HTTP com papel
     * errado. Mesmo corpo e status do handler de filtro, para resposta consistente nos
     * dois casos.
     */
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException exception, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetailFactory.create(
                ErrorCode.FORBIDDEN, detailFor(ErrorCode.FORBIDDEN), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
    }

    /** Mesmo conteúdo, fonte e data de referência já foram importados ({@code uq_import_batch_content}, I-6). */
    @ExceptionHandler(DuplicateFileException.class)
    ResponseEntity<ProblemDetail> handleDuplicateFile(DuplicateFileException exception, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetailFactory.create(
                ErrorCode.DUPLICATE_FILE, detailFor(ErrorCode.DUPLICATE_FILE), request.getRequestURI());
        problem.setProperty("originalImportBatchId", exception.originalImportBatchId());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    // MaxUploadSizeExceededException (o limite do próprio resolvedor de multipart do
    // Spring) não tem @ExceptionHandler próprio aqui de propósito: desde este Spring Boot,
    // ResponseEntityExceptionHandler já a trata via seu handleException(...) consolidado —
    // um handler explícito para o mesmo tipo aqui é rejeitado como ambíguo na
    // inicialização. Ela cai no handleExceptionInternal já sobrescrito acima, que traduz
    // o status (413) via ErrorCode.forStatus.

    /**
     * O limite de negócio ({@code fincore.ingestion.max-upload-bytes}, TDS 9.1/9.4:
     * "Tamanho" → 413) — verificado explicitamente em {@code ImportFileUseCase} porque o
     * limite do resolvedor de multipart do Spring depende do contêiner servlet real, que o
     * MockMvc não reproduz fielmente para ser testado.
     */
    @ExceptionHandler(UploadTooLargeException.class)
    ResponseEntity<ProblemDetail> handleUploadTooLarge(UploadTooLargeException exception, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetailFactory.create(
                ErrorCode.PAYLOAD_TOO_LARGE, detailFor(ErrorCode.PAYLOAD_TOO_LARGE), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(problem);
    }

    /** {@code POST /imports/{id}/retry} fora de {@code FAILED} (Implementation Plan M8). */
    @ExceptionHandler(ImportBatchNotRetryableException.class)
    ResponseEntity<ProblemDetail> handleImportBatchNotRetryable(
            ImportBatchNotRetryableException exception, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetailFactory.create(
                ErrorCode.IMPORT_BATCH_NOT_RETRYABLE, detailFor(ErrorCode.IMPORT_BATCH_NOT_RETRYABLE), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    private static ResponseEntity<ProblemDetail> unauthenticated(HttpServletRequest request) {
        ProblemDetail problem = ProblemDetailFactory.create(
                ErrorCode.UNAUTHENTICATED, detailFor(ErrorCode.UNAUTHENTICATED), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
    }

    private static ValidationError toValidationError(FieldError fieldError) {
        return new ValidationError(fieldError.getField(), fieldError.getDefaultMessage());
    }

    private record ValidationError(String field, String message) {
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
            // Deliberadamente genérico: não distingue token ausente, expirado, malformado,
            // credencial errada ou conta bloqueada (Domain §27.4).
            case UNAUTHENTICATED -> "Não foi possível autenticar a requisição.";
            case FORBIDDEN -> "Você não tem permissão para executar esta operação.";
            case CONFIGURATION_VERSION_CONFLICT ->
                    "A configuração foi alterada por outra requisição. Releia o recurso e tente de novo.";
            case FEE_RULE_ALREADY_ACTIVE -> "Já existe uma regra de taxa ativa para esta fonte e meio de pagamento.";
            case DUPLICATE_FILE -> "Este arquivo, para esta fonte e data de referência, já foi importado.";
            case PAYLOAD_TOO_LARGE -> "O arquivo excede o tamanho máximo permitido para upload.";
            case IMPORT_BATCH_NOT_RETRYABLE -> "Esta importação não está em FAILED — só é possível reprocessar a partir desse estado.";
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
