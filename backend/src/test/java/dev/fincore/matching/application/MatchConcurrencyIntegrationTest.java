package dev.fincore.matching.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.fincore.AbstractIntegrationTest;
import dev.fincore.configuration.infrastructure.ConfigSnapshotFactory;
import dev.fincore.evidence.domain.Direction;
import dev.fincore.evidence.domain.FinancialRecord;
import dev.fincore.evidence.domain.RecordType;
import dev.fincore.evidence.infrastructure.FinancialRecordRepository;
import dev.fincore.matching.domain.AmountEvaluation;
import dev.fincore.matching.domain.MatchEvidence;
import dev.fincore.matching.domain.MatchProposal;
import dev.fincore.matching.domain.rule.RuleACorrelationKey;
import dev.fincore.shared.configuration.RunConfigSnapshot;
import dev.fincore.shared.money.Currency;
import dev.fincore.shared.money.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * As três cenas obrigatórias de concorrência real do Implementation Plan M9/M10 — "não
 * mockar a constraint" — todas contra PostgreSQL via Testcontainers, nunca contra um
 * {@code if (!exists())} em Java. {@code @RepeatedTest} porque um teste de concorrência
 * instável é defeito, não ruído (mesmo padrão de {@code RefreshTokenConcurrencyTest}).
 */
class MatchConcurrencyIntegrationTest extends AbstractIntegrationTest {

    // UUIDs fixos do seed de referência V3 (ver V3__configuration.sql).
    private static final UUID SOURCE_PAIR_ID = UUID.fromString("00000000-0000-7000-8000-000000000103");
    private static final UUID INTERNAL_SALES_ID = UUID.fromString("00000000-0000-7000-8000-000000000101");
    private static final UUID ACQUIRER_SETTLEMENT_ID = UUID.fromString("00000000-0000-7000-8000-000000000102");

    @Autowired
    private FinancialRecordRepository financialRecordRepository;

    @Autowired
    private ConfigSnapshotFactory configSnapshotFactory;

    @Autowired
    private ClaimAndPersistMatchUseCase claimAndPersistMatchUseCase;

    @Autowired
    private EvaluateMatchesUseCase evaluateMatchesUseCase;

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * Caso A: duas transações tentam persistir o MESMO {@link MatchProposal} (mesmo par,
     * mesma evidência) ao mesmo tempo — exatamente uma cria o {@code match}; a outra recebe
     * {@link MatchClaimConflictException}, nunca uma exceção de constraint crua.
     */
    @RepeatedTest(10)
    void casoADuasTransacoesCriandoOMesmoMatchExatamenteUmaVence() throws Exception {
        String key = "CONC-A-" + UUID.randomUUID();
        FinancialRecord sale = persistSale(key, 50_000);
        FinancialRecord settlement = persistSettlement(key, sale.businessDate().plusDays(1), 50_000);
        MatchProposal proposal = buildAutomaticProposal(sale, settlement);

        ConcurrentOutcome outcome = runConcurrently(2, () -> {
            claimAndPersistMatchUseCase.execute(
                    proposal, Instant.parse("2026-09-21T00:00:00Z"), dev.fincore.audit.domain.ActorRef.system());
            return true;
        });

        assertThat(outcome.succeeded.get()).as("exatamente uma transação cria o match").isEqualTo(1);
        assertThat(outcome.conflicts.get()).as("a outra perde a corrida de match_claim (I-5)").isEqualTo(1);

        Integer matchCount = jdbc.queryForObject(
                "select count(*) from match_claim where financial_record_id in (?, ?)",
                Integer.class, sale.id(), settlement.id());
        assertThat(matchCount).as("cada registro reivindicado uma única vez").isEqualTo(2);
    }

