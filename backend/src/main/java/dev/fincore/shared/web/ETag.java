package dev.fincore.shared.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * {@code ETag} no GET, {@code If-Match} obrigatório em mutação versionada (TDS 19.1).
 * O valor é sempre a versão do optimistic locking (JPA {@code @Version}), como texto
 * entre aspas — formato de ETag exigido pelo HTTP, sem peso próprio de negócio.
 */
public final class ETag {

    private ETag() {
    }

    public static String quote(long version) {
        return "\"" + version + "\"";
    }

    /** @throws ResponseStatusException 400 se o cabeçalho não for um número (aspas opcionais) */
    public static long parseIfMatch(String header) {
        String trimmed = header.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "If-Match inválido");
        }
    }
}
