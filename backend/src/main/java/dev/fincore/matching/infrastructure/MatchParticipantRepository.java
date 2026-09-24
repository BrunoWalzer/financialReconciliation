package dev.fincore.matching.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Persistência de {@link MatchParticipant}. */
public interface MatchParticipantRepository extends Repository<MatchParticipant, MatchParticipant.ParticipantId> {

    MatchParticipant save(MatchParticipant participant);

    List<MatchParticipant> findByMatchId(UUID matchId);
}
