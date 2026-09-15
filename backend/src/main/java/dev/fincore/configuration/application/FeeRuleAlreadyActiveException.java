package dev.fincore.configuration.application;

/**
 * Já existe uma regra de taxa ativa para esta fonte e meio de pagamento
 * ({@code uq_fee_rule_active_source_payment_method}, TDS 7.3).
 */
public class FeeRuleAlreadyActiveException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public FeeRuleAlreadyActiveException() {
        super("já existe uma regra de taxa ativa para esta fonte e meio de pagamento");
    }
}
