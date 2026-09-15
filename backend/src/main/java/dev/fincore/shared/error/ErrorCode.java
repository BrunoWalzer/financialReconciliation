package dev.fincore.shared.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * Código estável de erro devolvido em toda resposta de falha (TDS 20).
 *
 * <p>O cliente decide comportamento pelo {@code code}, nunca pelo texto. Cada milestone
 * acrescenta os códigos que efetivamente consegue produzir; este enum contém apenas os
 * que o M0 produz, porque não há endpoint de negócio ainda.
 */
public enum ErrorCode {

    /** Entrada sintaticamente inválida. */
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Requisição inválida"),

    /** Rota ou recurso inexistente. */
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Recurso não encontrado"),

    /** Método HTTP não suportado pela rota. */
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Método não permitido"),

    /** Tipo de conteúdo não suportado. */
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Tipo de conteúdo não suportado"),

    /** Falha não prevista. A resposta carrega apenas o correlationId. */
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno");

    private final HttpStatus status;
    private final String title;

    ErrorCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }

    /** Segmento usado na URI de {@code type}: {@code RESOURCE_NOT_FOUND} → {@code resource-not-found}. */
    public String slug() {
        return name().toLowerCase().replace('_', '-');
    }

    /**
     * Código para uma falha que o Spring MVC classificou antes de chegar ao domínio.
     * Sem correspondência exata, um 4xx é tratado como entrada inválida e qualquer outra
     * coisa como falha interna — nunca se inventa um código de negócio aqui.
     */
    public static ErrorCode forStatus(HttpStatusCode status) {
        for (ErrorCode code : values()) {
            if (code.status.value() == status.value()) {
                return code;
            }
        }
        return status.is4xxClientError() ? VALIDATION_FAILED : INTERNAL_ERROR;
    }
}
