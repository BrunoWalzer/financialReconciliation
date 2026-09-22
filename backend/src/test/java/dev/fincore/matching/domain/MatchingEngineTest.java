package dev.fincore.matching.domain;

import static dev.fincore.matching.domain.EvaluationContextFixture.context;
import static dev.fincore.matching.domain.FinancialRecordFixture.aRecord;
import static org.assertj.core.api.Assertions.assertThat;

import dev.fincore.evidence.domain.Direction;
import dev.fincore.evidence.domain.FinancialRecord;
import dev.fincore.evidence.domain.RecordIntegrityFlagType;
import dev.fincore.evidence.domain.RecordType;
import dev.fincore.matching.domain.rule.RuleACorrelationKey;
import dev.fincore.matching.domain.rule.RuleBCompositeMutualUnique;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * O motor de ponta a ponta (TDS 11, Domain §12) — cenários C1-C20 do Implementation Plan
 * M9, determinismo, horizonte vs. escopo (ADR-007) e FD-3.
 */
class MatchingEngineTest {

    private final MatchingEngine engine = new MatchingEngine();

    // ------------------------------------------------------------ Nível A

    @Test
    void c1ReconciledExactComOitoPredicadosNaEvidencia() {
        String key = "NSU-C1";
        FinancialRecord sale = aRecord().correlationKey(key).recordType(RecordType.SALE).grossAmount(50_000).build();
        FinancialRecord settlement = aRecord().correlationKey(key).recordType(RecordType.SETTLEMENT)
                .businessDate(sale.businessDate().plusDays(1)).netAmount(null).grossAmount(50_000).build();

        MatchingDecisions decisions = engine.evaluate(
                new RecordScope(List.of(sale)), new InMemoryCandidateHorizon(List.of(settlement)), context().build());

        assertThat(decisions.automaticMatches()).hasSize(1);
        MatchProposal proposal = decisions.automaticMatches().get(0);
        assertThat(proposal.ruleId()).isEqualTo(RuleACorrelationKey.RULE_ID);
        assertThat(proposal.outcome()).isEqualTo(AmountEvaluation.Outcome.RECONCILED_EXACT);
        assertThat(proposal.evidence().predicates()).hasSize(8);
        assertThat(proposal.evidence().predicates()).allMatch(PredicateResult::passed);
        assertThat(decisions.orphans()).isEmpty();
    }

    @Test
    void c2ReconciledWithFee() {
        String key = "NSU-C2";
        FinancialRecord sale = aRecord().correlationKey(key).recordType(RecordType.SALE).grossAmount(50_000).build();
        FinancialRecord settlement = aRecord().correlationKey(key).recordType(RecordType.SETTLEMENT)
                .businessDate(sale.businessDate().plusDays(1))
                .declaredFeeAmount(1_280L).netAmount(48_720L).build();

        MatchingDecisions decisions = engine.evaluate(
                new RecordScope(List.of(sale)), new InMemoryCandidateHorizon(List.of(settlement)), context().build());

        assertThat(decisions.automaticMatches()).hasSize(1);
        assertThat(decisions.automaticMatches().get(0).outcome()).isEqualTo(AmountEvaluation.Outcome.RECONCILED_WITH_FEE);
    }

    @Test
    void c3ReconciledWithinTolerance() {
        String key = "NSU-C3";
        FinancialRecord sale = aRecord().correlationKey(key).recordType(RecordType.SALE).grossAmount(50_000).build();
        FinancialRecord settlement = aRecord().correlationKey(key).recordType(RecordType.SETTLEMENT)
                .businessDate(sale.businessDate().plusDays(1))
                .declaredFeeAmount(1_280L).netAmount(48_719L).build(); // 1 a menos, tolerância é 2

        MatchingDecisions decisions = engine.evaluate(
                new RecordScope(List.of(sale)), new InMemoryCandidateHorizon(List.of(settlement)), context().build());

        assertThat(decisions.automaticMatches()).hasSize(1);
        assertThat(decisions.automaticMatches().get(0).outcome()).isEqualTo(AmountEvaluation.Outcome.RECONCILED_WITHIN_TOLERANCE);
    }

