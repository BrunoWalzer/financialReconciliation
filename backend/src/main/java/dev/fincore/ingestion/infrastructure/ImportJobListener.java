package dev.fincore.ingestion.infrastructure;

import dev.fincore.ingestion.application.ProcessImportBatchUseCase;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.stereotype.Component;

/**
 * Consome {@code fincore.import.process} (TDS 17.1) e delega para
 * {@link ProcessImportBatchUseCase} — nenhuma lógica de parsing/persistência aqui, só a
 * ponte entre a mensagem e o caso de uso.
 *
 * <p>{@code acknowledge-mode} fica em AUTO (padrão, ver {@code application.yml}): o
 * container só confirma a mensagem depois que este método retorna sem lançar — ou seja,
 * depois que {@link ProcessImportBatchUseCase} já commitou a transação final. Uma exceção
 * que escapa daqui aciona o retry configurado (3x, backoff 1s/4s/16s); esgotado, o
 * {@link ImportJobFailureRecoverer} marca o lote {@code FAILED} e a mensagem morta.
 *
 * <p>{@code SecurityContextHolder}: o worker não é um usuário HTTP (Implementation Plan M8,
 * seção 18) — estabelece uma autenticação de sistema só para que os casos de uso internos
 * com {@code isAuthenticated()} (M5/M7) continuem funcionando, e a limpa ao final. A
 * auditoria em si usa {@code ActorRef.system(batchId)} (M1), mecanismo diferente e já
 * existente — isto aqui é só o lado do Spring Security.
 */
@Component
@Profile("!test | rabbit-it")
public class ImportJobListener {

    private static final Logger log = LoggerFactory.getLogger(ImportJobListener.class);

    private final ProcessImportBatchUseCase processImportBatchUseCase;

    public ImportJobListener(ProcessImportBatchUseCase processImportBatchUseCase) {
        this.processImportBatchUseCase = processImportBatchUseCase;
    }

    @RabbitListener(queues = "${fincore.messaging.import-process-queue}")
    public void onMessage(ImportJobMessage message) {
        log.info(
                "Consumindo fincore.import.process: batchId={} correlationId={} attempt={}",
                message.batchId(), message.correlationId(), message.attempt());

        var systemAuthentication = new UsernamePasswordAuthenticationToken(
                "SYSTEM", null, List.of(new SimpleGrantedAuthority("SYSTEM")));
        SecurityContextHolder.setContext(new SecurityContextImpl(systemAuthentication));
        try {
            processImportBatchUseCase.execute(message.batchId());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
