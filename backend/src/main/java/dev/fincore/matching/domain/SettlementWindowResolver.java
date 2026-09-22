package dev.fincore.matching.domain;

import dev.fincore.shared.configuration.RunConfigSnapshot;
import dev.fincore.shared.configuration.RunConfigSnapshot.SettlementWindowSnapshot;
import java.util.Optional;

/**
 * Resolve a janela de liquidação por meio de pagamento, com o padrão
 * ({@code paymentMethod IS NULL}) como fallback — mesma regra em todo lugar que precisa dela
 * ({@link dev.fincore.matching.domain.predicate.WithinSettlementWindowPredicate} e a
 * classificação de órfãos), para nunca haver duas formas de resolver a mesma janela.
 */
public final class SettlementWindowResolver {

    public static Optional<SettlementWindowSnapshot> resolve(RunConfigSnapshot config, String paymentMethod) {
        Optional<SettlementWindowSnapshot> specific = config.settlementWindows().stream()
                .filter(w -> paymentMethod != null && paymentMethod.equals(w.paymentMethod()))
                .findFirst();
        if (specific.isPresent()) {
            return specific;
        }
        return config.settlementWindows().stream()
                .filter(w -> w.paymentMethod() == null)
                .findFirst();
    }

    private SettlementWindowResolver() {
    }
}
