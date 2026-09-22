package dev.fincore.matching.domain;

/**
 * Um predicado comprovável sobre um par (TDS 11.2). Nunca um score, nunca um limiar — a
 * decisão automática só pode se apoiar em fatos binários e explicáveis (Domain §12.6).
 */
public interface Predicate {

    String name();

    PredicateResult test(RecordPair pair, EvaluationContext context);
}
