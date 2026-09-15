package dev.fincore.audit.api;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Envelope de paginação (TDS 19.1). Local a este módulo, não compartilhado — se um
 * segundo endpoint de listagem precisar do mesmo formato, essa é a hora de decidir se
 * ele migra para {@code shared}, não antes.
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements());
    }
}
