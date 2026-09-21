package dev.fincore.evidence.application;

import dev.fincore.audit.application.AuditEventRequest;
import dev.fincore.audit.application.AuditService;
import dev.fincore.audit.domain.ActorRef;
import dev.fincore.evidence.domain.RecordAnnotation;
import dev.fincore.evidence.infrastructure.FinancialRecordRepository;
import dev.fincore.evidence.infrastructure.RecordAnnotationRepository;
import java.time.Clock;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code POST /records/{id}/annotations} — "o operador sabe o valor correto", sem tocar na
 * evidência (TDS 7.5). Exige {@code RECONCILIATION_ANALYST} (Implementation Plan M4: "anotação
 * exige ANALYST"). Evento auditado na mesma transação (TDS 22.2: "anotação criada").
 */
@Service
public class CreateRecordAnnotationUseCase {

    private final RecordAnnotationRepository repository;
    private final FinancialRecordRepository financialRecordRepository;
    private final AuditService auditService;
    private final Clock clock;

    public CreateRecordAnnotationUseCase(
            RecordAnnotationRepository repository,
            FinancialRecordRepository financialRecordRepository,
            AuditService auditService,
            Clock clock) {
        this.repository = repository;
        this.financialRecordRepository = financialRecordRepository;
        this.auditService = auditService;
        this.clock = clock;
    }

    @PreAuthorize("hasAuthority('RECONCILIATION_ANALYST')")
    @Transactional
    public RecordAnnotation execute(UUID financialRecordId, String text, UUID actorUserId, String actorLabel) {
        financialRecordRepository.findById(financialRecordId)
                .orElseThrow(() -> new NoSuchElementException("financial_record não encontrado: " + financialRecordId));

        RecordAnnotation annotation = new RecordAnnotation(financialRecordId, actorUserId, text, clock.instant());
        RecordAnnotation saved = repository.save(annotation);

        auditService.record(AuditEventRequest.of(
                ActorRef.user(actorUserId, actorLabel), "RECORD_ANNOTATION_CREATED", "RecordAnnotation", saved.id()));

        return saved;
    }
}
