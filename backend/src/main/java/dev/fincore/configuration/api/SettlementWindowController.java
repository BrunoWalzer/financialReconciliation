package dev.fincore.configuration.api;

import dev.fincore.configuration.application.ListSettlementWindowsUseCase;
import dev.fincore.configuration.application.UpdateSettlementWindowCommand;
import dev.fincore.configuration.application.UpdateSettlementWindowUseCase;
import dev.fincore.configuration.domain.SettlementWindow;
import dev.fincore.identity.application.GetCurrentUserUseCase;
import dev.fincore.identity.domain.AppUser;
import dev.fincore.identity.domain.CurrentUser;
import dev.fincore.shared.web.ETag;
import jakarta.validation.Valid;
import java.util.List;
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

/** {@code GET|PUT /config/settlement-windows} (TDS 19.2). Sem POST: janelas só nascem por seed no M3. */
@RestController
@RequestMapping("/config/settlement-windows")
public class SettlementWindowController {

    private final ListSettlementWindowsUseCase listSettlementWindowsUseCase;
    private final UpdateSettlementWindowUseCase updateSettlementWindowUseCase;
    private final GetCurrentUserUseCase getCurrentUserUseCase;

    public SettlementWindowController(
            ListSettlementWindowsUseCase listSettlementWindowsUseCase,
            UpdateSettlementWindowUseCase updateSettlementWindowUseCase,
            GetCurrentUserUseCase getCurrentUserUseCase) {
        this.listSettlementWindowsUseCase = listSettlementWindowsUseCase;
        this.updateSettlementWindowUseCase = updateSettlementWindowUseCase;
        this.getCurrentUserUseCase = getCurrentUserUseCase;
    }

    @GetMapping
    public List<SettlementWindowResponse> list(@RequestParam UUID sourcePairId) {
        return listSettlementWindowsUseCase.execute(sourcePairId).stream().map(SettlementWindowResponse::from).toList();
    }

    @PutMapping("/{id}")
    public ResponseEntity<SettlementWindowResponse> update(
            @PathVariable UUID id,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @Valid @RequestBody UpdateSettlementWindowRequest request,
            @AuthenticationPrincipal CurrentUser currentUser) {

        AppUser actor = getCurrentUserUseCase.execute(currentUser);
        SettlementWindow updated = updateSettlementWindowUseCase.execute(
                id,
                ETag.parseIfMatch(ifMatch),
                new UpdateSettlementWindowCommand(request.minDays(), request.maxDays()),
                actor.id(),
                actor.email());

        return ResponseEntity.ok().eTag(ETag.quote(updated.version())).body(SettlementWindowResponse.from(updated));
    }
}
