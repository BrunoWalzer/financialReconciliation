package dev.fincore.configuration.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.fincore.audit.application.AuditEventRequest;
import dev.fincore.audit.application.AuditService;
import dev.fincore.audit.domain.ActorRef;
import dev.fincore.configuration.domain.CoverageExpectation;
import dev.fincore.configuration.infrastructure.CoverageExpectationRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateCoverageExpectationUseCase {

    private final CoverageExpectationRepository repository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public UpdateCoverageExpectationUseCase(
            CoverageExpectationRepository repository, AuditService auditService, ObjectMapper objectMapper) {
        this.repository = repository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    @Transactional
    public CoverageExpectation execute(
            UUID expectationId, long ifMatchVersion, UpdateCoverageExpectationCommand command, UUID actorUserId, String actorLabel) {
        CoverageExpectation expectation = repository.findById(expectationId)
                .orElseThrow(() -> new NoSuchElementException("coverage_expectation não encontrada: " + expectationId));

        if (expectation.version() != ifMatchVersion) {
            throw new StaleConfigurationVersionException();
        }

        Map<String, Object> before = toMap(expectation);
        expectation.update(command.schedule(), command.graceDays());

        CoverageExpectation saved;
        try {
            saved = repository.save(expectation);
        } catch (OptimisticLockingFailureException e) {
            throw new StaleConfigurationVersionException();
        }

        auditService.record(new AuditEventRequest(
                ActorRef.user(actorUserId, actorLabel),
                "COVERAGE_EXPECTATION_UPDATED",
                "CoverageExpectation",
                saved.id(),
                writeJson(before),
                writeJson(toMap(saved)),
                null,
                null,
                null));

        return saved;
    }

    private static Map<String, Object> toMap(CoverageExpectation expectation) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("schedule", expectation.schedule().name());
        map.put("graceDays", expectation.graceDays());
        return map;
    }

    private String writeJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("falha ao serializar estado de auditoria", e);
        }
    }
}
