package dev.fincore.ingestion.parser;

import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * O que um parser precisa da {@code source} para normalizar uma linha (TDS 9.3) — fuso,
 * formatos de data e separadores, vindos da configuração, nunca do ambiente. O parser não
 * consulta banco: quem monta o contexto (o caso de uso) já leu a {@code Source} uma vez.
 */
public record ParseContext(
        UUID sourceId,
        ZoneId timezone,
        char decimalSeparator,
        char thousandsSeparator,
        List<String> dateFormats) {
}
