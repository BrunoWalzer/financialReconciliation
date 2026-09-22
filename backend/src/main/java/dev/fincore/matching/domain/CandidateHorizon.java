package dev.fincore.matching.domain;

import dev.fincore.evidence.domain.FinancialRecord;
import java.util.List;

/**
 * Contrapartes já carregadas para um registro (TDS 11.1: "interface de dados já carregados,
 * não repositório"). Em teste, alimentada por listas em memória; em produção, por consultas
 * de <i>blocking</i> — mas nunca uma segunda ida ao banco escondida atrás desta interface no
 * meio da avaliação.
 *
 * <p><b>O horizonte vem da janela de liquidação de cada registro, nunca do escopo da
 * execução</b> (Domain §12.4, ADR-007). Um escopo estreito não pode ver menos candidatos do
 * que um escopo amplo veria para o mesmo registro — isso inverteria a segurança do
 * matching, conciliando com mais facilidade exatamente onde há menos informação.
 */
public interface CandidateHorizon {

    /** Contrapartes com a mesma chave de correlação (Nível A, TDS 11.4). */
    List<FinancialRecord> byCorrelationKey(String correlationKey);

    /**
     * Contrapartes com documento e meio de pagamento compatíveis, dentro da janela de
     * liquidação e numa faixa larga de valor (Nível B, TDS 11.5 "Passo 1") — bloqueio
     * grosseiro; a decisão exata é sempre feita depois, em Java, sobre o que este método
     * devolve.
     */
    List<FinancialRecord> byDocumentAndPaymentMethodWithinWindow(FinancialRecord scopeRecord);

    /**
     * Contrapartes com valor e data plausíveis, sem documento nem meio de pagamento (Nível
     * C, TDS 11.6) — mesma consulta do Nível B, mais larga. O motor aplica o teto de 20 por
     * registro sobre o resultado.
     */
    List<FinancialRecord> byAmountAndDateWithinWindow(FinancialRecord scopeRecord);
}
