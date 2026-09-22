package dev.fincore.matching.application;

import dev.fincore.audit.domain.ActorRef;
import dev.fincore.evidence.domain.FinancialRecord;
import dev.fincore.evidence.domain.RecordIntegrityFlag;
import dev.fincore.evidence.domain.RecordIntegrityFlagType;
import dev.fincore.matching.domain.CandidateHorizon;
import dev.fincore.matching.domain.EvaluationContext;
import dev.fincore.matching.domain.MatchProposal;
import dev.fincore.matching.domain.MatchingDecisions;
import dev.fincore.matching.domain.MatchingEngine;
import dev.fincore.matching.domain.RecordScope;
import dev.fincore.matching.domain.RejectedPair;
import dev.fincore.matching.infrastructure.MatchClaimRepository;
import dev.fincore.matching.infrastructure.MatchRejectionRepository;
import dev.fincore.matching.infrastructure.MatchingCandidateRecordRepository;
import dev.fincore.matching.infrastructure.MatchingIntegrityFlagRepository;
import dev.fincore.matching.infrastructure.SqlCandidateHorizon;
import dev.fincore.shared.configuration.RunConfigSnapshot;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * A orquestração mínima do M9 (Implementation Plan M9/M10, seção "caso de uso
 * orquestrador"): carrega os registros elegíveis, monta o {@link EvaluationContext} a partir
 * do banco, roda {@link MatchingEngine#evaluate}, persiste os matches automáticos um por um
 * (cada um sua própria transação, via {@link ClaimAndPersistMatchUseCase}) e devolve um
 * resumo. <b>Não é</b> o ciclo de vida de {@code ReconciliationRun} (fases, estado
 * persistido, disparo por API) — isso é M11; aqui não há run para anexar o resultado.
 *
 * <p>Recebe {@code config}, {@code now}, {@code evaluationDate} e {@code cutoff} como
 * parâmetros, nunca os lê sozinho: {@code matching} não pode depender de
 * {@code configuration} (TDS P4, ArchUnit {@code MATCHING_DOES_NOT_DEPEND_ON_CONFIGURATION})
 * nem ler o relógio (TDS 11.7, ArchUnit {@code MATCHING_DOES_NOT_READ_THE_CLOCK}) — a
 * restrição vale para o módulo inteiro, não só para {@code matching.domain}. Quem chama isto
 * (um teste hoje; um futuro orquestrador de {@code ReconciliationRun} no M11) monta o
 * snapshot via {@code ConfigSnapshotFactory} e decide o instante da execução.
 *
 * <p>Sem {@code @PreAuthorize}: não existe fronteira HTTP nesta milestone (mesmo raciocínio
 * de {@code ProcessImportBatchUseCase} no M8) — quando M11 expuser um disparo real, a
 * autorização entra na camada que o expõe.
 */
@Service
public class EvaluateMatchesUseCase {

    private final MatchingCandidateRecordRepository candidateRecordRepository;
    private final MatchClaimRepository claimRepository;
    private final MatchingIntegrityFlagRepository integrityFlagRepository;
    private final MatchRejectionRepository rejectionRepository;
    private final ClaimAndPersistMatchUseCase claimAndPersistMatchUseCase;
    private final MatchingEngine matchingEngine;

    public EvaluateMatchesUseCase(
            MatchingCandidateRecordRepository candidateRecordRepository,
            MatchClaimRepository claimRepository,
            MatchingIntegrityFlagRepository integrityFlagRepository,
            MatchRejectionRepository rejectionRepository,
            ClaimAndPersistMatchUseCase claimAndPersistMatchUseCase) {
        this.candidateRecordRepository = candidateRecordRepository;
        this.claimRepository = claimRepository;
        this.integrityFlagRepository = integrityFlagRepository;
        this.rejectionRepository = rejectionRepository;
        this.claimAndPersistMatchUseCase = claimAndPersistMatchUseCase;
        this.matchingEngine = new MatchingEngine();
    }

    public EvaluateMatchesResult execute(
            RunConfigSnapshot config,
            List<UUID> sourceIds,
            LocalDate evaluationDate,
            Instant cutoff,
            String ruleSetVersion,
            Instant now) {

        List<FinancialRecord> scopeRecords = candidateRecordRepository.findEligibleForSources(sourceIds);
        if (scopeRecords.isEmpty()) {
            return new EvaluateMatchesResult(0, 0, 0, 0, 0);
        }
        List<UUID> scopeIds = scopeRecords.stream().map(FinancialRecord::id).toList();

        EvaluationContext context = buildContext(config, evaluationDate, cutoff, ruleSetVersion, scopeIds);
        CandidateHorizon horizon = new SqlCandidateHorizon(candidateRecordRepository, config);

        MatchingDecisions decisions = matchingEngine.evaluate(new RecordScope(scopeRecords), horizon, context);

        int created = 0;
        int conflicts = 0;
        ActorRef actor = ActorRef.system();
        for (MatchProposal proposal : decisions.automaticMatches()) {
            try {
                claimAndPersistMatchUseCase.execute(proposal, now, actor);
                created++;
            } catch (MatchClaimConflictException e) {
                conflicts++;
            }
        }

        return new EvaluateMatchesResult(
                created, conflicts, decisions.ambiguities().size(), decisions.suggestions().size(), decisions.orphans().size());
    }

    private EvaluationContext buildContext(
            RunConfigSnapshot config, LocalDate evaluationDate, Instant cutoff, String ruleSetVersion, List<UUID> scopeIds) {
        // Em produção este conjunto tende a ser sempre vazio: findEligibleForSources e as
        // consultas do horizonte já excluem reivindicados por SQL (NOT EXISTS ... MatchClaim)
        // — a mesma garantia é reconstruída aqui por completude do contexto, e é o que os
        // testes de domínio exercitam sinteticamente sem precisar do banco.
        Set<UUID> claimedIds = Set.copyOf(claimRepository.findClaimedIdsAmong(scopeIds));

        Map<UUID, Set<RecordIntegrityFlagType>> openFlags = integrityFlagRepository.findOpenFlagsAmong(scopeIds).stream()
                .collect(Collectors.groupingBy(
                        RecordIntegrityFlag::financialRecordId,
                        Collectors.mapping(RecordIntegrityFlag::flagType, Collectors.toSet())));

        Set<RejectedPair> rejectedPairs = rejectionRepository.findInvolvingAnyOf(scopeIds).stream()
                .map(r -> RejectedPair.of(r.recordAId(), r.recordBId()))
                .collect(Collectors.toSet());

        return new EvaluationContext(config, evaluationDate, cutoff, ruleSetVersion, claimedIds, openFlags, rejectedPairs);
    }
}