    /**
     * Caso B: dois {@link MatchProposal} DIFERENTES (âncoras esquerdas diferentes) tentam
     * reivindicar o MESMO registro do lado direito — a exclusividade de {@code match_claim}
     * (I-5) garante que só um dos dois consiga capturar aquele registro específico,
     * independente de qual par "fecha" primeiro.
     */
    @RepeatedTest(10)
    void casoBDoisMatchesDisputandoOMesmoRegistroExatamenteUmaReivindicacaoVence() throws Exception {
        String sharedKey = "CONC-B-SHARED-" + UUID.randomUUID();
        FinancialRecord sharedSettlement = persistSettlement(sharedKey, LocalDate.of(2026, 9, 10), 50_000);

        FinancialRecord saleX = persistSale("CONC-B-X-" + UUID.randomUUID(), 50_000);
        FinancialRecord saleY = persistSale("CONC-B-Y-" + UUID.randomUUID(), 50_000);
        // Ambas as propostas visam o MESMO sharedSettlement do lado direito — a corrida real
        // é sobre o financial_record_id dele, não sobre o par inteiro (diferente do Caso A).
        MatchProposal proposalX = buildAutomaticProposal(saleX, sharedSettlement);
        MatchProposal proposalY = buildAutomaticProposal(saleY, sharedSettlement);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        try {
            Future<?> f1 = executor.submit(() -> runOne(proposalX, ready, start, succeeded, conflicts));
            Future<?> f2 = executor.submit(() -> runOne(proposalY, ready, start, succeeded, conflicts));
            ready.await(10, TimeUnit.SECONDS);
            start.countDown();
            f1.get(10, TimeUnit.SECONDS);
            f2.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdown();
        }

        assertThat(succeeded.get()).as("exatamente uma proposta reivindica sharedSettlement").isEqualTo(1);
        assertThat(conflicts.get()).isEqualTo(1);

        Integer claimsOnShared = jdbc.queryForObject(
                "select count(*) from match_claim where financial_record_id = ?", Integer.class, sharedSettlement.id());
        assertThat(claimsOnShared).as("sharedSettlement reivindicado uma única vez").isEqualTo(1);
    }

    /**
     * Caso C: dois "workers" chamam {@link EvaluateMatchesUseCase#execute} ao mesmo tempo
     * contra o MESMO estado de banco (mesmo par elegível) — ponta a ponta, incluindo carga
     * do horizonte real via SQL. Nenhuma duplicidade de correspondência financeira pode
     * sobreviver: no máximo um {@code match} para o par, nunca dois.
     */
    @RepeatedTest(10)
    void casoCDoisWorkersAvaliandoOMesmoConjuntoNuncaProduzemMatchDuplicado() throws Exception {
        String key = "CONC-C-" + UUID.randomUUID();
        FinancialRecord sale = persistSale(key, 70_000);
        FinancialRecord settlement = persistSettlement(key, sale.businessDate().plusDays(1), 70_000);
        RunConfigSnapshot config = configSnapshotFactory.capture(SOURCE_PAIR_ID);
        List<UUID> sourceIds = List.of(INTERNAL_SALES_ID, ACQUIRER_SETTLEMENT_ID);

        ConcurrentOutcome outcome = runConcurrently(2, () -> {
            EvaluateMatchesResult result = evaluateMatchesUseCase.execute(
                    config, sourceIds, LocalDate.of(2026, 9, 21), Instant.parse("2026-09-21T00:00:00Z"),
                    "2026.09.1", Instant.parse("2026-09-21T00:00:00Z"));
            if (result.matchesCreated() > 0) {
                return true;
            }
            if (result.claimConflicts() > 0) {
                throw new MatchClaimConflictException(sale.id(), null);
            }
            // Nenhum dos dois: o outro worker ainda não tinha commitado quando este avaliou —
            // não é vitória nem conflito, é "nada para fazer" nesta chamada específica.
            return false;
        });

        Integer matchCount = jdbc.queryForObject(
                "select count(*) from match_participant where financial_record_id in (?, ?)",
                Integer.class, sale.id(), settlement.id());
        assertThat(matchCount).as("no máximo um match (dois participantes) para o par, nunca dois").isEqualTo(2);
        assertThat(outcome.succeeded.get() + outcome.conflicts.get()).isGreaterThanOrEqualTo(1);
    }

