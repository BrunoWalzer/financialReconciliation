package dev.fincore.ingestion.parser;

import java.util.Optional;

/**
 * Converte um valor decimal textual em unidade mínima (centavos), usando os separadores
 * declarados da fonte — nunca heurística sobre o valor (Domain §10.3).
 *
 * <p><b>Ambiguidade é rejeição.</b> Sem uma parte fracionária de exatamente duas casas,
 * separada pelo separador decimal declarado, o valor é recusado — nunca se assume que
 * {@code "1,234"} significa 1,234 ou 1.234,00. Só {@code long}; nenhum {@code double}
 * participa desta conversão (TDS P3).
 */
public final class DecimalNormalizer {

    private DecimalNormalizer() {
    }

    public static Optional<Long> toMinorUnits(String raw, char decimalSeparator, char thousandsSeparator) {
        if (raw == null) {
            return Optional.empty();
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }

        boolean negative = false;
        String body = trimmed;
        if (body.charAt(0) == '-') {
            negative = true;
            body = body.substring(1);
        } else if (body.charAt(0) == '+') {
            body = body.substring(1);
        }
        if (body.isEmpty()) {
            return Optional.empty();
        }

        StringBuilder withoutThousands = new StringBuilder();
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c != thousandsSeparator) {
                withoutThousands.append(c);
            }
        }
        String noThousands = withoutThousands.toString();

        int decimalIndex = noThousands.indexOf(decimalSeparator);
        if (decimalIndex < 0) {
            // Sem parte decimal explícita: poderia ser 500 ou 5,00 — rejeita em vez de adivinhar.
            return Optional.empty();
        }
        if (noThousands.indexOf(decimalSeparator, decimalIndex + 1) >= 0) {
            return Optional.empty();
        }

        String integerPart = noThousands.substring(0, decimalIndex);
        String fractionalPart = noThousands.substring(decimalIndex + 1);
        if (fractionalPart.length() != 2) {
            // BRL tem exatamente duas casas decimais — qualquer outra contagem é ambígua.
            return Optional.empty();
        }
        if (integerPart.isEmpty()) {
            integerPart = "0";
        }
        if (!isDigitsOnly(integerPart) || !isDigitsOnly(fractionalPart)) {
            return Optional.empty();
        }

        try {
            long minor = Long.parseLong(integerPart + fractionalPart);
            return Optional.of(negative ? -minor : minor);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static boolean isDigitsOnly(String value) {
        if (value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