    @Test
    void c4PairedWithDivergenceAindaAssimEhAutoMatchNoNivelA() {
        // TDS 11.4: "Predicados ok, valor não fecha" -> AUTO_MATCH com PAIRED_WITH_DIVERGENCE.
        String key = "NSU-C4";
        FinancialRecord sale = aRecord().correlationKey(key).recordType(RecordType.SALE).grossAmount(50_000).build();
        FinancialRecord settlement = aRecord().correlationKey(key).recordType(RecordType.SETTLEMENT)
                .businessDate(sale.businessDate().plusDays(1))
                .declaredFeeAmount(1_280L).netAmount(40_000L).build(); // resíduo grande, fora da tolerância

        MatchingDecisions decisions = engine.evaluate(
                new RecordScope(List.of(sale)), new InMemoryCandidateHorizon(List.of(settlement)), context().build());

        assertThat(decisions.automaticMatches()).hasSize(1);
        assertThat(decisions.automaticMatches().get(0).outcome()).isEqualTo(AmountEvaluation.Outcome.PAIRED_WITH_DIVERGENCE);
    }

    @Test
    void chaveRepetidaDeUmDosLadosNaoProduzCorrespondencia() {
        // KEY_UNIQUE_BOTH_SIDES lido da flag (M7) — nunca recalculado por escopo.
        String key = "NSU-DUP";
        FinancialRecord sale = aRecord().correlationKey(key).recordType(RecordType.SALE).grossAmount(50_000).build();
        FinancialRecord settlement = aRecord().correlationKey(key).recordType(RecordType.SETTLEMENT)
                .businessDate(sale.businessDate().plusDays(1)).netAmount(null).grossAmount(50_000).build();
        var ctx = context().flagged(sale.id(), RecordIntegrityFlagType.DUPLICATE_CORRELATION_KEY).build();

        MatchingDecisions decisions = engine.evaluate(
                new RecordScope(List.of(sale)), new InMemoryCandidateHorizon(List.of(settlement)), ctx);

        assertThat(decisions.automaticMatches()).isEmpty();
    }

    @Test
    void ambosOsLadosDeUmParJaCasadoNaoReaparecemQuandoAmbosEstaoNoEscopo() {
        // Cenario realista de producao: o orquestrador carrega registros elegiveis dos DOIS
        // lados no mesmo escopo (nao sabe de antemao qual e "esquerdo"). O lado que a
        // varredura ja capturou como par do outro nao pode ser reprocessado como ancora nem
        // cair em orfaos/sugestoes/ambiguidades.
        String key = "NSU-BOTHSIDES";
        FinancialRecord sale = aRecord().correlationKey(key).recordType(RecordType.SALE).grossAmount(50_000).build();
        FinancialRecord settlement = aRecord().correlationKey(key).recordType(RecordType.SETTLEMENT)
                .businessDate(sale.businessDate().plusDays(1)).netAmount(null).grossAmount(50_000).build();

        MatchingDecisions decisions = engine.evaluate(
                new RecordScope(List.of(sale, settlement)),
                new InMemoryCandidateHorizon(List.of(sale, settlement)),
                context().build());

        assertThat(decisions.automaticMatches()).hasSize(1);
        assertThat(decisions.orphans()).isEmpty();
        assertThat(decisions.ambiguities()).isEmpty();
        assertThat(decisions.suggestions()).isEmpty();
    }

    @Test
    void umRegistroNuncaEhCandidatoDeSiMesmo() {
        // Um estorno é compatível com outro estorno (COMPATIBLE_TYPE), o que tornaria um
        // auto-casamento estruturalmente possível se o horizonte devolvesse o próprio âncora
        // como candidato — o motor precisa excluir isso, independente da implementação do
        // horizonte.
        FinancialRecord refund = aRecord().correlationKey("NSU-SELF").recordType(RecordType.REFUND)
                .direction(Direction.DEBIT).grossAmount(50_000).build();

        MatchingDecisions decisions = engine.evaluate(
                new RecordScope(List.of(refund)), new InMemoryCandidateHorizon(List.of(refund)), context().build());

        assertThat(decisions.automaticMatches()).isEmpty();
    }

