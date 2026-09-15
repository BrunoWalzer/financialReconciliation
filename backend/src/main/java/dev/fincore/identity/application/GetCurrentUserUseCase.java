package dev.fincore.identity.application;

import dev.fincore.identity.domain.AppUser;
import dev.fincore.identity.domain.CurrentUser;
import dev.fincore.identity.infrastructure.AppUserRepository;
import java.util.NoSuchElementException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * {@code GET /auth/me} (TDS 19.2). O JWT só carrega {@code sub} e {@code roles} —
 * deliberadamente mínimo (seção "JWT / Access Token" do M2) — então e-mail e nome de
 * exibição exigem esta consulta.
 */
@Service
public class GetCurrentUserUseCase {

    private final AppUserRepository appUserRepository;

    public GetCurrentUserUseCase(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @PreAuthorize("isAuthenticated()")
    public AppUser execute(CurrentUser currentUser) {
        return appUserRepository.findById(currentUser.userId())
                .orElseThrow(() -> new NoSuchElementException("usuário autenticado não encontrado"));
    }
}