    private void runOne(
            MatchProposal proposal, CountDownLatch ready, CountDownLatch start,
            AtomicInteger succeeded, AtomicInteger conflicts) {
        ready.countDown();
        awaitUninterruptibly(start);
        try {
            claimAndPersistMatchUseCase.execute(proposal, Instant.parse("2026-09-21T00:00:00Z"), dev.fincore.audit.domain.ActorRef.system());
            succeeded.incrementAndGet();
        } catch (MatchClaimConflictException e) {
            conflicts.incrementAndGet();
        }
    }

    private record ConcurrentOutcome(AtomicInteger succeeded, AtomicInteger conflicts) {
    }

    private ConcurrentOutcome runConcurrently(int threadCount, java.util.concurrent.Callable<Boolean> action) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        try {
            List<Future<?>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    awaitUninterruptibly(start);
                    try {
                        boolean won = Boolean.TRUE.equals(action.call());
                        if (won) {
                            succeeded.incrementAndGet();
                        }
                    } catch (MatchClaimConflictException e) {
                        conflicts.incrementAndGet();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }));
            }
            ready.await(10, TimeUnit.SECONDS);
            start.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdown();
        }
        return new ConcurrentOutcome(succeeded, conflicts);
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private MatchProposal buildAutomaticProposal(FinancialRecord left, FinancialRecord right) {
        RunConfigSnapshot config = configSnapshotFactory.capture(SOURCE_PAIR_ID);
        AmountEvaluation evaluation = AmountEvaluation.evaluate(left, right, List.of(), config);
        MatchEvidence evidence = new MatchEvidence(
                RuleACorrelationKey.RULE_ID, 1, "2026.09.1", List.of(),
                MatchEvidence.AmountEvaluationEvidence.from(evaluation), List.of());
        return new MatchProposal(left, right, List.of(), RuleACorrelationKey.RULE_ID, 1, evidence);
    }

    private FinancialRecord persistSale(String correlationKey, long grossMinor) {
        return persist(INTERNAL_SALES_ID, RecordType.SALE, correlationKey, LocalDate.of(2026, 9, 10), grossMinor);
    }

    private FinancialRecord persistSettlement(String correlationKey, LocalDate businessDate, long grossMinor) {
        return persist(ACQUIRER_SETTLEMENT_ID, RecordType.SETTLEMENT, correlationKey, businessDate, grossMinor);
    }

    private FinancialRecord persist(
            UUID sourceId, RecordType recordType, String correlationKey, LocalDate businessDate, long grossMinor) {
        FinancialRecord record = new FinancialRecord(
                sourceId, insertImportBatch(sourceId), 1, null, correlationKey, Direction.CREDIT, recordType,
                new Money(grossMinor, Currency.BRL), null, null, businessDate, null, null, null, null, null,
                "raw-line", UUID.randomUUID().toString().replace("-", "").repeat(2).substring(0, 64),
                Instant.parse("2026-09-01T00:00:00Z"));
        return financialRecordRepository.save(record);
    }

    /** {@code financial_record.import_batch_id} tem FK real (V6) — precisa de uma linha de verdade. */
    private UUID insertImportBatch(UUID sourceId) {
        UUID id = UUID.randomUUID();
        String contentHash = (UUID.randomUUID().toString() + UUID.randomUUID().toString()).replace("-", "").substring(0, 64);
        jdbc.update(
                """
                insert into import_batch (
                    id, source_id, original_filename, content_sha256, byte_size, reference_date,
                    status, storage_key, uploaded_by, uploaded_at)
                values (?, ?, 'test.csv', ?, 10, current_date, 'COMPLETED', 'test-key', ?, now())
                """,
                id, sourceId, contentHash, UUID.randomUUID());
        return id;
    }
}
