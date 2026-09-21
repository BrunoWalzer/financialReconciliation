package dev.fincore.ingestion.parser;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class CsvEncodingDetectorTest {

    @Test
    void deveDetectarUtf8SemBom() {
        byte[] content = "café;açúcar".getBytes(StandardCharsets.UTF_8);

        var detection = CsvEncodingDetector.detect(content);

        assertThat(detection.charset()).isEqualTo(StandardCharsets.UTF_8);
        assertThat(detection.bomLength()).isZero();
    }

    @Test
    void deveDetectarUtf8ComBomEIgnorarOBom() {
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] text = "café;açúcar".getBytes(StandardCharsets.UTF_8);
        byte[] content = new byte[bom.length + text.length];
        System.arraycopy(bom, 0, content, 0, bom.length);
        System.arraycopy(text, 0, content, bom.length, text.length);

        var detection = CsvEncodingDetector.detect(content);

        assertThat(detection.charset()).isEqualTo(StandardCharsets.UTF_8);
        assertThat(detection.bomLength()).isEqualTo(3);
    }

    @Test
    void deveCairParaIso88591QuandoNaoEUtf8Valido() {
        // 0xE7 sozinho é 'ç' em ISO-8859-1, mas não é uma sequência UTF-8 válida isolada.
        byte[] content = {(byte) 'c', (byte) 0xE7, (byte) 'a'};

        var detection = CsvEncodingDetector.detect(content);

        assertThat(detection.charset()).isEqualTo(StandardCharsets.ISO_8859_1);
    }
}
