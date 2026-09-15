package dev.fincore.identity.infrastructure;

import dev.fincore.identity.domain.RefreshToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Persistência de {@link RefreshToken}.
 *
 * <p>{@code findByReplacedById} é o que permite caminhar a família para trás — a partir
 * de um token qualquer, encontrar quem ele substituiu (ver {@code TokenFamilyLocator}).
 * Nenhum {@code deleteById}: um refresh token não é apagado, é revogado.
 *
 * <p>{@code claimForRotation}/{@code linkSuccessor} são a defesa de concorrência da
 * rotação (TDS 15.3: READ COMMITTED em todo lugar; a tabela não tem coluna
 * {@code version} — TDS 7.1 não lista uma). Sem uma escrita condicional, duas chamadas
 * concorrentes de refresh sobre o MESMO token liam a mesma linha "ainda ativa" e ambas
 * rotacionariam com sucesso, cada uma gerando um filho válido — duas sessões vivas a
 * partir de um token que deveria ser uso único.
 *
 * <p>São dois passos, não um, porque {@code replaced_by_id} tem chave estrangeira para a
 * própria tabela ({@code fk_refresh_token_replaced_by}): apontar para o filho antes de
 * ele existir violaria a constraint. {@code claimForRotation} usa
 * {@code WHERE revoked_at IS NULL} como comparação-e-troca — sob READ COMMITTED, a
 * chamada perdedora bloqueia na linha, e ao desbloquear (após a vencedora commitar)
 * reavalia a condição e não encontra mais {@code revoked_at IS NULL}: zero linhas
 * afetadas, o sinal que {@code RefreshSessionUseCase} usa para reagir como reuso. Só
 * depois de vencer essa corrida — e só então — o filho é inserido e
 * {@code linkSuccessor} liga o pai a ele; nesse ponto não há mais corrida a perder,
 * porque só quem venceu {@code claimForRotation} chega até aqui.
 */
public interface RefreshTokenRepository extends Repository<RefreshToken, UUID> {

    RefreshToken save(RefreshToken token);

    Optional<RefreshToken> findById(UUID id);

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    Optional<RefreshToken> findByReplacedById(UUID replacedById);

    @Modifying
    @Query("update RefreshToken t set t.revokedAt = :revokedAt where t.id = :id and t.revokedAt is null")
    int claimForRotation(@Param("id") UUID id, @Param("revokedAt") Instant revokedAt);

    @Modifying
    @Query("update RefreshToken t set t.replacedById = :replacedById where t.id = :id")
    void linkSuccessor(@Param("id") UUID id, @Param("replacedById") UUID replacedById);
}
