package dev.fincore.audit.application;

import dev.fincore.audit.domain.AuditEvent;
import dev.fincore.audit.infrastructure.AuditEventRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * {@code GET /audit-events} (Implementation Plan M2: "leitura, todos os papéis").
 *
 * <p>{@code @PreAuthorize} explícito mesmo sendo "qualquer autenticado" — o padrão do
 * módulo {@code identity} é que toda autorização se declare na aplicação, nunca fique
 * implícita só porque o filtro HTTP já pede autenticação (TDS 21.2).
 */
@Service
public class ListAuditEventsUseCase {

    private final AuditEventRepository repository;

    public ListAuditEventsUseCase(AuditEventRepository repository) {
        this.repository = repository;
    }

    @PreAuthorize("isAuthenticated()")
    public Page<AuditEvent> execute(Pageable pageable) {
        return repository.findAll(pageable);
    }
}
