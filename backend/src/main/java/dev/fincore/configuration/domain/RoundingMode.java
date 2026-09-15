package dev.fincore.configuration.domain;

/**
 * Critério de arredondamento declarado, usado tanto por {@link Source} (como o valor bruto
 * é interpretado na normalização) quanto por {@link FeeRule} (como a taxa esperada é
 * calculada) — TDS 7.3.
 *
 * <p>Só o nome do modo mora aqui; a aritmética em si é responsabilidade exclusiva de
 * {@code Money} (M4) e do avaliador de taxa (M9) — este módulo não faz conta com dinheiro.
 */
public enum RoundingMode {
    HALF_UP,
    HALF_EVEN,
    DOWN,
    UP
}
