package dev.fincore.configuration.infrastructure;

import dev.fincore.configuration.domain.Source;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/**
 * Persistência de {@link Source}. Sem {@code save} exposto: nenhum caso de uso do M3 cria
 * ou altera uma fonte via API — só o seed de referência (V3) o faz, em SQL puro.
 */
public interface SourceRepository extends Repository<Source, UUID> {

    Optional<Source> findById(UUID id);

    Optional<Source> findByCode(String code);

    List<Source> findAll();
}
