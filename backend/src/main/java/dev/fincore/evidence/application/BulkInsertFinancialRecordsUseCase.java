package dev.fincore.evidence.application;

import dev.fincore.evidence.domain.FinancialRecord;
import dev.fincore.evidence.infrastructure.FinancialRecordBulkInsert;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Insere um lote de {@link FinancialRecord} vindo da importação (M5), tolerando conflito
 * de identificador de origem sem abortar o lote inteiro (TDS 9.5). Só {@code ingestion}
 * chama isto — nunca um controller: sem endpoint HTTP próprio, daí o
 * {@code isAuthenticated()} genérico em vez de um papel específico, que é responsabilidade
 * de quem inicia a importação.
 *
 * <p>Recebe {@link FinancialRecord} já construído, nunca um tipo de {@code ingestion}:
 * {@code evidence} não pode depender de {@code ingestion} sem criar um ciclo (TDS 4.2).
 */
@Service
public class BulkInsertFinancialRecordsUseCase {

    private final FinancialRecordBulkInsert bulkInsert;

    public BulkInsertFinancialRecordsUseCase(FinancialRecordBulkInsert bulkInsert) {
        this.bulkInsert = bulkInsert;
    }

    @PreAuthorize("isAuthenticated()")
    public BulkInsertResult execute(List<FinancialRecord> records) {
        if (records.isEmpty()) {
            return new BulkInsertResult(0, 0);
        }
        List<Boolean> inserted = bulkInsert.insertIgnoringConflicts(records);
        long insertedCount = inserted.stream().filter(Boolean::booleanValue).count();
        long alreadyExistingCount = inserted.size() - insertedCount;
        return new BulkInsertResult((int) insertedCount, (int) alreadyExistingCount);
    }
}
