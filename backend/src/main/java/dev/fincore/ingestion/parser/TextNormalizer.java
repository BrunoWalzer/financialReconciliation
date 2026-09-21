package dev.fincore.ingestion.parser;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Texto livre: trim e colapso de espaços na cópia preservada; caixa e acentuação só na
 * cópia usada para comparação, nunca no original (Domain §10.2).
 */
public final class TextNormalizer {

    private static final Pattern MULTIPLE_SPACES = Pattern.compile("\\s+");
    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}");

    private TextNormalizer() {
    }

    /** Trim e colapso de espaços múltiplos — a forma preservada como evidência. */
    public static String clean(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return MULTIPLE_SPACES.matcher(trimmed).replaceAll(" ");
    }

    /** Maiúsculas, sem acento — só para comparação; nunca substitui o campo original. */
    public static String forComparison(String raw) {
        String cleaned = clean(raw);
        if (cleaned == null) {
            return null;
        }
        String decomposed = Normalizer.normalize(cleaned, Normalizer.Form.NFD);
        return COMBINING_MARKS.matcher(decomposed).replaceAll("").toUpperCase(java.util.Locale.ROOT);
    }
}
