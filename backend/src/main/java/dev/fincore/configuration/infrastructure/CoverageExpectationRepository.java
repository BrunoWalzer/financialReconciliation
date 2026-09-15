package dev.fincore.configuration.infrastructure;

import dev.fincore.configuration.domain.CoverageExpectation;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface CoverageExpectationRepository extends Repository<CoverageExpectation, UUID> {

    CoverageExpectation save(CoverageExpectation expectation);

    Optional<CoverageExpectation> findById(UUID id);

    Optional<CoverageExpectation> findBySourceId(UUID sourceId);
}
