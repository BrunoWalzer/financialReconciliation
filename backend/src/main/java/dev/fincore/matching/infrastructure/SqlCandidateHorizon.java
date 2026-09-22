package dev.fincore.matching.infrastructure;

import dev.fincore.evidence.domain.FinancialRecord;
import dev.fincore.matching.domain.CandidateHorizon;
import dev.fincore.matching.domain.SettlementWindowResolver;
import dev.fincore.shared.configuration.RunConfigSnapshot;
import dev.fincore.shared.configuration.RunConfigSnapshot.SettlementWindowSnapshot;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * O horizonte de candidatos real (TDS 11.1, 11.5), por trás das consultas de bloqueio de
 * {@link MatchingCandidateRecordRepository}. ADR-007 (independência do escopo): cada método
 * bloqueia só pela própria âncora — chave de correlação, ou documento/pagamento/janela, ou
 * valor/janela — nunca pelo tamanho do {@code RecordScope} da execução corrente. A janela de
 * datas usada para bloquear é a mesma resolvida por {@link SettlementWindowResolver}, para
 * nunca ser mais estreita que o que o próprio predicado {@code WITHIN_SETTLEMENT_WINDOW}
 * aceitaria (perderia candidato) nem gratuitamente mais ampla (cresceria sem necessidade).
 *
 * <p>Não implementa {@code matching.domain.CandidateHorizon} com Spring Data Specification
 * porque as três formas de bloqueio da TDS 11.5 são fixas e conhecidas — três consultas
 * nomeadas são mais legíveis que um construtor de predicados genérico para um conjunto que
 * não muda.
 */
public final class SqlCandidateHorizon implements CandidateHorizon {

    private final MatchingCandidateRecordRepository repository;
    private final RunConfigSnapshot config;

    public SqlCandidateHorizon(MatchingCandidateRecordRepository repository, RunConfigSnapshot config) {
        this.repository = Objects.requireNonNull(repository, "repository é obrigatório");
        this.config = Objects.requireNonNull(config, "config é obrigatório");
    }

    /**
     * A interface só recebe a chave, não o registro âncora inteiro — não há {@code id} para
     * excluir aqui. Não é um problema: {@code MatchingEngine} já filtra o próprio âncora do
     * resultado como segunda camada de defesa, independente da implementação do horizonte
     * (ver {@code umRegistroNuncaEhCandidatoDeSiMesmo} em {@code MatchingEngineTest}) — esta
     * consulta pode devolver o próprio âncora sem quebrar a decisão final.
     */
    @Override
    public List<FinancialRecord> byCorrelationKey(String correlationKey) {
        if (correlationKey == null || correlationKey.isBlank()) {
            return List.of();
        }
        return repository.byCorrelationKey(correlationKey);
    }

    @Override
    public List<FinancialRecord> byDocumentAndPaymentMethodWithinWindow(FinancialRecord anchor) {
        Optional<SettlementWindowSnapshot> window = SettlementWindowResolver.resolve(config, anchor.paymentMethod());
        if (window.isEmpty()) {
            return List.of();
        }
        LocalDate earliest = anchor.businessDate().plusDays(window.get().minDays());
        LocalDate latest = anchor.businessDate().plusDays(window.get().maxDays());
        return repository.byDocumentAndPaymentMethodWithinWindow(
                anchor.sourceId(), anchor.id(), anchor.counterpartyDocument(), anchor.paymentMethod(), earliest, latest);
    }

    @Override
    public List<FinancialRecord> byAmountAndDateWithinWindow(FinancialRecord anchor) {
        Optional<SettlementWindowSnapshot> window = SettlementWindowResolver.resolve(config, anchor.paymentMethod());
        if (window.isEmpty()) {
            return List.of();
        }
        LocalDate earliest = anchor.businessDate().plusDays(window.get().minDays());
        LocalDate latest = anchor.businessDate().plusDays(window.get().maxDays());
        return repository.byAmountAndDateWithinWindow(
                anchor.sourceId(), anchor.id(), anchor.grossAmount().amountMinor(), earliest, latest);
    }
}
