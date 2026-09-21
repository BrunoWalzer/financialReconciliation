package dev.fincore.ingestion.parser;

/** Uma coluna do layout posicional de uma fonte (TDS 9.3). */
public record ColumnSpec(String name, boolean required) {
}
