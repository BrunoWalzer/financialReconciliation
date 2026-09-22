package dev.fincore.matching.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.fincore.audit.application.AuditEventRequest;
import dev.fincore.audit.application.AuditService;
import dev.fincore.audit.domain.ActorRef;
import dev.fincore.evidence.domain.FinancialRecord;
import dev.fincore.matching.domain.Match;
import dev.fincore.matching.domain.MatchClaim;
import dev.fincore.matching.domain.MatchEvidence;
import dev.fincore.matching.domain.MatchParticipant;
import dev.fincore.matching.domain.MatchProposal;
import dev.fincore.matching.infrastructure.MatchClaimRepository;
import dev.fincore.matching.infrastructure.MatchParticipantRepository;
import dev.fincore.matching.infrastructure.MatchRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persiste um {@link MatchProposal} atomicamente: {@code match} + {@code match_participant}
 * (dois PRINCIPAL, um por lado, mais COMPONENT quando houver) + {@code match_claim} (um por
 * registro envolvido) + auditoria — tudo na mesma transação (TDS P6, 16.1).
 *
 * <p>A exclusividade real (I-5) vem da {@code PRIMARY KEY} de {@code match_claim}, nunca de
 * um {@code SELECT} antes do {@code INSERT} (Implementation Plan M10: "não mockar a
 * constraint"). Cada {@code save} de claim é seguido de um {@link EntityManager#flush()}
 * explícito — sem isso, o Hibernate adiaria o {@code INSERT} até o commit, e a violação de
 * chave só apareceria tarde demais para decidir, aqui, qual proposta venceu a corrida.
 */
@Service
class ClaimAndPersistMatchUseCase {

    private final MatchRepository matchRepository;
    private final MatchParticipantRepository participantRepository;
    private final MatchClaimRepository claimRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final EntityManager entityManager;

    ClaimAndPersistMatchUseCase(
            MatchRepository matchRepository,
            MatchParticipantRepository participantRepository,
            MatchClaimRepository claimRepository,
            AuditService auditService,
            ObjectMapper objectMapper,
            EntityManager entityManager) {
        this.matchRepository = matchRepository;
        this.participantRepository = participantRepository;
        this.claimRepository = claimRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.entityManager = entityManager;
    }

    /**
     * @throws MatchClaimConflictException quando outra transação já reivindicou algum dos
     *     registros do par — tradução limpa da violação de PK de {@code match_claim} (I-5).
     *     A transação inteira é revertida: nem o {@code match} nem seus participantes
     *     sobrevivem quando a proposta perde a corrida.
     */
    @Transactional
    Match execute(MatchProposal proposal, Instant now, ActorRef actor) {
        String evidenceJson = writeEvidence(proposal.evidence());
        Match match = Match.automatic(
                proposal.ruleId(), proposal.ruleVersion(), proposal.evidence().amountEvaluation(), evidenceJson, now);
        Match saved = matchRepository.save(match);

        saveParticipant(saved.id(), proposal.left().id(), MatchParticipant.Side.LEFT, MatchParticipant.Role.PRINCIPAL);
        saveParticipant(saved.id(), proposal.right().id(), MatchParticipant.Side.RIGHT, MatchParticipant.Role.PRINCIPAL);
        // 1:N (subset-sum) está fora do escopo deste milestone (Implementation Plan M9) —
        // proposal.components() está sempre vazia na prática hoje; o laço abaixo só evita
        // perder dado silenciosamente se um nível futuro passar a preenchê-la.
        for (FinancialRecord component : proposal.components()) {
            saveParticipant(saved.id(), component.id(), MatchParticipant.Side.LEFT, MatchParticipant.Role.COMPONENT);
        }
        entityManager.flush();

        claimRecord(proposal.left().id(), saved.id(), now);
        claimRecord(proposal.right().id(), saved.id(), now);
        for (FinancialRecord component : proposal.components()) {
            claimRecord(component.id(), saved.id(), now);
        }

        auditService.record(AuditEventRequest.of(actor, "MATCH_CREATED", "Match", saved.id()));
        return saved;
    }

    private void saveParticipant(UUID matchId, UUID recordId, MatchParticipant.Side side, MatchParticipant.Role role) {
        participantRepository.save(new MatchParticipant(matchId, recordId, side, role));
    }

    private void claimRecord(UUID financialRecordId, UUID matchId, Instant now) {
        try {
            claimRepository.save(new MatchClaim(financialRecordId, matchId, now));
            entityManager.flush();
        } catch (DataIntegrityViolationException | ConstraintViolationException e) {
            // save() por si só pode lançar o tipo traduzido do Spring (proxy de repositório
            // Spring Data, DataIntegrityViolationException); o flush() explícito logo depois,
            // por ser EntityManager cru, não passa pela tradução de exceção do Spring e
            // propaga a exceção nativa do Hibernate (ConstraintViolationException). A única
            // coisa que pode falhar exatamente aqui é a PK de match_claim (I-5):
            // financial_record_id e match_id já foram validados antes deste ponto na mesma
            // transação.
            throw new MatchClaimConflictException(financialRecordId, e);
        }
    }

    private String writeEvidence(MatchEvidence evidence) {
        try {
            return objectMapper.writeValueAsString(evidence);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("evidência de match não serializável", e);
        }
    }
}
