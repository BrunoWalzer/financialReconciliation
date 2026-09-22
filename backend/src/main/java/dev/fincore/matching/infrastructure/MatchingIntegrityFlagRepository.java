package dev.fincore.matching.infrastructure;

import dev.fincore.evidence.domain.RecordIntegrityFlag;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Leitura de flags de integridade abertas para montar {@code EvaluationContext} (predicado
 * {@code NO_UNRESOLVED_INTEGRITY_FLAG} e {@code KEY_UNIQUE_BOTH_SIDES}, M9). Repositório
 * próprio de {@code matching} sobre a entidade {@code RecordIntegrityFlag} (dona:
 * {@code evidence}) — mesmo raciocínio de {@link MatchingCandidateRecordRepository}.
 */
public interface MatchingIntegrityFlagRepository extends Repository<RecordIntegrityFlag, UUID> {

    @Query("SELECT f FROM RecordIntegrityFlag f WHERE f.financialRecordId IN :ids AND f.resolvedAt IS NULL")
    List<RecordIntegrityFlag> findOpenFlagsAmong(@Param("ids") Collection<UUID> ids);
}
