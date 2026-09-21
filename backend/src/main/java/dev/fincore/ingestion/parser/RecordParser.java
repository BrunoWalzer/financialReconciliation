package dev.fincore.ingestion.parser;

/**
 * Único ponto de extensão do sistema para uma nova fonte (TDS 9.3) — deliberadamente
 * estreito: adicionar uma fonte não muda {@code matching}, {@code reconciliation},
 * {@code divergence} ou {@code analytics}.
 */
public interface RecordParser {

    String sourceCode();

    SourceLayout layout();

    ParseResult parseLine(RawLine line, ParseContext context);
}
