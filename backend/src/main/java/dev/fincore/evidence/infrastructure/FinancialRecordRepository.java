package dev.fincore.evidence.infrastructure;

import dev.fincore.evidence.domain.FinancialRecord;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.Repository;

/**
 * Persistência de {@link FinancialRecord}. Só {@code save} e leitura: não há {@code update}
 * nem {@code delete} — a imutabilidade é reforçada pela ausência do método, além da trigger
 * de banco (V4) e da ausência de setters na entidade.
 *
 * <p>{@link JpaSpecificationExecutor} monta os filtros opcionais de {@code GET /records}
 * (TDS 20.2) como predicados via Criteria API — cada predicado só é adicionado quando o
 * filtro correspondente não é nulo, o que evita o erro clássico do driver PostgreSQL
 * "could not determine data type of parameter" que o padrão JPQL
 * {@code (:param is null or campo = :param)} produz para parâmetros não textuais.
 */
public interface FinancialRecordRepository
        extends Repository<FinancialRecord, UUID>, JpaSpecificationExecutor<FinancialRecord> {

    FinancialRecord save(FinancialRecord record);

    Optional<FinancialRecord> findById(UUID id);
}
