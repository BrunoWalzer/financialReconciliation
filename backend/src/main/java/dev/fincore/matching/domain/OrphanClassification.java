package dev.fincore.matching.domain;

import dev.fincore.evidence.domain.FinancialRecord;

/**
 * Um registro que sobrou sem correspondência, classificado contra {@code evaluationDate}
 * (Implementation Plan M9, Domain §6.3) — nunca contra o relógio do sistema.
 */
public record OrphanClassification(FinancialRecord record, Status status) {

    public enum Status {
        /** Dentro da janela esperada de liquidação — ainda não é problema. */
        PENDING_SETTLEMENT,
        /** Janela vencida — candidato a divergência de ausência (aberta só a partir do M12). */
        ABSENCE_CANDIDATE
    }
}