    @Test
    void c11EstornoNuncaCasaComVenda() {
        FinancialRecord refund = aRecord().correlationKey("NSU-C11").recordType(RecordType.REFUND)
                .direction(Direction.DEBIT).grossAmount(50_000).build();
        FinancialRecord settlement = aRecord().correlationKey("NSU-C11").recordType(RecordType.SETTLEMENT)
                .direction(Direction.CREDIT).businessDate(refund.businessDate().plusDays(1)).grossAmount(50_000).build();

        MatchingDecisions decisions = engine.evaluate(
                new RecordScope(List.of(refund)), new InMemoryCandidateHorizon(List.of(settlement)), context().build());

        assertThat(decisions.automaticMatches()).isEmpty();
    }

    @Test
    void registroJaReivindicadoNuncaEhReutilizado() {
        String key = "NSU-CLAIMED";
        FinancialRecord sale = aRecord().correlationKey(key).recordType(RecordType.SALE).grossAmount(50_000).build();
        FinancialRecord settlement = aRecord().correlationKey(key).recordType(RecordType.SETTLEMENT)
                .businessDate(sale.businessDate().plusDays(1)).netAmount(null).grossAmount(50_000).build();
        var ctx = context().claimed(settlement.id()).build();

        MatchingDecisions decisions = engine.evaluate(
                new RecordScope(List.of(sale)), new InMemoryCandidateHorizon(List.of(settlement)), ctx);

        assertThat(decisions.automaticMatches()).isEmpty();
    }

    // ------------------------------------------------------------ Nível B

    @Test
    void c5NivelBComDocumentoNosDoisLados() {
        String document = "11144477735";
        FinancialRecord sale = aRecord().recordType(RecordType.SALE).grossAmount(50_000)
                .counterpartyDocument(document).paymentMethod("CREDIT_CARD").build();
        FinancialRecord settlement = aRecord().recordType(RecordType.SETTLEMENT)
                .businessDate(sale.businessDate().plusDays(1)).netAmount(null).grossAmount(50_000)
                .counterpartyDocument(document).paymentMethod("CREDIT_CARD").build();

        MatchingDecisions decisions = engine.evaluate(
                new RecordScope(List.of(sale)), new InMemoryCandidateHorizon(List.of(settlement)), context().build());

        assertThat(decisions.automaticMatches()).hasSize(1);
        assertThat(decisions.automaticMatches().get(0).ruleId()).isEqualTo(RuleBCompositeMutualUnique.RULE_ID);
        assertThat(decisions.ambiguities()).isEmpty();
    }

    @Test
    void c6TresCandidatosNenhumMatchAmbiguitySetComTres() {
        String document = "11144477735";
        FinancialRecord sale = aRecord().recordType(RecordType.SALE).grossAmount(50_000)
                .counterpartyDocument(document).paymentMethod("CREDIT_CARD").build();
        List<FinancialRecord> threeCandidates = List.of(
                aRecord().recordType(RecordType.SETTLEMENT).businessDate(sale.businessDate().plusDays(1))
                        .netAmount(null).grossAmount(50_000).counterpartyDocument(document).paymentMethod("CREDIT_CARD").build(),
                aRecord().recordType(RecordType.SETTLEMENT).businessDate(sale.businessDate().plusDays(2))
                        .netAmount(null).grossAmount(50_000).counterpartyDocument(document).paymentMethod("CREDIT_CARD").build(),
                aRecord().recordType(RecordType.SETTLEMENT).businessDate(sale.businessDate().plusDays(3))
                        .netAmount(null).grossAmount(50_000).counterpartyDocument(document).paymentMethod("CREDIT_CARD").build());

        MatchingDecisions decisions = engine.evaluate(
                new RecordScope(List.of(sale)), new InMemoryCandidateHorizon(threeCandidates), context().build());

        assertThat(decisions.automaticMatches()).isEmpty();
        assertThat(decisions.ambiguities()).hasSize(1);
        assertThat(decisions.ambiguities().get(0).candidates()).hasSize(3);
    }

