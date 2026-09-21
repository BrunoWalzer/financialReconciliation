package dev.fincore.ingestion.parser;

import java.util.List;

/** Uma linha do arquivo já separada pelo delimitador {@code ;}, com o texto original intacto. */
public record RawLine(int lineNumber, String rawText, List<String> fields) {
}
