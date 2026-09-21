package dev.fincore.ingestion.parser;

import java.util.List;

/**
 * O contrato de entrada de uma fonte — posicional, contrato de integração, não modelo de
 * domínio (Domain §7.3). Cabeçalho errado é rejeitado estruturalmente; nenhuma
 * autodetecção de layout existe (TDS 9.4).
 */
public interface SourceLayout {

    List<ColumnSpec> columns();

    /** Compara o cabeçalho do arquivo, célula a célula e na ordem, contra {@link #columns()}. */
    default boolean matchesHeader(List<String> header) {
        List<String> expected = columns().stream().map(ColumnSpec::name).toList();
        if (header.size() != expected.size()) {
            return false;
        }
        for (int i = 0; i < expected.size(); i++) {
            if (!expected.get(i).equals(header.get(i).trim())) {
                return false;
            }
        }
        return true;
    }
}