    @Test
    void fd3ParSemDocumentoEmUmDosLadosNuncaChegaAoNivelB() {
        FinancialRecord sale = aRecord().recordType(RecordType.SALE).grossAmount(50_000)
                .counterpartyDocument(null).paymentMethod("CREDIT_CARD").build();
        FinancialRecord settlement = aRecord().recordType(RecordType.SETTLEMENT)
                .businessDate(sale.businessDate().plusDays(1)).netAmount(null).grossAmount(50_000)
                .counterpartyDocument("11144477735").paymentMethod("CREDIT_CARD").build();

        MatchingDecisions decisions = engine.evaluate(
                new RecordScope(List.of(sale)), new InMemoryCandidateHorizon(List.of(settlement)), context().build());

        assertThat(decisions.automaticMatches()).isEmpty();
        assertThat(decisions.ambiguities()).isEmpty();
        // Cai para o registro órfão (o Nível C pode sugerir, mas nunca concilia).
        assertThat(decisions.orphans()).extracting(OrphanClassification::record).contains(sale);
    }

    @Test
    void mutualUniquenessSoContaAposFiltroExatoDeValor() {
        // Um candidato que não fecha em valor não conta para ambiguidade — TDS 11.5.
        String document = "11144477735";
        FinancialRecord sale = aRecord().recordType(RecordType.SALE).grossAmount(50_000)
                .counterpartyDocument(document).paymentMethod("CREDIT_CARD").build();
        FinancialRecord closingCandidate = aRecord().recordType(RecordType.SETTLEMENT)
                .businessDate(sale.businessDate().plusDays(1)).netAmount(null).grossAmount(50_000)
                .counterpartyDocument(document).paymentMethod("CREDIT_CARD").build();
        FinancialRecord nonClosingCandidate = aRecord().recordType(RecordType.SETTLEMENT)
                .businessDate(sale.businessDate().plusDays(2)).declaredFeeAmount(1L).netAmount(1_000L) // não fecha
                .counterpartyDocument(document).paymentMethod("CREDIT_CARD").build();

        MatchingDecisions decisions = engine.evaluate(
                new RecordScope(List.of(sale)), new InMemoryCandidateHorizon(List.of(closingCandidate, nonClosingCandidate)),
                context().build());

        assertThat(decisions.automaticMatches()).hasSize(1);
        assertThat(decisions.ambiguities()).isEmpty();
    }

    // ------------------------------------------------------------ Nível C / órfãos

    @Test
    void c7OrfaoDentroDaJanelaEhPendingSettlement() {
        FinancialRecord sale = aRecord().recordType(RecordType.SALE).grossAmount(50_000)
                .businessDate(LocalDate.of(2026, 9, 19)).paymentMethod("CREDIT_CARD").build();
        var ctx = context().evaluationDate(LocalDate.of(2026, 9, 20)).build(); // +1 dia, dentro de 1-31

        MatchingDecisions decisions = engine.evaluate(
                new RecordScope(List.of(sale)), new InMemoryCandidateHorizon(List.of()), ctx);

        assertThat(decisions.orphans()).hasSize(1);
        assertThat(decisions.orphans().get(0).status()).isEqualTo(OrphanClassification.Status.PENDING_SETTLEMENT);
    }

    @Test
    void c8OrfaoComJanelaVencidaEhCandidatoAAusencia() {
        FinancialRecord sale = aRecord().recordType(RecordType.SALE).grossAmount(50_000)
                .businessDate(LocalDate.of(2026, 1, 1)).paymentMethod("CREDIT_CARD").build();
        var ctx = context().evaluationDate(LocalDate.of(2026, 9, 20)).build(); // muito além de 31 dias

        MatchingDecisions decisions = engine.evaluate(
                new RecordScope(List.of(sale)), new InMemoryCandidateHorizon(List.of()), ctx);

        assertThat(decisions.orphans()).hasSize(1);
        assertThat(decisions.orphans().get(0).status()).isEqualTo(OrphanClassification.Status.ABSENCE_CANDIDATE);
    }

