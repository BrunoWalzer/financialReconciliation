package dev.fincore.configuration.infrastructure;

import dev.fincore.configuration.domain.FeeRule;
import dev.fincore.configuration.domain.SettlementWindow;
import dev.fincore.configuration.domain.Source;
import dev.fincore.configuration.domain.SourcePair;
import dev.fincore.configuration.domain.ToleranceConfig;
import dev.fincore.shared.configuration.RunConfigSnapshot;
import dev.fincore.shared.configuration.RunConfigSnapshot.FeeRuleSnapshot;
import dev.fincore.shared.configuration.RunConfigSnapshot.SettlementWindowSnapshot;
import dev.fincore.shared.configuration.RunConfigSnapshot.SourceSnapshot;
import dev.fincore.shared.configuration.RunConfigSnapshot.ToleranceSnapshot;
import java.time.Clock;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * Constrói o {@link RunConfigSnapshot} de um par de fontes (Implementation Plan M3, "o
 * ponto central deste milestone"). O M11 vai chamar {@link #capture} na transição
 * {@code QUEUED → RUNNING} de uma execução; aqui ela só existe pronta para ser chamada —
 * não há execução ainda.
 *
 * <p><b>Estabilidade comprovada por teste:</b> duas chamadas sem alteração intermediária
 * produzem snapshots iguais (records comparam por valor), e o snapshot capturado não
 * muda quando a configuração muda depois — ele é uma cópia, não uma referência viva.
 */
@Component
public class ConfigSnapshotFactory {

    private final SourcePairRepository sourcePairRepository;
    private final SourceRepository sourceRepository;
    private final ToleranceConfigRepository toleranceConfigRepository;
    private final SettlementWindowRepository settlementWindowRepository;
    private final FeeRuleRepository feeRuleRepository;
    private final Clock clock;

    public ConfigSnapshotFactory(
            SourcePairRepository sourcePairRepository,
            SourceRepository sourceRepository,
            ToleranceConfigRepository toleranceConfigRepository,
            SettlementWindowRepository settlementWindowRepository,
            FeeRuleRepository feeRuleRepository,
            Clock clock) {
        this.sourcePairRepository = sourcePairRepository;
        this.sourceRepository = sourceRepository;
        this.toleranceConfigRepository = toleranceConfigRepository;
        this.settlementWindowRepository = settlementWindowRepository;
        this.feeRuleRepository = feeRuleRepository;
        this.clock = clock;
    }

    public RunConfigSnapshot capture(UUID sourcePairId) {
        SourcePair pair = sourcePairRepository.findById(sourcePairId)
                .orElseThrow(() -> new NoSuchElementException("source_pair não encontrado: " + sourcePairId));

        Source left = requireSource(pair.leftSourceId());
        Source right = requireSource(pair.rightSourceId());
        Map<UUID, String> codeById = Map.of(left.id(), left.code(), right.id(), right.code());

        ToleranceConfig tolerance = toleranceConfigRepository.findBySourcePairId(sourcePairId)
                .orElseThrow(() -> new NoSuchElementException("tolerance_config não encontrado para " + sourcePairId));

        List<SettlementWindowSnapshot> settlementWindows = settlementWindowRepository.findBySourcePairId(sourcePairId)
                .stream()
                .map(ConfigSnapshotFactory::toSnapshot)
                .toList();

        List<FeeRuleSnapshot> feeRules = Stream.of(left.id(), right.id())
                .flatMap(sourceId -> feeRuleRepository.findBySourceIdAndActiveTrue(sourceId).stream())
                .map(rule -> toSnapshot(rule, codeById.get(rule.sourceId())))
                .toList();

        List<SourceSnapshot> sources = List.of(toSnapshot(left), toSnapshot(right));

        return new RunConfigSnapshot(
                clock.instant(), toSnapshot(tolerance), settlementWindows, feeRules, sources);
    }

    private Source requireSource(UUID sourceId) {
        return sourceRepository.findById(sourceId)
                .orElseThrow(() -> new NoSuchElementException("source não encontrada: " + sourceId));
    }

    private static ToleranceSnapshot toSnapshot(ToleranceConfig tolerance) {
        return new ToleranceSnapshot(
                tolerance.absoluteAmountMinor(), tolerance.currency(), tolerance.aggregateAlertThresholdMinor());
    }

    private static SettlementWindowSnapshot toSnapshot(SettlementWindow window) {
        return new SettlementWindowSnapshot(window.paymentMethod(), window.minDays(), window.maxDays());
    }

    private static FeeRuleSnapshot toSnapshot(FeeRule rule, String sourceCode) {
        return new FeeRuleSnapshot(
                sourceCode, rule.paymentMethod(), rule.percentageBp(), rule.fixedAmountMinor(), rule.roundingMode().name());
    }

    private static SourceSnapshot toSnapshot(Source source) {
        return new SourceSnapshot(source.id(), source.code(), source.timezone());
    }
}
