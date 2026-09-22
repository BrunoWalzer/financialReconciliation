package dev.fincore.matching.domain;

import java.util.List;

/**
 * Identidade e metadados de uma regra de matching (TDS 11.2). As regras do MVP são código +
 * versão explícita — não há tabela de regras nem motor de regras editável (TDS 11.2: "isso
 * não tornaria nada configurável sem uma DSL, que está fora de escopo").
 */
public interface MatchingRule {

    String ruleId();

    int ruleVersion();

    int priority();

    Decisiveness decisiveness();

    /** Predicados que bloqueiam o candidato se falharem, para esta regra. */
    List<Predicate> mandatoryPredicates();
}
