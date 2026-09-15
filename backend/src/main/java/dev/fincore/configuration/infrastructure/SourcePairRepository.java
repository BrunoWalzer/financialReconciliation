package dev.fincore.configuration.infrastructure;

import dev.fincore.configuration.domain.SourcePair;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Persistência de {@link SourcePair}. Só nasce por seed — sem {@code save} exposto. */
public interface SourcePairRepository extends Repository<SourcePair, UUID> {

    Optional<SourcePair> findById(UUID id);
}
