package dev.fincore.matching.domain;

import dev.fincore.shared.configuration.RunConfigSnapshot;
import dev.fincore.shared.configuration.RunConfigSnapshot.FeeRuleSnapshot;
import dev.fincore.shared.configuration.RunConfigSnapshot.SettlementWindowSnapshot;
import dev.fincore.shared.configuration.RunConfigSnapshot.SourceSnapshot;
import dev.fincore.shared.configuration.RunConfigSnapshot.ToleranceSnapshot;
import dev.fincore.shared.identifier.Uuid7;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Um {@link RunConfigSnapshot} mínimo e plausível, mesmo padrão de seed do M3 (V3, TDS 7.3). */
public final class RunConfigSnapshotFixture {

    public static final UUID INTERNAL_SALES_ID = Uuid7.generate();
    public static final UUID ACQUIRER_SETTLEMENT_ID = Uuid7.generate();

    private long toleranceAbsoluteMinor = 2;
    private final List<SettlementWindowSnapshot> settlementWindows = new ArrayList<>(
            List.of(new SettlementWindowSnapshot(null, 1, 3), new SettlementWindowSnapshot("CREDIT_CARD", 1, 31)));
    private final List<FeeRuleSnapshot> feeRules = new ArrayList<>();

    public static RunConfigSnapshotFixture config() {
        return new RunConfigSnapshotFixture();
    }

    public RunConfigSnapshotFixture toleranceAbsoluteMinor(long value) {
        this.toleranceAbsoluteMinor = value;
        return this;
    }

    public RunConfigSnapshotFixture settlementWindows(List<SettlementWindowSnapshot> windows) {
        this.settlementWindows.clear();
        this.settlementWindows.addAll(windows);
        return this;
    }

    public RunConfigSnapshotFixture feeRule(String sourceCode, String paymentMethod, int percentageBp, long fixedAmountMinor, String roundingMode) {
        this.feeRules.add(new FeeRuleSnapshot(sourceCode, paymentMethod, percentageBp, fixedAmountMinor, roundingMode));
        return this;
    }

    public RunConfigSnapshot build() {
        return new RunConfigSnapshot(
                Instant.parse("2026-09-14T06:00:00Z"),
                new ToleranceSnapshot(toleranceAbsoluteMinor, "BRL", null),
                settlementWindows,
                feeRules,
                List.of(
                        new SourceSnapshot(INTERNAL_SALES_ID, "INTERNAL_SALES", "America/Sao_Paulo"),
                        new SourceSnapshot(ACQUIRER_SETTLEMENT_ID, "ACQUIRER_SETTLEMENT", "America/Sao_Paulo")));
    }
}
