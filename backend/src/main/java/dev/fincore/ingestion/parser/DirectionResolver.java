package dev.fincore.ingestion.parser;

import dev.fincore.evidence.domain.Direction;
import dev.fincore.evidence.domain.RecordType;

/**
 * Direção a partir do tipo de registro (Direction: {@code CREDIT} entrada, {@code DEBIT}
 * saída). Venda e liquidação trazem dinheiro para dentro; estorno devolve — decisão de
 * leitura direta do enunciado de {@link Direction}, já que nenhum documento lista a
 * combinação explicitamente.
 */
public final class DirectionResolver {

    private DirectionResolver() {
    }

    public static Direction fromRecordType(RecordType recordType) {
        return switch (recordType) {
            case SALE, SETTLEMENT -> Direction.CREDIT;
            case REFUND -> Direction.DEBIT;
            default -> throw new IllegalArgumentException("direção indefinida para " + recordType + " no MVP");
        };
    }
}
