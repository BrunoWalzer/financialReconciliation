package dev.fincore.matching.domain;

import dev.fincore.evidence.domain.RecordIntegrityFlagType;
import dev.fincore.shared.configuration.RunConfigSnapshot;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Tudo que o motor pode consultar para decidir — e nada mais (TDS 11.1, 11.7). Nenhum campo
 * aqui é lido de uma fonte mutável em tempo de avaliação: {@code config} é uma cópia
 * congelada (M3, {@code RunConfigSnapshot}), {@code evaluationDate}/{@code cutoff} são
 * parâmetros da execução, e os conjuntos de claims/flags/recusas são dados já carregados —
 * o motor nunca consulta o banco (TDS 11.1: "não persiste e não escreve").
 *
 * <p>{@code claimedRecordIds}, {@code openIntegrityFlagsByRecordId} e {@code rejectedPairs}
 * vêm de {@code match_claim}, {@code record_integrity_flag} e {@code match_rejection}
 * respectivamente — tabelas que {@code matching.domain} não pode consultar diretamente
 * (ArchUnit: sem repositório), então quem monta o contexto (M10/M11) os carrega antes.
 */
public record EvaluationContext(
        RunConfigSnapshot config,
        LocalDate evaluationDate,
        Instant cutoff,
        String ruleSetVersion,
        Set<UUID> claimedRecordIds,
        Map<UUID, Set<RecordIntegrityFlagType>> openIntegrityFlagsByRecordId,
        Set<RejectedPair> rejectedPairs) {

    public EvaluationContext {
        Objects.requireNonNull(config, "config é obrigatório");
        Objects.requireNonNull(evaluationDate, "evaluationDate é obrigatório");
        Objects.requireNonNull(cutoff, "cutoff é obrigatório");
        Objects.requireNonNull(ruleSetVersion, "ruleSetVersion é obrigatório");
        claimedRecordIds = Set.copyOf(claimedRecordIds);
        openIntegrityFlagsByRecordId = Map.copyOf(openIntegrityFlagsByRecordId);
        rejectedPairs = Set.copyOf(rejectedPairs);
    }

    public boolean isClaimed(UUID recordId) {
        return claimedRecordIds.contains(recordId);
    }

    public Set<RecordIntegrityFlagType> openFlagsFor(UUID recordId) {
        return openIntegrityFlagsByRecordId.getOrDefault(recordId, Set.of());
    }

    public boolean wasRejected(UUID recordIdA, UUID recordIdB) {
        return rejectedPairs.contains(RejectedPair.of(recordIdA, recordIdB));
    }
}
