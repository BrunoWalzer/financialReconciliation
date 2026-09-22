package dev.fincore.matching.domain;

import dev.fincore.evidence.domain.FinancialRecord;
import java.util.List;

/**
 * Os registros que esta avaliação examina — o lado esquerdo do par, tipicamente (TDS 11.1).
 * Só decide <b>quais registros são avaliados</b>; nunca decide contra o que eles são
 * comparados (isso é {@link CandidateHorizon}, Domain §12.4).
 */
public record RecordScope(List<FinancialRecord> records) {

    public RecordScope {
        records = List.copyOf(records);
    }
}
