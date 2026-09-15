package dev.fincore.identity.infrastructure;

import dev.fincore.identity.domain.AppUser;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/**
 * Persistência de {@link AppUser}. Estende o marcador {@link Repository}, não
 * {@code CrudRepository}: só os métodos que este módulo de fato usa são declarados —
 * mesmo padrão de {@code AuditEventRepository} (M1).
 */
public interface AppUserRepository extends Repository<AppUser, UUID> {

    AppUser save(AppUser user);

    Optional<AppUser> findById(UUID id);

    Optional<AppUser> findByEmail(String email);
}
