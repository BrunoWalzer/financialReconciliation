-- V7 — Persistência de correspondência. Milestone M9 (TDS 7.6, 11; Implementation Plan M9
-- + M10 — ver relatório do M9, seção Decisões, sobre a fusão de milestones desta sessão).
--
-- match.reconciliation_run_id não tem FK: reconciliation_run só nasce no M11 (FD-9). A FK é
-- adicionada quando aquela tabela existir. Mesmo raciocínio de import_batch_id em V4.
--
-- match_claim é a invariante de cardinalidade do sistema inteiro (I-5): financial_record_id
-- é a PRIMARY KEY, não um índice único — nenhum registro pode aparecer em dois matches
-- ativos, em qualquer papel (principal ou componente). A exclusividade é garantida pelo
-- banco, nunca por um SELECT antes do INSERT na aplicação.

CREATE TABLE match (
    id                        UUID        NOT NULL,
    reconciliation_run_id     UUID,
    origin                    TEXT        NOT NULL,
    rule_id                   TEXT,
    rule_version              INTEGER,
    outcome                   TEXT        NOT NULL,
    status                    TEXT        NOT NULL DEFAULT 'ACTIVE',
    currency                  CHAR(3)     NOT NULL,
    gross_expected_minor      BIGINT      NOT NULL,
    fee_applied_minor         BIGINT      NOT NULL,
    net_expected_minor        BIGINT      NOT NULL,
    observed_minor            BIGINT      NOT NULL,
    fee_source                TEXT        NOT NULL,
    residual_minor            BIGINT      NOT NULL,
    tolerance_limit_minor     BIGINT      NOT NULL,
    tolerance_absorbed_minor  BIGINT      NOT NULL,
    evidence                  JSONB,
    justification             TEXT,
    created_by                UUID,
    created_at                TIMESTAMPTZ NOT NULL,
    superseded_at             TIMESTAMPTZ,
    superseded_by_id          UUID,
    recomposition_count       INTEGER     NOT NULL DEFAULT 0,
    version                   BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT pk_match PRIMARY KEY (id),
    CONSTRAINT fk_match_superseded_by FOREIGN KEY (superseded_by_id) REFERENCES match (id),

    CONSTRAINT ck_match_origin CHECK (origin IN ('AUTOMATIC', 'MANUAL')),
    CONSTRAINT ck_match_outcome
        CHECK (outcome IN ('RECONCILED_EXACT', 'RECONCILED_WITH_FEE', 'RECONCILED_WITHIN_TOLERANCE', 'PAIRED_WITH_DIVERGENCE')),
    CONSTRAINT ck_match_status CHECK (status IN ('ACTIVE', 'SUPERSEDED')),
    CONSTRAINT ck_match_currency CHECK (currency IN ('BRL')),
    CONSTRAINT ck_match_fee_source CHECK (fee_source IN ('DECLARED', 'COMPONENT_RECORD', 'RULE', 'NONE')),

    -- I-11: automático sem regra/evidência não é representável.
    CONSTRAINT ck_match_automatic_requires_rule_and_evidence
        CHECK (origin <> 'AUTOMATIC' OR (rule_id IS NOT NULL AND evidence IS NOT NULL)),
    -- Manual chega só no M14 — a constraint já protege a forma da tabela desde já.
    CONSTRAINT ck_match_manual_requires_justification
        CHECK (origin <> 'MANUAL' OR (created_by IS NOT NULL AND length(trim(justification)) >= 10)),

    CONSTRAINT ck_match_tolerance_absorbed_non_negative CHECK (tolerance_absorbed_minor >= 0),
    CONSTRAINT ck_match_superseded_has_timestamp
        CHECK (status <> 'SUPERSEDED' OR superseded_at IS NOT NULL)
);

COMMENT ON TABLE match IS
    'Afirmação de que registros representam o mesmo evento financeiro (Domain §5.2, TDS 7.6). residual_minor e tolerance_absorbed_minor são NOT NULL mesmo quando zero — as duas colunas que impedem valor de evaporar (TDS 8.3).';

CREATE INDEX idx_match_created_at ON match (created_at);
CREATE INDEX idx_match_status ON match (status) WHERE status = 'ACTIVE';

