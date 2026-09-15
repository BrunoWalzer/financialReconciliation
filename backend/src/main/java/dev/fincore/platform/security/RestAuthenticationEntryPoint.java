package dev.fincore.platform.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.fincore.platform.web.ProblemDetailFactory;
import dev.fincore.shared.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * O que uma requisição sem autenticação válida recebe (TDS 20: 401 UNAUTHENTICATED).
 *
 * <p>Não é um {@code @ExceptionHandler} porque não pode ser: {@code ExceptionTranslationFilter}
 * do Spring Security intercepta {@link AuthenticationException} antes que ela alcance o
 * {@code @RestControllerAdvice} do MVC. Escreve o mesmo formato RFC 9457 na mão, via
 * {@link ProblemDetailFactory} — o mecanismo de erro continua sendo um só (M2 §14).
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {

        ProblemDetail problem = ProblemDetailFactory.create(
                ErrorCode.UNAUTHENTICATED, "Não foi possível autenticar a requisição.", request.getRequestURI());

        response.setStatus(problem.getStatus());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), problem);
    }
}
