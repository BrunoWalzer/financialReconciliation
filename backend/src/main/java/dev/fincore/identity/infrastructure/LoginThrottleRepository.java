package dev.fincore.identity.infrastructure;

import dev.fincore.identity.domain.LoginThrottle;
import java.util.Optional;
import org.springframework.data.repository.Repository;

/** Persistência de {@link LoginThrottle}. Chave é o e-mail submetido (TDS 7.1). */
public interface LoginThrottleRepository extends Repository<LoginThrottle, String> {

    LoginThrottle save(LoginThrottle throttle);

    Optional<LoginThrottle> findById(String email);
}
