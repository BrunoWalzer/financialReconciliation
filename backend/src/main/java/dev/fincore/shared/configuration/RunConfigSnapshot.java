package dev.fincore.shared.configuration;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * O que uma execução de conciliação congela ao entrar em {@code RUNNING} (TDS 10.2) — o
 * mecanismo central deste milestone (Implementation Plan M3, "Reprodutibilidade
 * histórica"). O motor de matching (M9) lê <b>exclusivamente</b> deste snapshot, nunca da
 * configuração corrente.
 *
 * <p>Formato espelha exatamente o exemplo do TDS §10.2: nomes de campo em
 * {@code camelCase} para (de)serialização Jackson direta, sem anotação — records já
 * expõem acessores com esses nomes. {@code roundingMode} é {@code String}, não o enum de
 * {@code configuration.domain}, de propósito — ver o Javadoc do pacote sobre por que este
 * tipo não pode depender de {@code configuration}.
 *
 * <p>Puro VO, sem persistência própria neste milestone: não existe ainda
 * {@code reconciliation_run.config_snapshot} (nasce no M11) para gravá-lo. M3 entrega o
 * tipo e {@code ConfigSnapshotFactory}, que o M11 vai chamar.
 */
public record RunConfigSnapshot(
        Instant capturedAt,
        ToleranceSnapshot tolerance,
        List<SettlementWindowSnapshot> settlementWindows,
        List<FeeRuleSnapshot> feeRules,
        List<SourceSnapshot> sources) {

    public RunConfigSnapshot {
        settlementWindows = List.copyOf(settlementWindows);
        feeRules = List.copyOf(feeRules);
        sources = List.copyOf(sources);
    }

    public record ToleranceSnapshot(long absoluteMinor, String currency, Long aggregateAlertThresholdMinor) {
    }

    public record SettlementWindowSnapshot(String paymentMethod, int minDays, int maxDays) {
    }

    public record FeeRuleSnapshot(
            String sourceCode, String paymentMethod, int percentageBp, long fixedAmountMinor, String roundingMode) {
    }

    /**
     * {@code id} entra no M9: {@code matching.domain} só recebe o {@code sourceId} (UUID) de
     * cada {@code FinancialRecord} — sem ele, não haveria como saber a qual fonte um
     * registro pertence para resolver {@code FeeRuleSnapshot.sourceCode()} sem que
     * {@code matching} dependesse de {@code configuration} (proibido, TDS 4.3).
     */
    public record SourceSnapshot(UUID id, String code, String timezone) {
    }
}
