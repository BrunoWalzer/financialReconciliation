package dev.fincore.matching.domain.predicate;

import dev.fincore.matching.domain.EvaluationContext;
import dev.fincore.matching.domain.Predicate;
import dev.fincore.matching.domain.PredicateResult;
import dev.fincore.matching.domain.RecordPair;
import dev.fincore.matching.domain.SettlementWindowResolver;
import dev.fincore.shared.configuration.RunConfigSnapshot.SettlementWindowSnapshot;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A data de negócio do lado direito cai dentro de {@code [left + minDays, left + maxDays]}
 * (TDS 8.4, 11.3) — a janela é sempre relativa ao lado esquerdo (a venda), nunca uma
 * distância simétrica: liquidação vem depois da venda, nunca antes.
 *
 * <p>A janela é resolvida por meio de pagamento — o meio da transação, lido do lado que o
 * tiver (normalmente o esquerdo). Sem janela configurada para o meio nem janela padrão
 * ({@code paymentMethod IS NULL}), o predicado falha: sem configuração, não há como provar
 * que a data está dentro do esperado.
 */
public final class WithinSettlementWindowPredicate implements Predicate {

    public static final String NAME = "WITHIN_SETTLEMENT_WINDOW";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public PredicateResult test(RecordPair pair, EvaluationContext context) {
        LocalDate leftDate = pair.left().businessDate();
        LocalDate rightDate = pair.right().businessDate();
        String paymentMethod = pair.left().paymentMethod() != null
                ? pair.left().paymentMethod()
                : pair.right().paymentMethod();

        Optional<SettlementWindowSnapshot> window = SettlementWindowResolver.resolve(context.config(), paymentMethod);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("leftDate", leftDate.toString());
        detail.put("rightDate", rightDate.toString());
        detail.put("paymentMethod", paymentMethod);

        if (window.isEmpty()) {
            detail.put("reason", "no_settlement_window_configured");
            return PredicateResult.fail(NAME, detail);
        }

        LocalDate earliest = leftDate.plusDays(window.get().minDays());
        LocalDate latest = leftDate.plusDays(window.get().maxDays());
        detail.put("minDays", window.get().minDays());
        detail.put("maxDays", window.get().maxDays());

        boolean within = !rightDate.isBefore(earliest) && !rightDate.isAfter(latest);
        return within ? PredicateResult.pass(NAME, detail) : PredicateResult.fail(NAME, detail);
    }
}
