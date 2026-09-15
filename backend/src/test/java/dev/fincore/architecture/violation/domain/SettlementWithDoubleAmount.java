package dev.fincore.architecture.violation.domain;

/** Viola DOMAIN_HAS_NO_FLOATING_POINT_FIELDS: campo double dentro de um pacote domain. */
public class SettlementWithDoubleAmount {

    private double amount;

    public double amount() {
        return amount;
    }
}
