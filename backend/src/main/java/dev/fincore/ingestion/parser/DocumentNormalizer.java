package dev.fincore.ingestion.parser;

import java.util.Optional;

/**
 * Documento da contraparte: só dígitos, com dígito verificador válido (Implementation Plan
 * M5) — CPF (11 dígitos) ou CNPJ (14 dígitos), os dois documentos brasileiros que os
 * layouts do MVP podem trazer. Máscara é removida; documento inválido é rejeitado, nunca
 * corrigido.
 */
public final class DocumentNormalizer {

    private DocumentNormalizer() {
    }

    public static Optional<String> normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String digitsOnly = raw.replaceAll("\\D", "");
        if (digitsOnly.length() == 11 && isValidCpf(digitsOnly)) {
            return Optional.of(digitsOnly);
        }
        if (digitsOnly.length() == 14 && isValidCnpj(digitsOnly)) {
            return Optional.of(digitsOnly);
        }
        return Optional.empty();
    }

    private static boolean isValidCpf(String cpf) {
        if (isAllSameDigit(cpf)) {
            return false;
        }
        int[] digits = toDigits(cpf);

        int sum1 = 0;
        for (int i = 0; i < 9; i++) {
            sum1 += digits[i] * (10 - i);
        }
        int dv1 = checkDigit(sum1);
        if (dv1 != digits[9]) {
            return false;
        }

        int sum2 = 0;
        for (int i = 0; i < 10; i++) {
            sum2 += digits[i] * (11 - i);
        }
        int dv2 = checkDigit(sum2);
        return dv2 == digits[10];
    }

    private static boolean isValidCnpj(String cnpj) {
        if (isAllSameDigit(cnpj)) {
            return false;
        }
        int[] digits = toDigits(cnpj);
        int[] weights1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        int[] weights2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

        int sum1 = 0;
        for (int i = 0; i < 12; i++) {
            sum1 += digits[i] * weights1[i];
        }
        int dv1 = checkDigit(sum1);
        if (dv1 != digits[12]) {
            return false;
        }

        int sum2 = 0;
        for (int i = 0; i < 13; i++) {
            sum2 += digits[i] * weights2[i];
        }
        int dv2 = checkDigit(sum2);
        return dv2 == digits[13];
    }

    private static int checkDigit(int sum) {
        int remainder = sum % 11;
        return remainder < 2 ? 0 : 11 - remainder;
    }

    private static boolean isAllSameDigit(String value) {
        char first = value.charAt(0);
        for (int i = 1; i < value.length(); i++) {
            if (value.charAt(i) != first) {
                return false;
            }
        }
        return true;
    }

    private static int[] toDigits(String value) {
        int[] digits = new int[value.length()];
        for (int i = 0; i < value.length(); i++) {
            digits[i] = value.charAt(i) - '0';
        }
        return digits;
    }
}
