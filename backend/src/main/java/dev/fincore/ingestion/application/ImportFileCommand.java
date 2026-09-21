package dev.fincore.ingestion.application;

import java.time.LocalDate;
import java.util.UUID;

/**
 * O que {@code POST /imports} recebe (TDS 20.2). {@code referenceDate} é obrigatório nesta
 * implementação — nenhum documento define um cálculo de default seguro para quando
 * omitido, e inventar um seria adivinhar a identidade do arquivo (Domain §9.3).
 */
public record ImportFileCommand(
        String sourceCode,
        String originalFilename,
        byte[] content,
        LocalDate referenceDate,
        UUID reimportOfId,
        String reimportReason) {
}
