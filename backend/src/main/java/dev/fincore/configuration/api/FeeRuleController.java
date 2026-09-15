package dev.fincore.configuration.api;

import dev.fincore.configuration.application.CreateFeeRuleCommand;
import dev.fincore.configuration.application.CreateFeeRuleUseCase;
import dev.fincore.configuration.application.ListFeeRulesUseCase;
import dev.fincore.configuration.application.UpdateFeeRuleCommand;
import dev.fincore.configuration.application.UpdateFeeRuleUseCase;
import dev.fincore.configuration.domain.FeeRule;
import dev.fincore.identity.application.GetCurrentUserUseCase;
import dev.fincore.identity.domain.AppUser;
import dev.fincore.identity.domain.CurrentUser;
import dev.fincore.shared.web.ETag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET|POST|PUT /config/fee-rules} (TDS 19.2). Só {@code ADMINISTRATOR}, {@code If-Match} na mutação. */
@RestController
@RequestMapping("/config/fee-rules")
public class FeeRuleController {

    private final ListFeeRulesUseCase listFeeRulesUseCase;
    private final CreateFeeRuleUseCase createFeeRuleUseCase;
    private final UpdateFeeRuleUseCase updateFeeRuleUseCase;
    private final GetCurrentUserUseCase getCurrentUserUseCase;

    public FeeRuleController(
            ListFeeRulesUseCase listFeeRulesUseCase,
            CreateFeeRuleUseCase createFeeRuleUseCase,
            UpdateFeeRuleUseCase updateFeeRuleUseCase,
            GetCurrentUserUseCase getCurrentUserUseCase) {
        this.listFeeRulesUseCase = listFeeRulesUseCase;
        this.createFeeRuleUseCase = createFeeRuleUseCase;
        this.updateFeeRuleUseCase = updateFeeRuleUseCase;
        this.getCurrentUserUseCase = getCurrentUserUseCase;
    }

    @GetMapping
    public List<FeeRuleResponse> list(@RequestParam UUID sourceId) {
        return listFeeRulesUseCase.execute(sourceId).stream().map(FeeRuleResponse::from).toList();
    }

    @PostMapping
    public ResponseEntity<FeeRuleResponse> create(
            @Valid @RequestBody CreateFeeRuleRequest request, @AuthenticationPrincipal CurrentUser currentUser) {

        AppUser actor = getCurrentUserUseCase.execute(currentUser);
        FeeRule created = createFeeRuleUseCase.execute(
                new CreateFeeRuleCommand(
                        request.sourceId(), request.paymentMethod(), request.percentageBp(),
                        request.fixedAmountMinor(), request.roundingMode()),
                actor.id(),
                actor.email());

        return ResponseEntity.created(URI.create("/config/fee-rules/" + created.id()))
                .eTag(ETag.quote(created.version()))
                .body(FeeRuleResponse.from(created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<FeeRuleResponse> update(
            @PathVariable UUID id,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @Valid @RequestBody UpdateFeeRuleRequest request,
            @AuthenticationPrincipal CurrentUser currentUser) {

        AppUser actor = getCurrentUserUseCase.execute(currentUser);
        FeeRule updated = updateFeeRuleUseCase.execute(
                id,
                ETag.parseIfMatch(ifMatch),
                new UpdateFeeRuleCommand(request.percentageBp(), request.fixedAmountMinor(), request.roundingMode()),
                actor.id(),
                actor.email());

        return ResponseEntity.ok().eTag(ETag.quote(updated.version())).body(FeeRuleResponse.from(updated));
    }
}
