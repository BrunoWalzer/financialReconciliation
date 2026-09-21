package dev.fincore.ingestion.parser;

import java.util.List;

/** {@code nsu;data_liquidacao;valor_bruto;valor_taxa;valor_liquido;bandeira;tipo_operacao;parcela} (Implementation Plan DR-1). */
public final class AcquirerSettlementLayout implements SourceLayout {

    private static final List<ColumnSpec> COLUMNS = List.of(
            new ColumnSpec("nsu", true),
            new ColumnSpec("data_liquidacao", true),
            new ColumnSpec("valor_bruto", true),
            new ColumnSpec("valor_taxa", true),
            new ColumnSpec("valor_liquido", true),
            new ColumnSpec("bandeira", true),
            new ColumnSpec("tipo_operacao", true),
            new ColumnSpec("parcela", true));

    @Override
    public List<ColumnSpec> columns() {
        return COLUMNS;
    }
}
