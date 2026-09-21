package dev.fincore.evidence.api;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Envelope de paginação (TDS 19.1). Local a este módulo, como {@code audit.api.PageResponse}
 * — duplicado de propósito (ver o comentário daquele arquivo); migrar para {@code shared}
 * fica para quando um terceiro endpoint precisar do mesmo formato.
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements());
    }
}
