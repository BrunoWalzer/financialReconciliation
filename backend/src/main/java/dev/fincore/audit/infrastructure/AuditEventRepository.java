package dev.fincore.audit.infrastructure;

import dev.fincore.audit.domain.AuditEvent;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

/**
 * Persistência de {@link AuditEvent}.
 *
 * <p>Estende o marcador {@link Repository}, não {@code JpaRepository} nem
 * {@code CrudRepository}: só os métodos que este módulo precisa são declarados. Um
 * evento de auditoria nunca é apagado nem atualizado pela aplicação — a ausência de
 * {@code deleteById} e {@code delete} aqui é a segunda camada dessa garantia (a primeira
 * e definitiva é o trigger em {@code V1__audit.sql}; a terceira são os testes que tentam
 * violar as duas).
 *
 * <p>{@code findAll(Pageable)} (M2, {@code GET /audit-events}) funciona mesmo sem
 * {@code PagingAndSortingRepository}: o Spring Data JPA sempre implementa este marcador
 * com {@code SimpleJpaRepository} por baixo, e delega qualquer assinatura que corresponda
 * a um método dela — o mesmo mecanismo que já viabiliza {@code save}/{@code findById}.
 */
public interface AuditEventRepository extends Repository<AuditEvent, UUID> {

    AuditEvent save(AuditEvent event);

    Optional<AuditEvent> findById(UUID id);

    Page<AuditEvent> findAll(Pageable pageable);
}
