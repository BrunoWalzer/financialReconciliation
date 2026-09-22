package dev.fincore.matching.domain;

import java.util.List;

/**
 * O que {@link MatchingEngine#evaluate} devolve (TDS 11.1). Sem {@code recompositions}: a
 * fase P1/recomposição pertence ao M13, fora do escopo deste milestone.
 */
public record MatchingDecisions(
        List<MatchProposal> automaticMatches,
        List<AmbiguitySet> ambiguities,
        List<SuggestionSet> suggestions,
        List<OrphanClassification> orphans) {

    public MatchingDecisions {
        automaticMatches = List.copyOf(automaticMatches);
        ambiguities = List.copyOf(ambiguities);
        suggestions = List.copyOf(suggestions);
        orphans = List.copyOf(orphans);
    }
}
