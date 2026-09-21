package dev.fincore.ingestion.api;

import java.util.List;
import org.springframework.data.domain.Page;

/** Envelope de paginação (TDS 19.1). Local a este módulo, mesmo padrão de {@code audit.api}/{@code evidence.api}. */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements());
    }
}
