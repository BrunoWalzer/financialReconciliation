package dev.fincore.evidence.application;

import dev.fincore.evidence.domain.RecordIntegrityFlag;
import dev.fincore.evidence.domain.RecordIntegrityFlagType;
import dev.fincore.evidence.infrastructure.IntegrityScanQueries;
import dev.fincore.evidence.infrastructure.RecordIntegrityFlagBulkInsert;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * A varredura de integridade intra-fonte (Implementation Plan M7, TDS 9.6): roda ao fim de
 * uma importação bem-sucedida, restrita aos registros novos do {@code import_batch} e aos
 * grupos de chave que eles tocam — nunca à fonte inteira. Só marca; nunca deduplica, nunca
 * altera {@code financial_record} (Domain §9.3: impressão digital repetida nunca autoriza
 * descarte de evidência).
 *
 * <p>Só {@code ingestion} chama isto, ao final do pipeline síncrono — sem endpoint HTTP
 * próprio, daí {@code isAuthenticated()} genérico em vez de um papel específico, mesmo padrão
 * de {@link BulkInsertFinancialRecordsUseCase}.
 *
 * <p><b>Tolerância de {@code SOURCE_INTERNAL_INCONSISTENCY}.</b> AC-FEE-03 (Domain v1.1)
 * exige rejeitar "bruto menos taxa declarada diferente do líquido além da tolerância", sem
 * quantificar o valor. {@code tolerance_config} não serve aqui: é indexada por
 * {@code source_pair_id} (Técnico 7.3) e esta verificação roda sobre um único registro de uma
 * única fonte, antes de qualquer pareamento — não há par, logo não há
 * {@code source_pair_id} para consultar. Adotado {@link #SOURCE_INTERNAL_INCONSISTENCY_TOLERANCE_MINOR}
 * fixo (1 centavo), a menor margem não nula defensável para arredondamento da própria fonte,
 * documentado aqui como decisão de implementação — não uma configuração de negócio, que
 * excederia o escopo deste milestone (nenhuma tabela nova). Ver relatório do M7, Decisões.
 */
@Service
public class RunIntegrityScanUseCase {

    private static final long SOURCE_INTERNAL_INCONSISTENCY_TOLERANCE_MINOR = 1;

    private final IntegrityScanQueries queries;
    private final RecordIntegrityFlagBulkInsert bulkInsert;

    public RunIntegrityScanUseCase(IntegrityScanQueries queries, RecordIntegrityFlagBulkInsert bulkInsert) {
        this.queries = queries;
        this.bulkInsert = bulkInsert;
    }

    @PreAuthorize("isAuthenticated()")
    public IntegrityScanResult execute(UUID sourceId, UUID importBatchId, Instant detectedAt) {
        int duplicateExternalId = flagAll(
                queries.findExternalIdDuplicates(sourceId, importBatchId),
                RecordIntegrityFlagType.DUPLICATE_EXTERNAL_ID, importBatchId, detectedAt);

        int duplicateCorrelationKey = flagAll(
                queries.findCorrelationKeyDuplicates(sourceId, importBatchId),
                RecordIntegrityFlagType.DUPLICATE_CORRELATION_KEY, importBatchId, detectedAt);

        int possibleDuplicate = flagAll(
                queries.findFingerprintDuplicatesWithoutExternalId(sourceId, importBatchId),
                RecordIntegrityFlagType.POSSIBLE_DUPLICATE, importBatchId, detectedAt);

        int sourceInternalInconsistency = flagAll(
                queries.findSourceInternalInconsistencies(importBatchId, SOURCE_INTERNAL_INCONSISTENCY_TOLERANCE_MINOR),
                RecordIntegrityFlagType.SOURCE_INTERNAL_INCONSISTENCY, importBatchId, detectedAt);

        return new IntegrityScanResult(duplicateExternalId, duplicateCorrelationKey, possibleDuplicate, sourceInternalInconsistency);
    }

    private int flagAll(List<UUID> financialRecordIds, RecordIntegrityFlagType type, UUID importBatchId, Instant detectedAt) {
        List<RecordIntegrityFlag> flags = financialRecordIds.stream()
                .map(id -> new RecordIntegrityFlag(id, type, importBatchId, detectedAt))
                .toList();
        return bulkInsert.insertIgnoringConflicts(flags);
    }
}
