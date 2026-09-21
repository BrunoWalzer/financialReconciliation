package dev.fincore.ingestion.parser;

import dev.fincore.ingestion.domain.RejectionReasonCode;

/** O que {@link RecordParser#parseLine} devolve para uma linha (TDS 9.3): aceita, ou rejeitada com motivo. */
public sealed interface ParseResult {

    record Accepted(NormalizedRecordDraft draft) implements ParseResult {
    }

    /** {@code extractedAmountMinor} preserva o valor que foi possível extrair, mesmo numa linha rejeitada (TDS 7.4). */
    record Rejected(RejectionReasonCode reasonCode, String detail, Long extractedAmountMinor) implements ParseResult {
    }
}
