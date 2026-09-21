package dev.fincore.shared.messaging;

/** As duas chaves de roteamento do exchange {@code fincore.commands} (TDS 17.1, 17.2). */
public final class RoutingKeys {

    public static final String IMPORT_PROCESS = "import.process";
    public static final String RECONCILIATION_RUN = "reconciliation.run";

    private RoutingKeys() {
    }
}
