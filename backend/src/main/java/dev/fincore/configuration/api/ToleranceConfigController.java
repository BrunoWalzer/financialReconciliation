package dev.fincore.configuration.api;

import dev.fincore.configuration.application.GetToleranceConfigUseCase;
import dev.fincore.configuration.application.UpdateToleranceConfigCommand;
import dev.fincore.configuration.application.UpdateToleranceConfigUseCase;
import dev.fincore.configuration.domain.ToleranceConfig;
import dev.fincore.identity.application.GetCurrentUserUseCase;
import dev.fincore.identity.domain.AppUser;
import dev.fincore.identity.domain.CurrentUser;
import dev.fincore.shared.web.ETag;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RestController;

/** {@code GET|PUT /config/tolerances/{sourcePairId}} (TDS 19.2). Só {@code ADMINISTRATOR}, {@code If-Match} na mutação. */
@RestController
@RequestMapping("/config/tolerances")
public class ToleranceConfigController {

    private final GetToleranceConfigUseCase getToleranceConfigUseCase;
    private final UpdateToleranceConfigUseCase updateToleranceConfigUseCase;
    private final GetCurrentUserUseCase getCurrentUserUseCase;

    public ToleranceConfigController(
            GetToleranceConfigUseCase getToleranceConfigUseCase,
            UpdateToleranceConfigUseCase updateToleranceConfigUseCase,
            GetCurrentUserUseCase getCurrentUserUseCase) {
        this.getToleranceConfigUseCase = getToleranceConfigUseCase;
        this.updateToleranceConfigUseCase = updateToleranceConfigUseCase;
        this.getCurrentUserUseCase = getCurrentUserUseCase;
    }

    @GetMapping("/{sourcePairId}")
    public ResponseEntity<ToleranceConfigResponse> get(@PathVariable UUID sourcePairId) {
        ToleranceConfig config = getToleranceConfigUseCase.execute(sourcePairId);
        return withETag(config.version(), ToleranceConfigResponse.from(config));
    }

    @PutMapping("/{sourcePairId}")
    public ResponseEntity<ToleranceConfigResponse> update(
            @PathVariable UUID sourcePairId,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @Valid @RequestBody UpdateToleranceConfigRequest request,
            @AuthenticationPrincipal CurrentUser currentUser) {

        AppUser actor = getCurrentUserUseCase.execute(currentUser);
        ToleranceConfig updated = updateToleranceConfigUseCase.execute(
                sourcePairId,
                ETag.parseIfMatch(ifMatch),
                new UpdateToleranceConfigCommand(request.absoluteAmountMinor(), request.currency(), request.aggregateAlertThresholdMinor()),
                actor.id(),
                actor.email());

        return withETag(updated.version(), ToleranceConfigResponse.from(updated));
    }

    private static <T> ResponseEntity<T> withETag(long version, T body) {
        return ResponseEntity.ok().eTag(ETag.quote(version)).body(body);
    }
}
