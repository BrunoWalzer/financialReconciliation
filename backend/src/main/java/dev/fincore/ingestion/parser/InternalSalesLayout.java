package dev.fincore.ingestion.parser;

import java.util.List;

/** {@code pedido_id;nsu;data_hora;valor_bruto;meio_pagamento;documento_cliente;tipo;descricao} (Implementation Plan DR-1). */
public final class InternalSalesLayout implements SourceLayout {

    private static final List<ColumnSpec> COLUMNS = List.of(
            new ColumnSpec("pedido_id", true),
            new ColumnSpec("nsu", false),
            new ColumnSpec("data_hora", true),
            new ColumnSpec("valor_bruto", true),
            new ColumnSpec("meio_pagamento", true),
            new ColumnSpec("documento_cliente", false),
            new ColumnSpec("tipo", true),
            new ColumnSpec("descricao", false));

    @Override
    public List<ColumnSpec> columns() {
        return COLUMNS;
    }
}
