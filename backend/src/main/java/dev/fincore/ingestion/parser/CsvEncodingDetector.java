package dev.fincore.ingestion.parser;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;

/**
 * UTF-8 ou ISO-8859-1, com detecção (TDS 9.1) — nunca o padrão da JVM/máquina. ISO-8859-1
 * decodifica qualquer sequência de bytes sem erro (é de um byte por caractere), então a
 * detecção real é "isto é UTF-8 válido? Senão, ISO-8859-1".
 */
public final class CsvEncodingDetector {

    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private CsvEncodingDetector() {
    }

    public static Detection detect(byte[] content) {
        int offset = startsWithUtf8Bom(content) ? UTF8_BOM.length : 0;
        boolean validUtf8 = isValidUtf8(content, offset, content.length - offset);
        var charset = validUtf8 ? StandardCharsets.UTF_8 : StandardCharsets.ISO_8859_1;
        return new Detection(charset, offset);
    }

    private static boolean startsWithUtf8Bom(byte[] content) {
        if (content.length < UTF8_BOM.length) {
            return false;
        }
        for (int i = 0; i < UTF8_BOM.length; i++) {
            if (content[i] != UTF8_BOM[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean isValidUtf8(byte[] content, int offset, int length) {
        // decode(ByteBuffer) — a sobrecarga de conveniência — ignora a ação de erro
        // configurada no decoder e sempre lança em entrada malformada; é o jeito correto
        // de validar de uma vez, sem precisar de um CharBuffer de saída explícito.
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
        try {
            decoder.decode(ByteBuffer.wrap(content, offset, length));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }

    /** {@code bomLength} bytes a pular antes de decodificar — 0 quando não há BOM. */
    public record Detection(java.nio.charset.Charset charset, int bomLength) {
    }
}
