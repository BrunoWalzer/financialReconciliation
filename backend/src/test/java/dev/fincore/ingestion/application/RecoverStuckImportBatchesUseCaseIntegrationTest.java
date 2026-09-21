package dev.fincore.ingestion.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.fincore.AbstractIntegrationTest;
import dev.fincore.ingestion.infrastructure.FakeImportJobPublisher;
import dev.fincore.shared.identifier.Uuid7;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * O sweep de trabalhos travados (Implementation Plan M8, TDS 17.5) — substitui outbox
 * transacional. Limite baixo só neste teste, via propriedade dinâmica, para não esperar o
 * padrão de produção (15 minutos).
 */
class RecoverStuckImportBatchesUseCaseIntegrationTest extends AbstractIntegrationTest {

    private static final UUID INTERNAL_SALES_ID = UUID.fromString("00000000-0000-7000-8000-000000000101");

    @DynamicPropertySource
    static void lowStuckThreshold(DynamicPropertyRegistry registry) {
        registry.add("fincore.ingestion.stuck-threshold", () -> "1s");
    }

    @Autowired
    private RecoverStuckImportBatchesUseCase recoverStuckImportBatchesUseCase;

    @Autowired
    private FakeImportJobPublisher fakeImportJobPublisher;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void deveRecuperarLoteTravadoEmReceivedAlemDoPrazoERepublicar() throws InterruptedException {
        UUID batchId = insertBatch("RECEIVED", Instant.now().minusSeconds(30), null);
        fakeImportJobPublisher.clear();
        Thread.sleep(1100); // garante que o threshold de 1s realmente passou.

        int recovered = recoverStuckImportBatchesUseCase.execute();

        assertThat(recovered).isGreaterThanOrEqualTo(1);
        assertThat(fakeImportJobPublisher.published())
                .extracting(FakeImportJobPublisher.PublishedJob::batchId)
                .contains(batchId);
    }

    @Test
    void deveRecuperarLoteTravadoEmProcessingAlemDoPrazo() throws InterruptedException {
        UUID batchId = insertBatch("PROCESSING", Instant.now().minusSeconds(120), Instant.now().minusSeconds(30));
        fakeImportJobPublisher.clear();
        Thread.sleep(1100);

        recoverStuckImportBatchesUseCase.execute();

        assertThat(fakeImportJobPublisher.published())
                .extracting(FakeImportJobPublisher.PublishedJob::batchId)
                .contains(batchId);
    }

    @Test
    void naoDeveRepublicarLoteEmEstadoTerminal() throws InterruptedException {
        UUID batchId = insertBatch("COMPLETED", Instant.now().minusSeconds(120), Instant.now().minusSeconds(120));
        fakeImportJobPublisher.clear();
        Thread.sleep(1100);

        recoverStuckImportBatchesUseCase.execute();

        assertThat(fakeImportJobPublisher.published())
                .extracting(FakeImportJobPublisher.PublishedJob::batchId)
                .doesNotContain(batchId);
    }

    @Test
    void naoDeveRepublicarLoteRecemCriadoDentroDoPrazo() {
        UUID batchId = insertBatch("RECEIVED", Instant.now(), null);
        fakeImportJobPublisher.clear();

        recoverStuckImportBatchesUseCase.execute();

        assertThat(fakeImportJobPublisher.published())
                .extracting(FakeImportJobPublisher.PublishedJob::batchId)
                .doesNotContain(batchId);
    }

    private UUID insertBatch(String status, Instant uploadedAt, Instant startedAt) {
        UUID id = Uuid7.generate();
        String hash = id.toString().replace("-", "").repeat(2).substring(0, 64);
        jdbc.update(
                """
                insert into import_batch (id, source_id, original_filename, content_sha256, byte_size,
                    reference_date, status, storage_key, uploaded_by, uploaded_at, started_at, correlation_id, version)
                values (?, ?, 'fixture.csv', ?, 1, ?, ?, ?, ?, ?, ?, ?, 0)
                """,
                id, INTERNAL_SALES_ID, hash, LocalDate.now(), status, "fixture-key-" + id, Uuid7.generate(),
                Timestamp.from(uploadedAt), startedAt == null ? null : Timestamp.from(startedAt), "corr-" + id);
        return id;
    }
}
