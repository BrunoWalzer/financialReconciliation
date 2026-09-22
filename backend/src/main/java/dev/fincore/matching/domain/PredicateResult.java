package dev.fincore.matching.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * O resultado de avaliar um {@link Predicate} sobre um par (TDS 11.2). {@code detail}
 * carrega os valores que justificam o resultado — é o que torna a evidência de um match
 * auditável sem depender de log (TDS 11.8: "rule = LEVEL_A sozinho é insuficiente").
 */
public record PredicateResult(String name, boolean passed, Map<String, Object> detail) {

    public PredicateResult {
        // Map.copyOf rejeita valores nulos, mas um detalhe como "paymentMethod ausente"
        // é evidência legítima (ex.: WITHIN_SETTLEMENT_WINDOW quando nenhum dos dois lados
        // declara meio de pagamento) — não pode virar exceção nem virar string mágica.
        detail = Collections.unmodifiableMap(new LinkedHashMap<>(detail));
    }

    public static PredicateResult pass(String name, Map<String, Object> detail) {
        return new PredicateResult(name, true, detail);
    }

    public static PredicateResult fail(String name, Map<String, Object> detail) {
        return new PredicateResult(name, false, detail);
    }
}
