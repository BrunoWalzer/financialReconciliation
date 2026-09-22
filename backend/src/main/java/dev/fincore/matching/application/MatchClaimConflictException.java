package dev.fincore.matching.application;

import java.util.UUID;

/**
 * Duas avaliações concorrentes propuseram um match que reivindica um {@code FinancialRecord}
 * já reivindicado (I-5) — uma corrida legítima e esperada entre execuções concorrentes
 * (Implementation Plan M9/M10, "não mockar a constraint"), nunca um erro de sistema. Quem
 * perde a corrida não corrompeu nada: o vencedor já reivindicou o registro; esta exceção é a
 * tradução limpa da violação de chave primária de {@code match_claim} em algo que a camada
 * de aplicação trata sem expor um stack trace de constraint ao chamador.
 */
public class MatchClaimConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final UUID financialRecordId;

    public MatchClaimConflictException(UUID financialRecordId, Throwable cause) {
        super("financial_record " + financialRecordId + " já foi reivindicado por outro match concorrente", cause);
        this.financialRecordId = financialRecordId;
    }

    public UUID financialRecordId() {
        return financialRecordId;
    }
}
