package dev.fincore.matching.application;

/**
 * O resumo de uma execução de {@link EvaluateMatchesUseCase} — sem um {@code ReconciliationRun}
 * para anexar (M11), o resultado é devolvido diretamente a quem chamou, não persistido.
 *
 * @param claimConflicts quantas propostas automáticas perderam a corrida de {@code match_claim}
 *     (I-5) para outra execução concorrente — não é erro, é a exclusividade funcionando.
 */
public record EvaluateMatchesResult(
        int matchesCreated, int claimConflicts, int ambiguities, int suggestions, int orphans) {
}
