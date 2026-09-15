package dev.fincore.platform.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.fincore.platform.web.ProblemDetailFactory;
import dev.fincore.shared.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * O que uma requisição autenticada, mas sem a autoridade exigida, recebe (TDS 20: 403
 * FORBIDDEN). Cobre tanto a negação vinda da cadeia HTTP quanto a de
 * {@code @PreAuthorize} num caso de uso (TDS 21.2) — {@code ExceptionTranslationFilter}
 * intercepta {@link AccessDeniedException} nos dois casos antes que o MVC a veja.
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public RestAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {

        ProblemDetail problem = ProblemDetailFactory.create(
                ErrorCode.FORBIDDEN, "Você não tem permissão para executar esta operação.", request.getRequestURI());

        response.setStatus(problem.getStatus());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), problem);
    }
}
