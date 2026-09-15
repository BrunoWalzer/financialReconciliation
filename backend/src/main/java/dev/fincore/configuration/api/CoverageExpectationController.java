package dev.fincore.configuration.api;

import dev.fincore.configuration.application.ListCoverageExpectationsUseCase;
import dev.fincore.configuration.application.UpdateCoverageExpectationCommand;
import dev.fincore.configuration.application.UpdateCoverageExpectationUseCase;
import dev.fincore.configuration.domain.CoverageExpectation;
import dev.fincore.identity.application.GetCurrentUserUseCase;
import dev.fincore.identity.domain.AppUser;
import dev.fincore.identity.domain.CurrentUser;
import dev.fincore.shared.web.ETag;
import jakarta.validation.Valid;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET|PUT /config/coverage-expectations} (TDS 19.2). Sem POST: só nasce por seed no M3. */
@RestController
@RequestMapping("/config/coverage-expectations")
public class CoverageExpectationController {

    private final ListCoverageExpectationsUseCase listCoverageExpectationsUseCase;
    private final UpdateCoverageExpectationUseCase updateCoverageExpectationUseCase;
    private final GetCurrentUserUseCase getCurrentUserUseCase;

    public CoverageExpectationController(
            ListCoverageExpectationsUseCase listCoverageExpectationsUseCase,
            UpdateCoverageExpectationUseCase updateCoverageExpectationUseCase,
            GetCurrentUserUseCase getCurrentUserUseCase) {
        this.listCoverageExpectationsUseCase = listCoverageExpectationsUseCase;
        this.updateCoverageExpectationUseCase = updateCoverageExpectationUseCase;
        this.getCurrentUserUseCase = getCurrentUserUseCase;
    }

    @GetMapping
    public CoverageExpectationResponse get(@RequestParam UUID sourceId) {
        CoverageExpectation expectation = listCoverageExpectationsUseCase.execute(sourceId)
                .orElseThrow(() -> new NoSuchElementException("coverage_expectation não encontrada para " + sourceId));
        return CoverageExpectationResponse.from(expectation);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CoverageExpectationResponse> update(
            @PathVariable UUID id,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @Valid @RequestBody UpdateCoverageExpectationRequest request,
            @AuthenticationPrincipal CurrentUser currentUser) {

        AppUser actor = getCurrentUserUseCase.execute(currentUser);
        CoverageExpectation updated = updateCoverageExpectationUseCase.execute(
                id,
                ETag.parseIfMatch(ifMatch),
                new UpdateCoverageExpectationCommand(request.schedule(), request.graceDays()),
                actor.id(),
                actor.email());

        return ResponseEntity.ok().eTag(ETag.quote(updated.version())).body(CoverageExpectationResponse.from(updated));
    }
}