CREATE TABLE match_participant (
    match_id            UUID    NOT NULL,
    financial_record_id UUID    NOT NULL,
    side                TEXT    NOT NULL,
    role                TEXT    NOT NULL,

    CONSTRAINT pk_match_participant PRIMARY KEY (match_id, financial_record_id),
    CONSTRAINT fk_match_participant_match FOREIGN KEY (match_id) REFERENCES match (id),
    CONSTRAINT fk_match_participant_record FOREIGN KEY (financial_record_id) REFERENCES financial_record (id),

    CONSTRAINT ck_match_participant_side CHECK (side IN ('LEFT', 'RIGHT')),
    CONSTRAINT ck_match_participant_role CHECK (role IN ('PRINCIPAL', 'COMPONENT'))
);

-- I-14 (FD-5): no máximo um PRINCIPAL por lado. "Pelo menos um por lado" (exatamente dois
-- principais no total) não é expressável em CHECK de linha — validado na aplicação, com
-- teste de integração que prova a rejeição (Implementation Plan FD-5).
CREATE UNIQUE INDEX uq_match_participant_principal_per_side
    ON match_participant (match_id, side)
    WHERE role = 'PRINCIPAL';

COMMENT ON TABLE match_participant IS
    'Quem compõe um match (Domain §5.2). Exatamente dois PRINCIPAL (um por lado) e zero ou mais COMPONENT — a cardinalidade "1:1 dos principais" do domínio (TDS 7.6, FD-5).';

CREATE TABLE match_claim (
    financial_record_id UUID        NOT NULL,
    match_id             UUID        NOT NULL,
    claimed_at            TIMESTAMPTZ NOT NULL,

    -- I-5: a invariante de cardinalidade do sistema inteiro. financial_record_id é a PK, não
    -- um índice único sobre outra PK — nenhum registro participa de dois matches ativos, em
    -- qualquer papel. Duas transações concorrentes tentando reivindicar o mesmo registro
    -- colidem aqui, não num SELECT antes do INSERT (TDS 7.6, Implementation Plan M10).
    CONSTRAINT pk_match_claim PRIMARY KEY (financial_record_id),
    CONSTRAINT fk_match_claim_record FOREIGN KEY (financial_record_id) REFERENCES financial_record (id),
    CONSTRAINT fk_match_claim_match FOREIGN KEY (match_id) REFERENCES match (id)
);

COMMENT ON TABLE match_claim IS
    'A garantia de exclusividade do sistema inteiro (I-5, TDS 7.6): nenhum financial_record aparece em dois matches ativos, em qualquer papel — principal ou componente. A PK é a defesa real contra concorrência, não uma checagem em Java.';

CREATE INDEX idx_match_claim_match ON match_claim (match_id);

CREATE TABLE match_rejection (
    id           UUID        NOT NULL,
    record_a_id  UUID        NOT NULL,
    record_b_id  UUID        NOT NULL,
    rejected_by  UUID        NOT NULL,
    rejected_at  TIMESTAMPTZ NOT NULL,
    reason       TEXT        NOT NULL,
    divergence_id UUID,

    CONSTRAINT pk_match_rejection PRIMARY KEY (id),
    CONSTRAINT fk_match_rejection_record_a FOREIGN KEY (record_a_id) REFERENCES financial_record (id),
    CONSTRAINT fk_match_rejection_record_b FOREIGN KEY (record_b_id) REFERENCES financial_record (id),

    -- I-10: ordem canônica força um par a existir uma única vez, independente de qual lado
    -- foi informado primeiro.
    CONSTRAINT ck_match_rejection_canonical_order CHECK (record_a_id < record_b_id),
    CONSTRAINT uq_match_rejection_pair UNIQUE (record_a_id, record_b_id)
);

COMMENT ON TABLE match_rejection IS
    'Par humano recusado como correspondência (I-10, TDS 7.6) — usado pelo predicado NOT_PREVIOUSLY_REJECTED (M9). Sem escrita nenhuma até o M14 (criação por API); a tabela nasce agora porque o predicado já a lê.';
