package dev.fincore.audit.infrastructure;

import dev.fincore.audit.domain.AuditEvent;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/**
 * Persistência de {@link AuditEvent}.
 *
 * <p>Estende o marcador {@link Repository}, não {@code JpaRepository} nem
 * {@code CrudRepository}: só {@code save} e {@code findById} são declarados, porque são
 * os únicos que este módulo precisa. Um evento de auditoria nunca é apagado nem
 * atualizado pela aplicação — a ausência de {@code deleteById} e {@code delete} aqui é a
 * segunda camada dessa garantia (a primeira e definitiva é o trigger em {@code V1__audit.sql};
 * a terceira são os testes que tentam violar as duas).
 */
public interface AuditEventRepository extends Repository<AuditEvent, UUID> {

    AuditEvent save(AuditEvent event);

    Optional<AuditEvent> findById(UUID id);
}
