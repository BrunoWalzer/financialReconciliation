package dev.fincore.matching.domain.rule;

import dev.fincore.matching.domain.Decisiveness;
import dev.fincore.matching.domain.MatchingRule;
import dev.fincore.matching.domain.Predicate;
import dev.fincore.matching.domain.predicate.BothHaveCounterpartyDocumentPredicate;
import dev.fincore.matching.domain.predicate.BothUnclaimedPredicate;
import dev.fincore.matching.domain.predicate.CompatibleDirectionPredicate;
import dev.fincore.matching.domain.predicate.CompatibleTypePredicate;
import dev.fincore.matching.domain.predicate.NoUnresolvedIntegrityFlagPredicate;
import dev.fincore.matching.domain.predicate.NotPreviouslyRejectedPredicate;
import dev.fincore.matching.domain.predicate.SameCurrencyPredicate;
import dev.fincore.matching.domain.predicate.WithinSettlementWindowPredicate;
import java.util.List;

/**
 * Nível B — atributos compostos com unicidade mútua (TDS 11.5, Domain §12.4). Para quando
 * não há chave de correlação compartilhada. {@code BOTH_HAVE_COUNTERPARTY_DOCUMENT} é
 * obrigatório e não negociável (novo na v1.1, Implementation Plan FD-3/DR-2): sem documento
 * em qualquer lado, não há candidato — nunca derivado, nunca inferido.
 *
 * <p>Diferente do Nível A, aqui {@code WITHIN_SETTLEMENT_WINDOW} <b>é</b> mandatório: a
 * seleção do Domain §12.4 já inclui "data dentro da janela" como parte da própria seleção de
 * candidato, não como informação posterior.
 *
 * <p>A unicidade mútua (mandatória, decisiva) não é um {@link Predicate} — precisa do
 * conjunto inteiro de pares que fecham financeiramente para contar ocorrências por lado
 * (TDS 11.5, "só então contar ocorrências"), e por isso é responsabilidade do motor
 * ({@code MatchingEngine}), não desta classe de metadados.
 */
public final class RuleBCompositeMutualUnique implements MatchingRule {

    public static final String RULE_ID = "RULE_B_COMPOSITE_MUTUAL_UNIQUE";

    private static final List<Predicate> MANDATORY_PREDICATES = List.of(
            new SameCurrencyPredicate(),
            new CompatibleDirectionPredicate(),
            new CompatibleTypePredicate(),
            new BothUnclaimedPredicate(),
            new NoUnresolvedIntegrityFlagPredicate(),
            new NotPreviouslyRejectedPredicate(),
            new WithinSettlementWindowPredicate(),
            new BothHaveCounterpartyDocumentPredicate());

    @Override
    public String ruleId() {
        return RULE_ID;
    }

    @Override
    public int ruleVersion() {
        return 1;
    }

    @Override
    public int priority() {
        return 20;
    }

    @Override
    public Decisiveness decisiveness() {
        return Decisiveness.AUTO_MATCH;
    }

    @Override
    public List<Predicate> mandatoryPredicates() {
        return MANDATORY_PREDICATES;
    }
}
