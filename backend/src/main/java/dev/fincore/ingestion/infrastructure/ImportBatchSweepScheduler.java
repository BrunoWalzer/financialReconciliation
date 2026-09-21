package dev.fincore.ingestion.infrastructure;

import dev.fincore.ingestion.application.RecoverStuckImportBatchesUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * O sweep de trabalhos travados (Implementation Plan M8, TDS 17.5) — substitui outbox
 * transacional. {@code pg_try_advisory_lock} garante que, com múltiplas instâncias da
 * aplicação rodando, só uma executa por vez; as demais desistem imediatamente em vez de
 * competir ou duplicar trabalho.
 *
 * <p>TDS 17.5 descreve um único job diário por par de fontes que também enfileira o sweep
 * de cobertura/envelhecimento (M15, não implementado) e expira atribuições (M14, não
 * implementado). Este milestone implementa só a recuperação de trabalhos travados — e
 * "travado" é propriedade do {@code import_batch} (uma fonte), não de um par de fontes, daí
 * este job rodar sozinho e mais frequente que diário (padrão a cada 5 minutos,
 * {@code fincore.ingestion.sweep-cron}) em vez de esperar pelo job diário combinado que
 * ainda não existe. Decisão registrada no relatório do M8.
 */
@Component
@Profile("!test | rabbit-it")
public class ImportBatchSweepScheduler {

    private static final Logger log = LoggerFactory.getLogger(ImportBatchSweepScheduler.class);

    /** Chave arbitrária, só precisa ser estável e não colidir com outro uso de advisory lock. */
    private static final long ADVISORY_LOCK_KEY = 8_374_520_193L;

    private final JdbcTemplate jdbc;
    private final RecoverStuckImportBatchesUseCase recoverStuckImportBatchesUseCase;

    public ImportBatchSweepScheduler(JdbcTemplate jdbc, RecoverStuckImportBatchesUseCase recoverStuckImportBatchesUseCase) {
        this.jdbc = jdbc;
        this.recoverStuckImportBatchesUseCase = recoverStuckImportBatchesUseCase;
    }

    @Scheduled(cron = "${fincore.ingestion.sweep-cron}")
    public void sweep() {
        Boolean acquired = jdbc.queryForObject("SELECT pg_try_advisory_lock(?)", Boolean.class, ADVISORY_LOCK_KEY);
        if (!Boolean.TRUE.equals(acquired)) {
            log.debug("sweep de import_batch já em execução em outra instância — pulando este ciclo");
            return;
        }
        try {
            int recovered = recoverStuckImportBatchesUseCase.execute();
            if (recovered > 0) {
                log.info("sweep recuperou {} import_batch travado(s)", recovered);
            }
        } finally {
            jdbc.queryForObject("SELECT pg_advisory_unlock(?)", Boolean.class, ADVISORY_LOCK_KEY);
        }
    }
}
