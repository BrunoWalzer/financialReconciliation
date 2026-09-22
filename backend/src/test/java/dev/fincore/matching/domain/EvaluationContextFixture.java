package dev.fincore.matching.domain;

import dev.fincore.evidence.domain.RecordIntegrityFlagType;
import dev.fincore.shared.configuration.RunConfigSnapshot;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class EvaluationContextFixture {

    private RunConfigSnapshot config = RunConfigSnapshotFixture.config().build();
    private LocalDate evaluationDate = LocalDate.of(2026, 9, 20);
    private Instant cutoff = Instant.parse("2026-09-20T06:00:00Z");
    private String ruleSetVersion = "2026.09.1";
    private final Set<UUID> claimedRecordIds = new HashSet<>();
    private final Map<UUID, Set<RecordIntegrityFlagType>> openFlags = new HashMap<>();
    private final Set<RejectedPair> rejectedPairs = new HashSet<>();

    public static EvaluationContextFixture context() {
        return new EvaluationContextFixture();
    }

    public EvaluationContextFixture config(RunConfigSnapshot value) {
        this.config = value;
        return this;
    }

    public EvaluationContextFixture evaluationDate(LocalDate value) {
        this.evaluationDate = value;
        return this;
    }

    public EvaluationContextFixture claimed(UUID recordId) {
        this.claimedRecordIds.add(recordId);
        return this;
    }

    public EvaluationContextFixture flagged(UUID recordId, RecordIntegrityFlagType type) {
        this.openFlags.computeIfAbsent(recordId, k -> new HashSet<>()).add(type);
        return this;
    }

    public EvaluationContextFixture rejected(UUID a, UUID b) {
        this.rejectedPairs.add(RejectedPair.of(a, b));
        return this;
    }

    public EvaluationContext build() {
        return new EvaluationContext(config, evaluationDate, cutoff, ruleSetVersion, claimedRecordIds, openFlags, rejectedPairs);
    }
}
