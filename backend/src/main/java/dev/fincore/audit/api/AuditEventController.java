package dev.fincore.audit.api;

import dev.fincore.audit.application.ListAuditEventsUseCase;
import dev.fincore.audit.domain.AuditEvent;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * {@code GET /audit-events} (TDS 19.2, Implementation Plan M2: "leitura, todos os
 * papéis"). Paginação e ordenação seguem a convenção geral da API (TDS 19.1): {@code page}
 * 0-based, {@code size} padrão 25 máx 100, {@code sort} em lista branca.
 */
@RestController
public class AuditEventController {

    private static final int DEFAULT_SIZE = 25;
    private static final int MAX_SIZE = 100;
    private static final Set<String> SORTABLE_FIELDS = Set.of("occurredAt", "action");

    private final ListAuditEventsUseCase listAuditEventsUseCase;

    public AuditEventController(ListAuditEventsUseCase listAuditEventsUseCase) {
        this.listAuditEventsUseCase = listAuditEventsUseCase;
    }

    @GetMapping("/audit-events")
    public PageResponse<AuditEventResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + DEFAULT_SIZE) int size,
            @RequestParam(defaultValue = "occurredAt,desc") String sort) {

        Pageable pageable = PageRequest.of(page, Math.min(size, MAX_SIZE), parseSort(sort));
        Page<AuditEvent> events = listAuditEventsUseCase.execute(pageable);
        return PageResponse.of(events.map(AuditEventResponse::from));
    }

    private static Sort parseSort(String sort) {
        String[] parts = sort.split(",", 2);
        String field = parts[0];
        if (!SORTABLE_FIELDS.contains(field)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "campo de ordenação desconhecido: " + field);
        }
        Sort.Direction direction = parts.length > 1 && "asc".equalsIgnoreCase(parts[1])
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        return Sort.by(direction, field);
    }
}
