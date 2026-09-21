package dev.fincore.ingestion.parser;

import java.util.List;
import java.util.regex.Pattern;

/** Separa uma linha pelo delimitador {@code ;} do contrato do MVP — sem quoting/escaping, que os dois layouts aprovados não definem. */
public final class CsvLineSplitter {

    public static final char DELIMITER = ';';
    private static final Pattern SPLIT_PATTERN = Pattern.compile(";", Pattern.LITERAL);

    private CsvLineSplitter() {
    }

    /** {@code -1} no limite preserva campos vazios à direita (ex.: {@code "a;b;"} tem 3 campos, não 2). */
    public static List<String> split(String line) {
        return List.of(SPLIT_PATTERN.split(line, -1));
    }
}
