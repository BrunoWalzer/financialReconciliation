package dev.fincore.evidence.application;

import dev.fincore.evidence.domain.FinancialRecord;
import dev.fincore.evidence.infrastructure.FinancialRecordRepository;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/** {@code GET /records} — leitura para todos os papéis autenticados (TDS 20.2). */
@Service
public class SearchFinancialRecordsUseCase {

    private final FinancialRecordRepository repository;

    public SearchFinancialRecordsUseCase(FinancialRecordRepository repository) {
        this.repository = repository;
    }

    @PreAuthorize("isAuthenticated()")
    public Page<FinancialRecord> execute(FinancialRecordSearchFilter filter, Pageable pageable) {
        return repository.findAll(toSpecification(filter), pageable);
    }

    /**
     * Cada predicado só é adicionado quando o filtro correspondente não é nulo — diferente
     * de {@code (:param is null or campo = :param)} em JPQL, que faz o driver PostgreSQL
     * falhar ao inferir o tipo do parâmetro para colunas não textuais (DATE, BIGINT).
     */
    private static Specification<FinancialRecord> toSpecification(FinancialRecordSearchFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.sourceId() != null) {
                predicates.add(cb.equal(root.get("sourceId"), filter.sourceId()));
            }
            if (filter.externalId() != null) {
                predicates.add(cb.equal(root.get("externalId"), filter.externalId()));
            }
            if (filter.correlationKey() != null) {
                predicates.add(cb.equal(root.get("correlationKey"), filter.correlationKey()));
            }
            if (filter.grossAmountMinor() != null) {
                predicates.add(cb.equal(root.get("grossAmountMinor"), filter.grossAmountMinor()));
            }
            if (filter.businessDateFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("businessDate"), filter.businessDateFrom()));
            }
            if (filter.businessDateTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("businessDate"), filter.businessDateTo()));
            }
            if (filter.paymentMethod() != null) {
                predicates.add(cb.equal(root.get("paymentMethod"), filter.paymentMethod()));
            }
            if (filter.counterpartyDocument() != null) {
                predicates.add(cb.equal(root.get("counterpartyDocument"), filter.counterpartyDocument()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
