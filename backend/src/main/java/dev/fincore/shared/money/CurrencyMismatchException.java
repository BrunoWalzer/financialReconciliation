package dev.fincore.shared.money;

/**
 * Uma operação de {@link Money} foi tentada entre moedas distintas (Domain §10.3: "matching
 * entre moedas diferentes é proibido" — a proibição vale mesmo com uma moeda só no MVP).
 */
public class CurrencyMismatchException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CurrencyMismatchException(Currency left, Currency right) {
        super("moedas incompatíveis: " + left + " e " + right);
    }
}
