package dev.fincore.ingestion.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.fincore.shared.identifier.Uuid7;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** {@link ImportBatch} — a máquina de estados de uma importação (Domain §9.2). */
class ImportBatchTest {

    private static final Instant NOW = Instant.parse("2026-09-11T12:00:00Z");

    @Test
    void deveNascerRecebido() {
        ImportBatch batch = newBatch();

        assertThat(batch.status()).isEqualTo(ImportStatus.RECEIVED);
        assertThat(batch.id()).isNotNull();
    }

    @Test
    void deveTransicionarParaProcessing() {
        ImportBatch batch = newBatch();

        batch.startProcessing(NOW);

        assertThat(batch.status()).isEqualTo(ImportStatus.PROCESSING);
        assertThat(batch.startedAt()).isEqualTo(NOW);
    }

    @Test
    void naoDevePermitirIniciarProcessamentoDuasVezes() {
        ImportBatch batch = newBatch();
        batch.startProcessing(NOW);

        assertThatThrownBy(() -> batch.startProcessing(NOW)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejeicaoEstruturalZeraContagens() {
        ImportBatch batch = newBatch();
        batch.startProcessing(NOW);

        batch.rejectStructurally("EMPTY_FILE", NOW);

        assertThat(batch.status()).isEqualTo(ImportStatus.REJECTED);
        assertThat(batch.rejectionReason()).isEqualTo("EMPTY_FILE");
        assertThat(batch.totalLines()).isZero();
        assertThat(batch.acceptedCount()).isZero();
        assertThat(batch.rejectedCount()).isZero();
    }

    @Test
    void naoDevePermitirRejeicaoEstruturalAntesDeProcessing() {
        ImportBatch batch = newBatch();

        assertThatThrownBy(() -> batch.rejectStructurally("EMPTY_FILE", NOW)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void completeComTodasAceitasEhCompleted() {
        ImportBatch batch = newBatch();
        batch.startProcessing(NOW);

        batch.complete(10, 10, 0, 0, NOW);

        assertThat(batch.status()).isEqualTo(ImportStatus.COMPLETED);
    }

    @Test
    void completeComAceitasERejeitadasEhCompletedWithRejects() {
        ImportBatch batch = newBatch();
        batch.startProcessing(NOW);

        batch.complete(10, 7, 3, 0, NOW);

        assertThat(batch.status()).isEqualTo(ImportStatus.COMPLETED_WITH_REJECTS);
    }

    @Test
    void completeComTodasRejeitadasEhRejectedComMotivoAllLinesInvalid() {
        // TDS 9.7: REJECTED significa zero registros financeiros, não zero linhas rejeitadas.
        ImportBatch batch = newBatch();
        batch.startProcessing(NOW);

        batch.complete(5, 0, 5, 0, NOW);

        assertThat(batch.status()).isEqualTo(ImportStatus.REJECTED);
        assertThat(batch.rejectionReason()).isEqualTo("ALL_LINES_INVALID");
        assertThat(batch.rejectedCount()).isEqualTo(5);
    }

    @Test
    void deveFalharEPreservarStatusFailed() {
        ImportBatch batch = newBatch();
        batch.startProcessing(NOW);

        batch.fail("erro simulado no lote 3", NOW);

        assertThat(batch.status()).isEqualTo(ImportStatus.FAILED);
        assertThat(batch.rejectionReason()).isEqualTo("erro simulado no lote 3");
    }

    @Test
    void naoDevePermitirCompleteAposEstadoTerminal() {
        ImportBatch batch = newBatch();
        batch.startProcessing(NOW);
        batch.complete(1, 1, 0, 0, NOW);

        assertThatThrownBy(() -> batch.complete(1, 1, 0, 0, NOW)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void deveExigirMotivoDeReimportacaoComAoMenosDezCaracteres() {
        assertThatThrownBy(() -> new ImportBatch(
                        Uuid7.generate(), "arquivo.csv", "a".repeat(64), 100, LocalDate.of(2026, 9, 10),
                        "key", Uuid7.generate(), NOW, Uuid7.generate(), "curto", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void devePermitirReimportacaoComMotivoValido() {
        ImportBatch batch = new ImportBatch(
                Uuid7.generate(), "arquivo.csv", "a".repeat(64), 100, LocalDate.of(2026, 9, 10),
                "key", Uuid7.generate(), NOW, Uuid7.generate(), "arquivo corrigido após erro de digitação", null);

        assertThat(batch.reimportReason()).isEqualTo("arquivo corrigido após erro de digitação");
    }

    private static ImportBatch newBatch() {
        UUID sourceId = Uuid7.generate();
        return new ImportBatch(
                sourceId, "vendas_2026-09-10.csv", "a".repeat(64), 1024, LocalDate.of(2026, 9, 10),
                "storage-key", Uuid7.generate(), NOW, null, null, "corr-1");
    }
}
