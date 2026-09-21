package dev.fincore.ingestion.parser;

import dev.fincore.evidence.domain.Direction;
import dev.fincore.evidence.domain.RecordType;
import java.util.Map;
import java.util.Optional;

/**
 * {@code tipo_operacao} → {@code payment_method} + {@code record_type} + {@code direction}
 * (Implementation Plan DR-1). Só o único valor documentado no exemplo aprovado é
 * reconhecido; qualquer outro é rejeitado como valor de conjunto fechado desconhecido
 * (Domain §10.4) — o vocabulário completo não está especificado em nenhum documento, e
 * inventá-lo seria adivinhar informação financeira.
 */
final class AcquirerOperationType {

    private static final Map<String, AcquirerOperationType> KNOWN = Map.of(
            "CREDITO_A_VISTA", new AcquirerOperationType("CREDIT_CARD", RecordType.SETTLEMENT, Direction.CREDIT));

    private final String paymentMethod;
    private final RecordType recordType;
    private final Direction direction;

    private AcquirerOperationType(String paymentMethod, RecordType recordType, Direction direction) {
        this.paymentMethod = paymentMethod;
        this.recordType = recordType;
        this.direction = direction;
    }

    static Optional<AcquirerOperationType> fromTipoOperacao(String tipoOperacao) {
        if (tipoOperacao == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(KNOWN.get(tipoOperacao.trim().toUpperCase(java.util.Locale.ROOT)));
    }

    String paymentMethod() {
        return paymentMethod;
    }

    RecordType recordType() {
        return recordType;
    }

    Direction direction() {
        return direction;
    }
}
