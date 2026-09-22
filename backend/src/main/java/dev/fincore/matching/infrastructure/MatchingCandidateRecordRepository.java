package dev.fincore.matching.infrastructure;

import dev.fincore.evidence.domain.FinancialRecord;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Consultas de bloqueio do horizonte de candidatos (TDS 11.5). Repositório <b>próprio de
 * {@code matching}</b> sobre a entidade {@code FinancialRecord} (dona: {@code evidence}) —
 * não é o mesmo objeto que {@code evidence.infrastructure.FinancialRecordRepository}, que
 * {@code matching} não pode referenciar (ArchUnit {@code REPOSITORIES_ARE_PRIVATE_TO_THEIR_MODULE},
 * TDS 4.3: "módulos conversam por serviço, não pelo repositório do vizinho"). Um segundo
 * repositório Spring Data sobre a mesma entidade, declarado dentro do módulo consumidor, é o
 * padrão já usado pelo grafo da TDS 4.2 (aresta {@code matching --> evidence} permite
 * depender do tipo de domínio, nunca do repositório alheio).
 *
 * <p>Cada consulta já exclui registros reivindicados ({@code NOT EXISTS ... MatchClaim}); as
 * duas que recebem o âncora inteiro (documento/pagamento e valor/data) também excluem o
 * próprio {@code id} do âncora. {@code byCorrelationKey} não recebe o âncora (a interface
 * {@code CandidateHorizon} só passa a chave) — {@code MatchingEngine} filtra o próprio
 * registro do resultado como segunda camada de defesa, independente da implementação do
 * horizonte. Bloqueio por índice existente em {@code financial_record} (M4/M7): nenhum
 * índice novo foi necessário (Implementation Plan M9, seção 33).
 */
public interface MatchingCandidateRecordRepository extends Repository<FinancialRecord, UUID> {

    @Query("""
            SELECT r FROM FinancialRecord r
            WHERE r.sourceId IN :sourceIds
              AND NOT EXISTS (SELECT 1 FROM MatchClaim c WHERE c.financialRecordId = r.id)
            """)
    List<FinancialRecord> findEligibleForSources(@Param("sourceIds") Collection<UUID> sourceIds);

    @Query("""
            SELECT r FROM FinancialRecord r
            WHERE r.correlationKey = :correlationKey
              AND NOT EXISTS (SELECT 1 FROM MatchClaim c WHERE c.financialRecordId = r.id)
            """)
    List<FinancialRecord> byCorrelationKey(@Param("correlationKey") String correlationKey);

    @Query("""
            SELECT r FROM FinancialRecord r
            WHERE r.sourceId <> :anchorSourceId
              AND r.id <> :excludeId
              AND (r.counterpartyDocument = :document OR (r.counterpartyDocument IS NULL AND :document IS NULL))
              AND (r.paymentMethod = :paymentMethod OR (r.paymentMethod IS NULL AND :paymentMethod IS NULL))
              AND r.businessDate BETWEEN :earliestDate AND :latestDate
              AND NOT EXISTS (SELECT 1 FROM MatchClaim c WHERE c.financialRecordId = r.id)
            """)
    List<FinancialRecord> byDocumentAndPaymentMethodWithinWindow(
            @Param("anchorSourceId") UUID anchorSourceId,
            @Param("excludeId") UUID excludeId,
            @Param("document") String document,
            @Param("paymentMethod") String paymentMethod,
            @Param("earliestDate") LocalDate earliestDate,
            @Param("latestDate") LocalDate latestDate);

    @Query("""
            SELECT r FROM FinancialRecord r
            WHERE r.sourceId <> :anchorSourceId
              AND r.id <> :excludeId
              AND r.grossAmountMinor = :grossAmountMinor
              AND r.businessDate BETWEEN :earliestDate AND :latestDate
              AND NOT EXISTS (SELECT 1 FROM MatchClaim c WHERE c.financialRecordId = r.id)
            """)
    List<FinancialRecord> byAmountAndDateWithinWindow(
            @Param("anchorSourceId") UUID anchorSourceId,
            @Param("excludeId") UUID excludeId,
            @Param("grossAmountMinor") long grossAmountMinor,
            @Param("earliestDate") LocalDate earliestDate,
            @Param("latestDate") LocalDate latestDate);
}
