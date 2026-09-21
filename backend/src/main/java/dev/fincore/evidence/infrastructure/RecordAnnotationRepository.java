package dev.fincore.evidence.infrastructure;

import dev.fincore.evidence.domain.RecordAnnotation;
import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Persistência de {@link RecordAnnotation}. Só {@code save} e leitura — append-only. */
public interface RecordAnnotationRepository extends Repository<RecordAnnotation, UUID> {

    RecordAnnotation save(RecordAnnotation annotation);

    List<RecordAnnotation> findByFinancialRecordIdOrderByCreatedAtAsc(UUID financialRecordId);
}
