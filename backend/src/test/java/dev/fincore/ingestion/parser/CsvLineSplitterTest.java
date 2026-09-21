package dev.fincore.ingestion.parser;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CsvLineSplitterTest {

    @Test
    void deveSepararPeloDelimitador() {
        assertThat(CsvLineSplitter.split("a;b;c")).containsExactly("a", "b", "c");
    }

    @Test
    void devePreservarCamposVaziosNoMeioENoFim() {
        assertThat(CsvLineSplitter.split("a;;c;")).containsExactly("a", "", "c", "");
    }

    @Test
    void devePreservarEspacosDentroDeUmCampo() {
        assertThat(CsvLineSplitter.split("Pedido loja online;b")).containsExactly("Pedido loja online", "b");
    }
}
