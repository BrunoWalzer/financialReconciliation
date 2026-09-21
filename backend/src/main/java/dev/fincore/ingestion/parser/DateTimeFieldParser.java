package dev.fincore.ingestion.parser;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

/**
 * Interpreta um campo de data/hora contra a lista ordenada de formatos declarados da fonte
 * (Domain §10.2) — nunca adivinha um formato não declarado. Data impossível ou fora de uma
 * faixa plausível é rejeitada (Domain §10.4).
 *
 * <p><b>Verificação por ida-e-volta, não {@code ResolverStyle.STRICT}.</b> O padrão
 * {@code SMART} do Java "corrige" 31 de fevereiro para 28 silenciosamente — exatamente a
 * adivinhação que o Domain §10.4 proíbe — mas {@code STRICT} com o padrão {@code yyyy}
 * (ano da era, não ano ISO) falha em resolver <em>qualquer</em> data, mesmo válida, sem um
 * literal de era no padrão — e os formatos já semeados em {@code source.date_formats}
 * (V3/V5) usam {@code yyyy}. Em vez de reescrever o padrão para {@code uuuu} (mais uma
 * correção de dado de seed), reformata a data já interpretada com o mesmo padrão e compara
 * com a entrada: se não bater, o valor não sobreviveria a uma volta exata e foi corrigido
 * silenciosamente — rejeita.
 */
public final class DateTimeFieldParser {

    private static final int MIN_PLAUSIBLE_YEAR = 2000;
    private static final int MAX_PLAUSIBLE_YEAR = 2100;

    private DateTimeFieldParser() {
    }

    public static Optional<LocalDateTime> parseDateTime(String raw, List<String> patterns) {
        String trimmed = trimOrNull(raw);
        if (trimmed == null) {
            return Optional.empty();
        }
        for (String pattern : patterns) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
            try {
                LocalDateTime parsed = LocalDateTime.parse(trimmed, formatter);
                if (isPlausible(parsed.getYear()) && parsed.format(formatter).equals(trimmed)) {
                    return Optional.of(parsed);
                }
            } catch (DateTimeParseException ignored) {
                // Tenta o próximo formato declarado.
            }
        }
        return Optional.empty();
    }

    public static Optional<LocalDate> parseDate(String raw, List<String> patterns) {
        String trimmed = trimOrNull(raw);
        if (trimmed == null) {
            return Optional.empty();
        }
        for (String pattern : patterns) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
            try {
                LocalDate parsed = LocalDate.parse(trimmed, formatter);
                if (isPlausible(parsed.getYear()) && parsed.format(formatter).equals(trimmed)) {
                    return Optional.of(parsed);
                }
            } catch (DateTimeParseException ignored) {
                // Tenta o próximo formato declarado.
            }
        }
        return Optional.empty();
    }

    private static boolean isPlausible(int year) {
        return year >= MIN_PLAUSIBLE_YEAR && year <= MAX_PLAUSIBLE_YEAR;
    }

    private static String trimOrNull(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
