package dev.fincore.matching.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Leitura de {@link MatchRejection} para montar {@code EvaluationContext.rejectedPairs()}.
 * Sem escrita até a resolução manual (M14) — ver o Javadoc da entidade.
 */
public interface MatchRejectionRepository extends Repository<MatchRejection, UUID> {

    @Query("SELECT r FROM MatchRejection r WHERE r.recordAId IN :ids OR r.recordBId IN :ids")
    List<MatchRejection> findInvolvingAnyOf(@Param("ids") Collection<UUID> ids);
}