    @Test
    void nivelCNuncaProduzMatchProposalApenasSugestao() {
        FinancialRecord sale = aRecord().recordType(RecordType.SALE).grossAmount(50_000)
                .counterpartyDocument(null).paymentMethod(null).build();
        FinancialRecord looseCandidate = aRecord().recordType(RecordType.SETTLEMENT)
                .businessDate(sale.businessDate().plusDays(1)).netAmount(null).grossAmount(50_000)
                .counterpartyDocument(null).paymentMethod(null).build();

        MatchingDecisions decisions = engine.evaluate(
                new RecordScope(List.of(sale)), new InMemoryCandidateHorizon(List.of(looseCandidate)), context().build());

        assertThat(decisions.automaticMatches()).isEmpty();
        assertThat(decisions.suggestions()).hasSize(1);
        assertThat(decisions.suggestions().get(0).candidates()).containsExactly(looseCandidate);
    }

    // ------------------------------------------------------------ Determinismo e horizonte

    @Test
    void mesmaEntradaAvaliadaDuasVezesProduzDecisoesIdenticas() {
        String document = "11144477735";
        FinancialRecord sale = aRecord().recordType(RecordType.SALE).grossAmount(50_000)
                .counterpartyDocument(document).paymentMethod("CREDIT_CARD").build();
        List<FinancialRecord> candidates = List.of(
                aRecord().recordType(RecordType.SETTLEMENT).businessDate(sale.businessDate().plusDays(1))
                        .netAmount(null).grossAmount(50_000).counterpartyDocument(document).paymentMethod("CREDIT_CARD").build(),
                aRecord().recordType(RecordType.SETTLEMENT).businessDate(sale.businessDate().plusDays(2))
                        .netAmount(null).grossAmount(50_000).counterpartyDocument(document).paymentMethod("CREDIT_CARD").build());
        EvaluationContext ctx = context().build();

        MatchingDecisions first = engine.evaluate(new RecordScope(List.of(sale)), new InMemoryCandidateHorizon(candidates), ctx);
        MatchingDecisions second = engine.evaluate(new RecordScope(List.of(sale)), new InMemoryCandidateHorizon(candidates), ctx);

        assertThat(first.ambiguities()).hasSize(1);
        assertThat(second.ambiguities()).hasSize(1);
        assertThat(first.ambiguities().get(0).candidates()).isEqualTo(second.ambiguities().get(0).candidates());
    }

    @Test
    void embaralharAListaDeEntradaNaoMudaOResultado() {
        String keyA = "NSU-SHUFFLE-A";
        String keyB = "NSU-SHUFFLE-B";
        FinancialRecord saleA = aRecord().correlationKey(keyA).recordType(RecordType.SALE).grossAmount(10_000).build();
        FinancialRecord saleB = aRecord().correlationKey(keyB).recordType(RecordType.SALE).grossAmount(20_000).build();
        FinancialRecord settlementA = aRecord().correlationKey(keyA).recordType(RecordType.SETTLEMENT)
                .businessDate(saleA.businessDate().plusDays(1)).netAmount(null).grossAmount(10_000).build();
        FinancialRecord settlementB = aRecord().correlationKey(keyB).recordType(RecordType.SETTLEMENT)
                .businessDate(saleB.businessDate().plusDays(1)).netAmount(null).grossAmount(20_000).build();
        EvaluationContext ctx = context().build();
        InMemoryCandidateHorizon horizon = new InMemoryCandidateHorizon(List.of(settlementA, settlementB));

        List<FinancialRecord> order1 = List.of(saleA, saleB);
        List<FinancialRecord> order2 = new java.util.ArrayList<>(List.of(saleB, saleA));
        Collections.shuffle(order2, new java.util.Random(42));

        MatchingDecisions decisions1 = engine.evaluate(new RecordScope(order1), horizon, ctx);
        MatchingDecisions decisions2 = engine.evaluate(new RecordScope(order2), horizon, ctx);

        assertThat(decisions1.automaticMatches()).hasSize(2);
        assertThat(decisions2.automaticMatches()).hasSize(2);
        assertThat(decisions1.automaticMatches().stream().map(p -> p.left().id()).sorted().toList())
                .isEqualTo(decisions2.automaticMatches().stream().map(p -> p.left().id()).sorted().toList());
    }

