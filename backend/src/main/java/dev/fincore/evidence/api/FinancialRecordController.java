package dev.fincore.evidence.api;

import dev.fincore.configuration.application.GetSourceByCodeUseCase;
import dev.fincore.evidence.application.FinancialRecordSearchFilter;
import dev.fincore.evidence.application.GetFinancialRecordUseCase;
import dev.fincore.evidence.application.ListRecordIntegrityFlagsUseCase;
import dev.fincore.evidence.application.SearchFinancialRecordsUseCase;
import dev.fincore.evidence.domain.FinancialRecord;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * {@code GET /records} · {@code GET /records/{id}} (TDS 20.2) — leitura para todos os
 * papéis autenticados. {@code sourceCode} é resolvido para {@code sourceId} aqui, na camada
 * api, porque {@code evidence.application} não depende de {@code configuration} (mesmo
 * padrão de {@code configuration.api} resolvendo {@code CurrentUser} no M3).
 *
 * <p>O filtro {@code state} do documento não está disponível — depende da projeção
 * derivada de {@code Match}/{@code Divergence}, que não existem neste milestone.
 */
@RestController
@RequestMapping("/records")
public class FinancialRecordController {

    private static final int DEFAULT_SIZE = 25;
    private static final int MAX_SIZE = 100;
    private static final Set<String> SORTABLE_FIELDS = Set.of("businessDate", "createdAt");

    private final SearchFinancialRecordsUseCase searchFinancialRecordsUseCase;
    private final GetFinancialRecordUseCase getFinancialRecordUseCase;
    private final ListRecordIntegrityFlagsUseCase listRecordIntegrityFlagsUseCase;
    private final GetSourceByCodeUseCase getSourceByCodeUseCase;

    public FinancialRecordController(
            SearchFinancialRecordsUseCase searchFinancialRecordsUseCase,
            GetFinancialRecordUseCase getFinancialRecordUseCase,
            ListRecordIntegrityFlagsUseCase listRecordIntegrityFlagsUseCase,
            GetSourceByCodeUseCase getSourceByCodeUseCase) {
        this.searchFinancialRecordsUseCase = searchFinancialRecordsUseCase;
        this.getFinancialRecordUseCase = getFinancialRecordUseCase;
        this.listRecordIntegrityFlagsUseCase = listRecordIntegrityFlagsUseCase;
        this.getSourceByCodeUseCase = getSourceByCodeUseCase;
    }

    @GetMapping
    public PageResponse<FinancialRecordResponse> search(
            @RequestParam(required = false) String sourceCode,
            @RequestParam(required = false) String externalId,
            @RequestParam(required = false) String correlationKey,
            @RequestParam(required = false) Long amountMinor,
            @RequestParam(required = false) LocalDate businessDateFrom,
            @RequestParam(required = false) LocalDate businessDateTo,
            @RequestParam(required = false) String paymentMethod,
            @RequestParam(required = false) String document,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + DEFAULT_SIZE) int size,
            @RequestParam(defaultValue = "businessDate,desc") String sort) {

        UUID sourceId = sourceCode == null ? null : resolveSourceId(sourceCode);
        FinancialRecordSearchFilter filter = new FinancialRecordSearchFilter(
                sourceId, externalId, correlationKey, amountMinor, businessDateFrom, businessDateTo,
                paymentMethod, document);

        Pageable pageable = PageRequest.of(page, Math.min(size, MAX_SIZE), parseSort(sort));
        Page<FinancialRecord> records = searchFinancialRecordsUseCase.execute(filter, pageable);
        return PageResponse.of(records.map(FinancialRecordResponse::from));
    }

    @GetMapping("/{id}")
    public FinancialRecordResponse get(@PathVariable UUID id) {
        FinancialRecord record = getFinancialRecordUseCase.execute(id);
        List<RecordIntegrityFlagResponse> flags = listRecordIntegrityFlagsUseCase.execute(id).stream()
                .map(RecordIntegrityFlagResponse::from)
                .toList();
        return FinancialRecordResponse.from(record, flags);
    }

    private UUID resolveSourceId(String sourceCode) {
        return getSourceByCodeUseCase.execute(sourceCode)
                .map(source -> source.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "sourceCode desconhecido: " + sourceCode));
    }

    private static Sort parseSort(String sort) {
        String[] parts = sort.split(",", 2);
        String field = parts[0];
        if (!SORTABLE_FIELDS.contains(field)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "campo de ordenação desconhecido: " + field);
        }
        Sort.Direction direction = parts.length > 1 && "asc".equalsIgnoreCase(parts[1])
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        return Sort.by(direction, field);
    }
}
