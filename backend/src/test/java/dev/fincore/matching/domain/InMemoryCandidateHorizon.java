package dev.fincore.matching.domain;

import dev.fincore.evidence.domain.FinancialRecord;
import java.util.List;

/**
 * O horizonte "alimentado por listas em memória" que o TDS 11.1 pede para teste: um único
 * conjunto de candidatos, fixo, que <b>não muda com o tamanho do escopo</b> — é exatamente
 * essa independência que prova ADR-007 (horizonte nunca vem do escopo da execução).
 *
 * <p>Sem bloqueio grosseiro aqui de propósito: em produção o SQL de blocking (TDS 11.5)
 * reduz o espaço de busca por índice; em teste, a redução não importa — o que importa é que
 * os predicados e regras decidam exatamente igual, com ou sem ela.
 */
public final class InMemoryCandidateHorizon implements CandidateHorizon {

    private final List<FinancialRecord> pool;

    public InMemoryCandidateHorizon(List<FinancialRecord> pool) {
        this.pool = List.copyOf(pool);
    }

    @Override
    public List<FinancialRecord> byCorrelationKey(String correlationKey) {
        return pool.stream().filter(r -> correlationKey.equals(r.correlationKey())).toList();
    }

    @Override
    public List<FinancialRecord> byDocumentAndPaymentMethodWithinWindow(FinancialRecord scopeRecord) {
        return pool;
    }

    @Override
    public List<FinancialRecord> byAmountAndDateWithinWindow(FinancialRecord scopeRecord) {
        return pool;
    }
}
