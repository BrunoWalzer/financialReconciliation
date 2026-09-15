package dev.fincore.audit.application;

import dev.fincore.audit.domain.AuditEvent;
import dev.fincore.audit.infrastructure.AuditEventRepository;
import dev.fincore.shared.correlation.CorrelationId;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Registra fatos de auditoria (TDS 22.1).
 *
 * <p>Classe concreta, não interface: o Plano proíbe interface com implementação única
 * fora de {@code RecordParser} e {@code FileStorage} (seção 13, regra 8), e não há
 * segunda implementação prevista para este serviço.
 *
 * <p><b>Sem {@code @Transactional} de propósito.</b> {@link AuditEventRepository#save}
 * (Spring Data JPA) já abre uma transação quando não existe nenhuma ativa, e adere à
 * transação do chamador quando existe — que é exatamente a regra invíolavel do TDS 16.1:
 * "auditoria é sempre da mesma transação da mudança". Anotar este método criaria uma
 * segunda decisão de propagação onde a primeira já basta, e {@code REQUIRES_NEW} está
 * fechado neste estágio (TDS 16.1 regra 6; ArchUnit {@code NO_REQUIRES_NEW_PROPAGATION}).
 */
@Service
public class AuditService {

    private final AuditEventRepository repository;

    public AuditService(AuditEventRepository repository) {
        this.repository = repository;
    }

    /**
     * Persiste o evento. Se {@link AuditEventRequest#correlationId()} não foi informado,
     * usa o da requisição em curso, quando houver uma.
     */
    public AuditEvent record(AuditEventRequest request) {
        Objects.requireNonNull(request, "request é obrigatório");

        String correlationId = resolveCorrelationId(request.correlationId());

        AuditEvent event = new AuditEvent(
                request.actor(),
                request.action(),
                request.entityType(),
                request.entityId(),
                request.beforeState(),
                request.afterState(),
                request.justification(),
                correlationId,
                request.ipAddress());

        return repository.save(event);
    }

    private static String resolveCorrelationId(String explicit) {
        if (explicit != null && !explicit.isBlank()) {
            return explicit;
        }
        return CorrelationId.current().orElse(null);
    }
}
