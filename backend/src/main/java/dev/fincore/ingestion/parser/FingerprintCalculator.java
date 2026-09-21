package dev.fincore.ingestion.parser;

import dev.fincore.evidence.domain.Direction;
import dev.fincore.evidence.domain.RecordType;
import dev.fincore.shared.money.Currency;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;

/**
 * SHA-256 determinístico sobre os campos de negócio normalizados, em ordem fixa
 * (Implementation Plan M5). Identifica; <b>nunca</b> autoriza descartar evidência (Domain
 * §9.3) — não há {@code UNIQUE} sobre esta coluna em nenhuma migration.
 *
 * <p>Ordem fixa e documentada: fonte, identificador de origem, chave de correlação,
 * direção, tipo, valor bruto, moeda, data de negócio, documento da contraparte, meio de
 * pagamento — os campos que descrevem o fato financeiro, nunca o texto livre
 * ({@code description}/{@code rawLine}) nem metadados de importação (que mudariam a cada
 * reenvio do mesmo arquivo, quebrando "mesmo conteúdo canônico → mesmo fingerprint").
 */
public final class FingerprintCalculator {

    private FingerprintCalculator() {
    }

    public static String calculate(
            String sourceCode,
            String externalId,
            String correlationKey,
            Direction direction,
            RecordType recordType,
            long grossAmountMinor,
            Currency currency,
            LocalDate businessDate,
            String counterpartyDocument,
            String paymentMethod) {

        String canonical = String.join("|",
                sourceCode,
                nullToEmpty(externalId),
                nullToEmpty(correlationKey),
                direction.name(),
                recordType.name(),
                Long.toString(grossAmountMinor),
                currency.name(),
                businessDate.toString(),
                nullToEmpty(counterpartyDocument),
                nullToEmpty(paymentMethod));

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível na JVM", e);
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