    @Test
    void escopoEstreitoOuAmploVeemOMesmoHorizonteEProduzemAmbiguitySetIgual() {
        // A2 / ADR-007: o horizonte nunca vem do escopo. Um escopo com 1 candidato e um
        // escopo com 3 devem produzir exatamente o mesmo resultado para o registro comum,
        // porque o InMemoryCandidateHorizon devolve o mesmo pool independente do escopo.
        String document = "11144477735";
        FinancialRecord sale = aRecord().recordType(RecordType.SALE).grossAmount(50_000)
                .counterpartyDocument(document).paymentMethod("CREDIT_CARD").build();
        List<FinancialRecord> threeCandidates = List.of(
                aRecord().recordType(RecordType.SETTLEMENT).businessDate(sale.businessDate().plusDays(1))
                        .netAmount(null).grossAmount(50_000).counterpartyDocument(document).paymentMethod("CREDIT_CARD").build(),
                aRecord().recordType(RecordType.SETTLEMENT).businessDate(sale.businessDate().plusDays(2))
                        .netAmount(null).grossAmount(50_000).counterpartyDocument(document).paymentMethod("CREDIT_CARD").build(),
                aRecord().recordType(RecordType.SETTLEMENT).businessDate(sale.businessDate().plusDays(3))
                        .netAmount(null).grossAmount(50_000).counterpartyDocument(document).paymentMethod("CREDIT_CARD").build());
        InMemoryCandidateHorizon wideHorizon = new InMemoryCandidateHorizon(threeCandidates);
        EvaluationContext ctx = context().build();

        // Escopo "estreito": só avalia o registro isoladamente. Escopo "amplo": o mesmo
        // registro, junto de outro registro qualquer não relacionado — o tamanho do escopo
        // não pode influenciar o horizonte deste registro.
        FinancialRecord unrelated = aRecord().recordType(RecordType.SALE).grossAmount(1).build();

        MatchingDecisions narrowScope = engine.evaluate(new RecordScope(List.of(sale)), wideHorizon, ctx);
        MatchingDecisions wideScope = engine.evaluate(new RecordScope(List.of(sale, unrelated)), wideHorizon, ctx);

        assertThat(narrowScope.ambiguities()).hasSize(1);
        assertThat(wideScope.ambiguities()).hasSize(1);
        assertThat(narrowScope.ambiguities().get(0).candidates()).isEqualTo(wideScope.ambiguities().get(0).candidates());
    }

    @Test
    void reexecutarNaoDeveMudarDecisaoLogicaComMesmosParametros() {
        String key = "NSU-REEXEC";
        FinancialRecord sale = aRecord().correlationKey(key).recordType(RecordType.SALE).grossAmount(50_000).build();
        FinancialRecord settlement = aRecord().correlationKey(key).recordType(RecordType.SETTLEMENT)
                .businessDate(sale.businessDate().plusDays(1)).netAmount(null).grossAmount(50_000).build();
        EvaluationContext ctx = context().build();
        InMemoryCandidateHorizon horizon = new InMemoryCandidateHorizon(List.of(settlement));

        MatchingDecisions first = engine.evaluate(new RecordScope(List.of(sale)), horizon, ctx);
        MatchingDecisions second = engine.evaluate(new RecordScope(List.of(sale)), horizon, ctx);

        assertThat(first.automaticMatches()).hasSize(1);
        assertThat(second.automaticMatches()).hasSize(1);
        assertThat(first.automaticMatches().get(0).outcome()).isEqualTo(second.automaticMatches().get(0).outcome());
        assertThat(first.automaticMatches().get(0).left().id()).isEqualTo(second.automaticMatches().get(0).left().id());
        assertThat(first.automaticMatches().get(0).right().id()).isEqualTo(second.automaticMatches().get(0).right().id());
    }

