package dev.fincore.matching.domain;

import dev.fincore.evidence.domain.FinancialRecord;
import dev.fincore.matching.domain.rule.RuleACorrelationKey;
import dev.fincore.matching.domain.rule.RuleBCompositeMutualUnique;
import dev.fincore.matching.domain.rule.RuleCAmountDateSuggestion;
import dev.fincore.shared.configuration.RunConfigSnapshot.SettlementWindowSnapshot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * O motor de correspondência (TDS 11.1). Função pura: nenhum banco, nenhum relógio
 * ({@link #evaluate} recebe {@code evaluationDate}/{@code cutoff} dentro de
 * {@link EvaluationContext}, nunca lê {@code Instant.now()}), nenhum aleatório. Devolve
 * decisões; não persiste.
 *
 * <p>Níveis avaliados em ordem — A, depois B, depois C (Domain §12.7). Um registro
 * correspondido num nível sai do conjunto elegível dos seguintes.
 */
public final class MatchingEngine {

    private final RuleACorrelationKey ruleA = new RuleACorrelationKey();
    private final RuleBCompositeMutualUnique ruleB = new RuleBCompositeMutualUnique();
    private final RuleCAmountDateSuggestion ruleC = new RuleCAmountDateSuggestion();

    public MatchingDecisions evaluate(RecordScope scope, CandidateHorizon horizon, EvaluationContext context) {
        List<FinancialRecord> scopeRecords = scope.records().stream()
                .sorted(DeterministicOrder.RECORD_ORDER)
                .toList();

        Set<UUID> capturedThisRun = new HashSet<>();
        List<MatchProposal> automaticMatches = new ArrayList<>();
        List<AmbiguitySet> ambiguities = new ArrayList<>();

        List<FinancialRecord> afterA = evaluateLevelA(scopeRecords, horizon, context, capturedThisRun, automaticMatches);
        List<FinancialRecord> afterB = evaluateLevelB(afterA, horizon, context, capturedThisRun, automaticMatches, ambiguities);
        List<SuggestionSet> suggestions = evaluateLevelC(afterB, horizon, context, capturedThisRun);
        List<OrphanClassification> orphans = classifyOrphans(afterB, context);

        return new MatchingDecisions(automaticMatches, ambiguities, suggestions, orphans);
    }

    // ------------------------------------------------------------ Nível A

    private List<FinancialRecord> evaluateLevelA(
            List<FinancialRecord> scopeRecords, CandidateHorizon horizon, EvaluationContext context,
            Set<UUID> capturedThisRun, List<MatchProposal> automaticMatches) {

        List<FinancialRecord> remaining = new ArrayList<>();
        for (FinancialRecord left : scopeRecords) {
            // Um registro carregado nos dois lados do escopo (caso realista: o orquestrador
            // nao sabe de antemao qual lado e "esquerdo") pode ja ter sido capturado como par
            // de um ancora anterior, na mesma passada — reprocessa-lo aqui o devolveria a
            // "remaining" e o vazaria para Nivel B/C/orfaos como se nunca tivesse casado.
            if (capturedThisRun.contains(left.id())) {
                continue;
            }
            if (isBlank(left.correlationKey())) {
                remaining.add(left);
                continue;
            }
            List<FinancialRecord> candidates = horizon.byCorrelationKey(left.correlationKey()).stream()
                    .filter(r -> !capturedThisRun.contains(r.id()))
                    .filter(r -> !r.id().equals(left.id()))
                    .sorted(DeterministicOrder.RECORD_ORDER)
                    .toList();
            if (candidates.isEmpty()) {
                remaining.add(left);
                continue;
            }

            RecordPair pair = new RecordPair(left, candidates.get(0));
            List<PredicateResult> predicateResults = evaluatePredicates(ruleA.mandatoryPredicates(), pair, context);
            boolean mandatoryPassed = predicateResults.stream().allMatch(PredicateResult::passed);

            // WITHIN_SETTLEMENT_WINDOW não é mandatório no Nível A (TDS 11.4) mas entra na
            // evidência mesmo assim — o operador precisa ver o prazo mesmo quando ele não
            // bloqueou a decisão.
            List<PredicateResult> evidencePredicates = new ArrayList<>(predicateResults);
            evidencePredicates.add(new dev.fincore.matching.domain.predicate.WithinSettlementWindowPredicate().test(pair, context));

            if (mandatoryPassed) {
                AmountEvaluation amountEvaluation =
                        AmountEvaluation.evaluate(pair.left(), pair.right(), List.of(), context.config());
                automaticMatches.add(buildProposal(
                        ruleA.ruleId(), ruleA.ruleVersion(), pair, List.of(), evidencePredicates, amountEvaluation, context));
                capturedThisRun.add(left.id());
                capturedThisRun.add(candidates.get(0).id());
            } else {
                remaining.add(left);
            }
        }
        return remaining;
    }

    // ------------------------------------------------------------ Nível B

    private record PairEvaluation(RecordPair pair, List<PredicateResult> predicates, AmountEvaluation amountEvaluation) {
    }

    private List<FinancialRecord> evaluateLevelB(
            List<FinancialRecord> scopeRecords, CandidateHorizon horizon, EvaluationContext context,
            Set<UUID> capturedThisRun, List<MatchProposal> automaticMatches, List<AmbiguitySet> ambiguities) {

        List<PairEvaluation> closingPairs = new ArrayList<>();
        for (FinancialRecord left : scopeRecords) {
            List<FinancialRecord> candidates = horizon.byDocumentAndPaymentMethodWithinWindow(left).stream()
                    .filter(r -> !capturedThisRun.contains(r.id()))
                    .filter(r -> !r.id().equals(left.id()))
                    .sorted(DeterministicOrder.RECORD_ORDER)
                    .toList();
            for (FinancialRecord right : candidates) {
                RecordPair pair = new RecordPair(left, right);
                List<PredicateResult> predicateResults = evaluatePredicates(ruleB.mandatoryPredicates(), pair, context);
                if (!predicateResults.stream().allMatch(PredicateResult::passed)) {
                    continue;
                }
                AmountEvaluation amountEvaluation = AmountEvaluation.evaluate(left, right, List.of(), context.config());
                // TDS 11.5: "descartar os que não fecham" — ao contrário do Nível A, um par
                // que não fecha financeiramente nem é candidato ao Nível B.
                if (amountEvaluation.outcome() == AmountEvaluation.Outcome.PAIRED_WITH_DIVERGENCE) {
                    continue;
                }
                closingPairs.add(new PairEvaluation(pair, predicateResults, amountEvaluation));
            }
        }

        Map<UUID, List<PairEvaluation>> byLeft = closingPairs.stream()
                .collect(Collectors.groupingBy(pe -> pe.pair().left().id()));
        Map<UUID, List<PairEvaluation>> byRight = closingPairs.stream()
                .collect(Collectors.groupingBy(pe -> pe.pair().right().id()));

        List<PairEvaluation> sortedClosingPairs = closingPairs.stream()
                .sorted(Comparator
                        .<PairEvaluation, FinancialRecord>comparing(pe -> pe.pair().left(), DeterministicOrder.RECORD_ORDER)
                        .thenComparing(pe -> pe.pair().right(), DeterministicOrder.RECORD_ORDER))
                .toList();

        Set<UUID> matchedInB = new HashSet<>();
        Set<UUID> anchoredAmbiguity = new HashSet<>();
        for (PairEvaluation pe : sortedClosingPairs) {
            UUID leftId = pe.pair().left().id();
            UUID rightId = pe.pair().right().id();
            if (matchedInB.contains(leftId) || matchedInB.contains(rightId)) {
                continue;
            }
            boolean mutuallyUnique = byLeft.get(leftId).size() == 1 && byRight.get(rightId).size() == 1;
            if (mutuallyUnique) {
                automaticMatches.add(buildProposal(
                        ruleB.ruleId(), ruleB.ruleVersion(), pe.pair(), List.of(), pe.predicates(), pe.amountEvaluation(), context));
                matchedInB.add(leftId);
                matchedInB.add(rightId);
                capturedThisRun.add(leftId);
                capturedThisRun.add(rightId);
            } else if (anchoredAmbiguity.add(leftId)) {
                List<FinancialRecord> allCandidates = byLeft.get(leftId).stream()
                        .map(x -> x.pair().right())
                        .sorted(DeterministicOrder.RECORD_ORDER)
                        .toList();
                ambiguities.add(new AmbiguitySet(pe.pair().left(), allCandidates, ruleB.ruleId()));
            }
        }

        return scopeRecords.stream().filter(r -> !matchedInB.contains(r.id())).toList();
    }

    // ------------------------------------------------------------ Nível C

    private List<SuggestionSet> evaluateLevelC(
            List<FinancialRecord> scopeRecords, CandidateHorizon horizon, EvaluationContext context, Set<UUID> capturedThisRun) {

        List<SuggestionSet> suggestions = new ArrayList<>();
        for (FinancialRecord left : scopeRecords) {
            List<FinancialRecord> candidates = horizon.byAmountAndDateWithinWindow(left).stream()
                    .filter(r -> !capturedThisRun.contains(r.id()))
                    .filter(r -> !r.id().equals(left.id()))
                    .filter(r -> allPassed(ruleC.mandatoryPredicates(), new RecordPair(left, r), context))
                    .sorted(DeterministicOrder.RECORD_ORDER)
                    .limit(RuleCAmountDateSuggestion.MAX_CANDIDATES_PER_RECORD)
                    .toList();
            if (!candidates.isEmpty()) {
                suggestions.add(new SuggestionSet(left, candidates, ruleC.ruleId()));
            }
        }
        return suggestions;
    }

    // ------------------------------------------------------------ Órfãos

    private List<OrphanClassification> classifyOrphans(List<FinancialRecord> remaining, EvaluationContext context) {
        List<OrphanClassification> orphans = new ArrayList<>();
        for (FinancialRecord record : remaining) {
            Optional<SettlementWindowSnapshot> window =
                    SettlementWindowResolver.resolve(context.config(), record.paymentMethod());
            OrphanClassification.Status status = window
                    .map(w -> record.businessDate().plusDays(w.maxDays()).isBefore(context.evaluationDate())
                            ? OrphanClassification.Status.ABSENCE_CANDIDATE
                            : OrphanClassification.Status.PENDING_SETTLEMENT)
                    // Sem janela configurada para o meio: não há como provar que ainda é
                    // cedo para cobrar — classificado como candidato a ausência.
                    .orElse(OrphanClassification.Status.ABSENCE_CANDIDATE);
            orphans.add(new OrphanClassification(record, status));
        }
        return orphans;
    }

    // ------------------------------------------------------------ auxiliares

    private static boolean allPassed(List<Predicate> predicates, RecordPair pair, EvaluationContext context) {
        for (Predicate predicate : predicates) {
            if (!predicate.test(pair, context).passed()) {
                return false;
            }
        }
        return true;
    }

    private static List<PredicateResult> evaluatePredicates(List<Predicate> predicates, RecordPair pair, EvaluationContext context) {
        List<PredicateResult> results = new ArrayList<>();
        for (Predicate predicate : predicates) {
            results.add(predicate.test(pair, context));
        }
        return results;
    }

    private static MatchProposal buildProposal(
            String ruleId, int ruleVersion, RecordPair pair, List<FinancialRecord> components,
            List<PredicateResult> predicateResults, AmountEvaluation amountEvaluation, EvaluationContext context) {
        MatchEvidence evidence = new MatchEvidence(
                ruleId, ruleVersion, context.ruleSetVersion(), predicateResults,
                MatchEvidence.AmountEvaluationEvidence.from(amountEvaluation),
                components.stream().map(FinancialRecord::id).toList());
        return new MatchProposal(pair.left(), pair.right(), components, ruleId, ruleVersion, evidence);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
