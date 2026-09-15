package dev.fincore.platform.web;

import dev.fincore.shared.correlation.CorrelationId;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Lê {@code X-Correlation-Id} da requisição ou gera um, publica no MDC e ecoa na resposta.
 *
 * <p>Precedência máxima: tudo o que for logado durante a requisição — inclusive falhas
 * ocorridas em filtros posteriores — precisa sair correlacionado.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    /**
     * Guarda o identificador na requisição. O despacho de erro do container reentra neste
     * filtro; sem isso ele geraria um segundo identificador, diferente do já ecoado.
     */
    static final String REQUEST_ATTRIBUTE = CorrelationIdFilter.class.getName() + ".correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String correlationId = resolve(request);
        MDC.put(CorrelationId.MDC_KEY, correlationId);
        response.setHeader(CorrelationId.HEADER, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(CorrelationId.MDC_KEY);
        }
    }

    private static String resolve(HttpServletRequest request) {
        Object existing = request.getAttribute(REQUEST_ATTRIBUTE);
        if (existing instanceof String alreadyResolved) {
            return alreadyResolved;
        }
        String incoming = request.getHeader(CorrelationId.HEADER);
        String correlationId = CorrelationId.isAcceptable(incoming) ? incoming : CorrelationId.generate();
        request.setAttribute(REQUEST_ATTRIBUTE, correlationId);
        return correlationId;
    }

    /** O identificador precisa existir também nas respostas de erro produzidas pelo container. */
    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }
}
