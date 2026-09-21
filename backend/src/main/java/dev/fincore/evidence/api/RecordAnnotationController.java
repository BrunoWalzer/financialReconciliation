package dev.fincore.evidence.api;

import dev.fincore.evidence.application.CreateRecordAnnotationUseCase;
import dev.fincore.evidence.application.ListRecordAnnotationsUseCase;
import dev.fincore.evidence.domain.RecordAnnotation;
import dev.fincore.identity.application.GetCurrentUserUseCase;
import dev.fincore.identity.domain.AppUser;
import dev.fincore.identity.domain.CurrentUser;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET|POST /records/{id}/annotations} (TDS 20.2) — resposta a "o operador sabe o
 * valor correto" sem tocar na evidência. {@code POST} exige {@code RECONCILIATION_ANALYST}
 * (Implementation Plan M4).
 */
@RestController
@RequestMapping("/records/{financialRecordId}/annotations")
public class RecordAnnotationController {

    private final CreateRecordAnnotationUseCase createRecordAnnotationUseCase;
    private final ListRecordAnnotationsUseCase listRecordAnnotationsUseCase;
    private final GetCurrentUserUseCase getCurrentUserUseCase;

    public RecordAnnotationController(
            CreateRecordAnnotationUseCase createRecordAnnotationUseCase,
            ListRecordAnnotationsUseCase listRecordAnnotationsUseCase,
            GetCurrentUserUseCase getCurrentUserUseCase) {
        this.createRecordAnnotationUseCase = createRecordAnnotationUseCase;
        this.listRecordAnnotationsUseCase = listRecordAnnotationsUseCase;
        this.getCurrentUserUseCase = getCurrentUserUseCase;
    }

    @GetMapping
    public List<RecordAnnotationResponse> list(@PathVariable UUID financialRecordId) {
        return listRecordAnnotationsUseCase.execute(financialRecordId).stream()
                .map(RecordAnnotationResponse::from)
                .toList();
    }

    @PostMapping
    public ResponseEntity<RecordAnnotationResponse> create(
            @PathVariable UUID financialRecordId,
            @Valid @RequestBody CreateRecordAnnotationRequest request,
            @AuthenticationPrincipal CurrentUser currentUser) {

        AppUser actor = getCurrentUserUseCase.execute(currentUser);
        RecordAnnotation created = createRecordAnnotationUseCase.execute(
                financialRecordId, request.text(), actor.id(), actor.email());

        return ResponseEntity.created(URI.create("/records/" + financialRecordId + "/annotations/" + created.id()))
                .body(RecordAnnotationResponse.from(created));
    }
}
