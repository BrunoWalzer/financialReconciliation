package dev.fincore.identity.application;

import dev.fincore.audit.application.AuditEventRequest;
import dev.fincore.audit.application.AuditService;
import dev.fincore.audit.domain.ActorRef;
import dev.fincore.identity.domain.AppUser;
import dev.fincore.identity.domain.CurrentUser;
import dev.fincore.identity.domain.UserRole;
import dev.fincore.identity.infrastructure.AppUserRepository;
import java.time.Clock;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Concede ou revoga um papel (Domain §27.2: "altera papel de usuário — concede
 * capacidade de decisão financeira", uma das operações mais sensíveis do sistema).
 *
 * <p>Sem endpoint HTTP neste milestone — o Implementation Plan lista este caso de uso na
 * separação de responsabilidades do M2, mas a API do M2 não inclui gerenciamento de
 * usuários ("CRUD de usuários por API" é explicitamente adiado, fora do MVP). Existe
 * porque precisa existir e ser auditável — não porque tem para onde ser chamado ainda.
 */
@Service
public class ChangeUserRoleUseCase {

    /** Concede, ou revoga. */
    public enum Action {
        GRANT,
        REVOKE
    }

    private final AppUserRepository appUserRepository;
    private final AuditService auditService;
    private final Clock clock;

    public ChangeUserRoleUseCase(AppUserRepository appUserRepository, AuditService auditService, Clock clock) {
        this.appUserRepository = appUserRepository;
        this.auditService = auditService;
        this.clock = clock;
    }

    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    @Transactional
    public void execute(UUID targetUserId, UserRole role, Action action, CurrentUser actor) {
        AppUser user = appUserRepository.findById(targetUserId)
                .orElseThrow(() -> new NoSuchElementException("usuário não encontrado"));

        Set<UserRole> before = user.roles();
        if (action == Action.GRANT) {
            user.grantRole(role, clock.instant());
        } else {
            user.revokeRole(role, clock.instant());
        }
        appUserRepository.save(user);

        ActorRef actorRef = appUserRepository.findById(actor.userId())
                .map(admin -> ActorRef.user(admin.id(), admin.email()))
                .orElseGet(ActorRef::system);

        auditService.record(new AuditEventRequest(
                actorRef,
                "USER_ROLE_CHANGED",
                "AppUser",
                user.id(),
                toJsonArray(before),
                toJsonArray(user.roles()),
                null,
                null,
                null));
    }

    private static String toJsonArray(Set<UserRole> roles) {
        if (roles.isEmpty()) {
            return "[]";
        }
        return roles.stream().map(UserRole::name).collect(Collectors.joining("\",\"", "[\"", "\"]"));
    }
}
