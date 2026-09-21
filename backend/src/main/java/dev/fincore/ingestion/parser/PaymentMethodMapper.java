package dev.fincore.ingestion.parser;

/**
 * {@code meio_pagamento} → {@code payment_method}: mapeamento direto (Implementation Plan
 * DR-1 não define transformação de vocabulário). Só limpeza de texto — canonicalizar para
 * o vocabulário de {@code fee_rule}/{@code settlement_window} é decisão do motor de
 * matching (M9), fora de escopo aqui.
 */
public final class PaymentMethodMapper {

    private PaymentMethodMapper() {
    }

    public static String normalize(String raw) {
        return TextNormalizer.clean(raw);
    }
}