    @Test
    void configuracaoAlteradaDepoisNaoMudaAvaliacaoJaCongelada() {
        // Uma execução usa o snapshot A; outra, mais tarde, usa o snapshot B — a primeira
        // nunca é recalculada com B, porque o contexto é imutável e não é lido de novo.
        String key = "NSU-FROZEN";
        FinancialRecord sale = aRecord().correlationKey(key).recordType(RecordType.SALE).grossAmount(50_000).build();
        FinancialRecord settlement = aRecord().correlationKey(key).recordType(RecordType.SETTLEMENT)
                .businessDate(sale.businessDate().plusDays(1))
                .declaredFeeAmount(1_280L).netAmount(48_719L).build(); // resíduo -1, dentro da tolerância(2)

        EvaluationContext contextA = context()
                .config(RunConfigSnapshotFixture.config().toleranceAbsoluteMinor(2).build())
                .build();
        MatchingDecisions decisionsWithA = engine.evaluate(
                new RecordScope(List.of(sale)), new InMemoryCandidateHorizon(List.of(settlement)), contextA);
        assertThat(decisionsWithA.automaticMatches().get(0).outcome())
                .isEqualTo(AmountEvaluation.Outcome.RECONCILED_WITHIN_TOLERANCE);

        // "Configuração B" mudou a tolerância para 0 — mas isto é um contexto NOVO, não uma
        // mutação do primeiro. A execução histórica (contextA) já aconteceu e seu resultado
        // não é recalculado.
        EvaluationContext contextB = context()
                .config(RunConfigSnapshotFixture.config().toleranceAbsoluteMinor(0).build())
                .build();
        MatchingDecisions decisionsWithB = engine.evaluate(
                new RecordScope(List.of(sale)), new InMemoryCandidateHorizon(List.of(settlement)), contextB);
        assertThat(decisionsWithB.automaticMatches().get(0).outcome())
                .isEqualTo(AmountEvaluation.Outcome.PAIRED_WITH_DIVERGENCE);

        // A reavaliação com o snapshot original A continua idêntica — nada "vazou" de B.
        MatchingDecisions decisionsWithAAgain = engine.evaluate(
                new RecordScope(List.of(sale)), new InMemoryCandidateHorizon(List.of(settlement)), contextA);
        assertThat(decisionsWithAAgain.automaticMatches().get(0).outcome())
                .isEqualTo(AmountEvaluation.Outcome.RECONCILED_WITHIN_TOLERANCE);
    }

    @Test
    void identificadoresTecnicosNaoImportamParaEquivalenciaLogica() {
        // Domain AC-REC-13: reexecução produz o mesmo conjunto LÓGICO — novos ids técnicos
        // são esperados e não violam o critério.
        String key = "NSU-LOGICAL";
        FinancialRecord sale = aRecord().correlationKey(key).recordType(RecordType.SALE).grossAmount(50_000).build();
        FinancialRecord settlement = aRecord().correlationKey(key).recordType(RecordType.SETTLEMENT)
                .businessDate(sale.businessDate().plusDays(1)).netAmount(null).grossAmount(50_000).build();

        MatchingDecisions decisions = engine.evaluate(
                new RecordScope(List.of(sale)), new InMemoryCandidateHorizon(List.of(settlement)), context().build());

        // Cada chamada ao fixture gera um id novo (Uuid7) — mesmo assim, o resultado lógico
        // (outcome, ruleId, pares corretos) é sempre o mesmo em execuções repetidas.
        assertThat(decisions.automaticMatches()).hasSize(1);
        MatchProposal proposal = decisions.automaticMatches().get(0);
        assertThat(proposal.left().id()).isNotEqualTo(proposal.right().id());
        assertThat(proposal.ruleId()).isEqualTo(RuleACorrelationKey.RULE_ID);
    }
}
