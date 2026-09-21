package dev.fincore.shared.time;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

/**
 * Deriva {@code businessDate} — a única base de comparação temporal do motor (Domain §5.5;
 * TDS 8.4). Comparar instantes produz erros de um dia inteiro em torno da meia-noite entre
 * fusos diferentes; a data de negócio elimina essa comparação.
 *
 * <p>Função pura, sem estado e sem relógio: a data de negócio vem sempre do
 * {@code sourceTimestamp} e do fuso da fonte, nunca do instante corrente.
 */
public final class BusinessDateResolver {

    private BusinessDateResolver() {
    }

    /** Converte um instante da origem para a data de negócio, no fuso declarado da fonte. */
    public static LocalDate resolve(Instant sourceTimestamp, ZoneId sourceZone) {
        Objects.requireNonNull(sourceTimestamp, "sourceTimestamp é obrigatório");
        Objects.requireNonNull(sourceZone, "sourceZone é obrigatório");
        return sourceTimestamp.atZone(sourceZone).toLocalDate();
    }

    /** Fontes que só trazem data (sem hora) preservam a data — nada a converter. */
    public static LocalDate resolve(LocalDate dateOnly) {
        return Objects.requireNonNull(dateOnly, "dateOnly é obrigatório");
    }
}
