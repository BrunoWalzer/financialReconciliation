package dev.fincore.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.fincore.AbstractIntegrationTest;
import dev.fincore.identity.domain.AppUser;
import dev.fincore.identity.domain.CurrentUser;
import dev.fincore.identity.domain.UserRole;
import dev.fincore.identity.infrastructure.AppUserRepository;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * {@link ChangeUserRoleUseCase} contra PostgreSQL real: os três papéis exercitados, com
 * uma negativa para cada não-administrador (Implementation Plan M2, critério de aceite
 * 1), e a auditoria de "troca de papel" (TDS 22.2).
 *
 * <p>{@code @WithMockUser(authorities = ...)} popula o {@code SecurityContext} para que
 * {@code @PreAuthorize} avalie de verdade — sem isso, o método rodaria sem nenhuma
 * autenticação e o teste não provaria nada sobre autorização.
 */
class ChangeUserRoleUseCaseIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ChangeUserRoleUseCase changeUserRoleUseCase;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @WithMockUser(authorities = "ADMINISTRATOR")
    void administradorDeveConcederPapel() {
        AppUser target = createUser("alvo1@fincore.dev", UserRole.AUDITOR);
        AppUser admin = createUser("admin-ator1@fincore.dev", UserRole.ADMINISTRATOR);

        changeUserRoleUseCase.execute(
                target.id(), UserRole.RECONCILIATION_ANALYST, ChangeUserRoleUseCase.Action.GRANT,
                new CurrentUser(admin.id(), Set.of(UserRole.ADMINISTRATOR)));

        AppUser reloaded = appUserRepository.findById(target.id()).orElseThrow();
        assertThat(reloaded.roles()).containsExactlyInAnyOrder(UserRole.AUDITOR, UserRole.RECONCILIATION_ANALYST);
    }

    @Test
    @WithMockUser(authorities = "ADMINISTRATOR")
    void administradorDeveRevogarPapelERegistrarAuditoria() {
        AppUser target = createUser("alvo2@fincore.dev", UserRole.AUDITOR, UserRole.RECONCILIATION_ANALYST);
        AppUser admin = createUser("admin-ator2@fincore.dev", UserRole.ADMINISTRATOR);

        changeUserRoleUseCase.execute(
                target.id(), UserRole.RECONCILIATION_ANALYST, ChangeUserRoleUseCase.Action.REVOKE,
                new CurrentUser(admin.id(), Set.of(UserRole.ADMINISTRATOR)));

        AppUser reloaded = appUserRepository.findById(target.id()).orElseThrow();
        assertThat(reloaded.roles()).containsExactly(UserRole.AUDITOR);

        Integer auditCount = jdbc.queryForObject(
                "select count(*) from audit_event where action = 'USER_ROLE_CHANGED' and entity_id = ?",
                Integer.class,
                target.id());
        assertThat(auditCount).isEqualTo(1);
    }

    @Test
    @WithMockUser(authorities = "AUDITOR")
    void auditorNaoDeveConseguirAlterarPapel() {
        AppUser target = createUser("alvo3@fincore.dev", UserRole.AUDITOR);

        assertThatThrownBy(() -> changeUserRoleUseCase.execute(
                        target.id(), UserRole.ADMINISTRATOR, ChangeUserRoleUseCase.Action.GRANT,
                        new CurrentUser(target.id(), Set.of(UserRole.AUDITOR))))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(authorities = "RECONCILIATION_ANALYST")
    void analistaNaoDeveConseguirAlterarPapel() {
        AppUser target = createUser("alvo4@fincore.dev", UserRole.AUDITOR);

        assertThatThrownBy(() -> changeUserRoleUseCase.execute(
                        target.id(), UserRole.ADMINISTRATOR, ChangeUserRoleUseCase.Action.GRANT,
                        new CurrentUser(target.id(), Set.of(UserRole.RECONCILIATION_ANALYST))))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void naoDeveConseguirAlterarPapelSemAutenticacao() {
        AppUser target = createUser("alvo5@fincore.dev", UserRole.AUDITOR);

        assertThatThrownBy(() -> changeUserRoleUseCase.execute(
                        target.id(), UserRole.ADMINISTRATOR, ChangeUserRoleUseCase.Action.GRANT,
                        new CurrentUser(target.id(), Set.of(UserRole.AUDITOR))))
                .isInstanceOf(RuntimeException.class);
    }

    private AppUser createUser(String email, UserRole... roles) {
        AppUser user = new AppUser(
                email, passwordEncoder.encode("qualquer-senha"), "Nome", EnumSet.copyOf(java.util.List.of(roles)), Instant.now());
        return appUserRepository.save(user);
    }
}
