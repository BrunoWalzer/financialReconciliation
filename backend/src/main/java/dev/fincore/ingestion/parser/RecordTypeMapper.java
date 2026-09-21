package dev.fincore.ingestion.parser;

import dev.fincore.evidence.domain.RecordType;
import java.util.Locale;
import java.util.Optional;

/** {@code tipo} de Internal Sales → {@link RecordType} (Implementation Plan DR-1). Conjunto fechado; valor desconhecido não é adivinhado. */
public final class RecordTypeMapper {

    private RecordTypeMapper() {
    }

    public static Optional<RecordType> fromInternalSalesTipo(String tipo) {
        if (tipo == null) {
            return Optional.empty();
        }
        return switch (tipo.trim().toUpperCase(Locale.ROOT)) {
            case "VENDA" -> Optional.of(RecordType.SALE);
            case "ESTORNO" -> Optional.of(RecordType.REFUND);
            default -> Optional.empty();
        };
    }
}
