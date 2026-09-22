package dev.fincore.matching.infrastructure;

import dev.fincore.matching.domain.MatchClaim;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Persistência de {@link MatchClaim}. {@code save} é onde a exclusividade real acontece
 * (I-5): duas transações reivindicando o mesmo {@code financial_record_id} colidem na PK,
 * nunca antes disso — ver {@code ClaimFinancialRecordsUseCase}.
 */
public interface MatchClaimRepository extends Repository<MatchClaim, UUID> {

    MatchClaim save(MatchClaim claim);

    /** Quais dos ids informados já estão reivindicados — usado para montar {@code EvaluationContext}. */
    @Query("SELECT c.financialRecordId FROM MatchClaim c WHERE c.financialRecordId IN :ids")
    List<UUID> findClaimedIdsAmong(@Param("ids") Collection<UUID> ids);
}
