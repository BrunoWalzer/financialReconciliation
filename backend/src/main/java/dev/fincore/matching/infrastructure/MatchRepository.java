package dev.fincore.matching.infrastructure;

import dev.fincore.matching.domain.Match;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Persistência de {@link Match}. Só {@code save} e leitura: recomposição é M13. */
public interface MatchRepository extends Repository<Match, UUID> {

    Match save(Match match);

    Optional<Match> findById(UUID id);
}
