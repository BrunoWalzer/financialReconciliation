-- V1 — Fundação de auditoria. Milestone M1 (TDS 7.2, 22.1; Implementation Plan M1).
--
-- audit_event é o primeiro registro imutável do FINCORE. Nasce sem FK para app_user
-- (FD-7): auditoria nunca pode ser bloqueada nem invalidada por integridade referencial
-- com a tabela de usuários, e é essa ausência de FK que permite este módulo existir antes
-- de identity (M2) — quebrando a circularidade que existiria se auditoria dependesse de
-- quem ainda não foi criado. actor_label denormaliza o rótulo do ator (e-mail, ou
-- "SYSTEM") no momento do evento, para que o registro continue legível mesmo que o
-- usuário mude de e-mail depois (FD-9: a FK nunca é adicionada — a tabela FD-9 do
-- Implementation Plan marca esta coluna como "nunca").
--
-- id e occurred_at são atribuídos pela aplicação, não pelo banco: o PostgreSQL 16 não
-- gera UUID v7 nativamente (ver V0__extensions.sql), e occurred_at registra o instante em
-- que a aplicação decidiu o fato, não o instante em que a linha chegou ao disco.

CREATE TABLE audit_event (
    id              UUID        NOT NULL,
    occurred_at     TIMESTAMPTZ NOT NULL,
    actor_type      TEXT        NOT NULL,
    actor_user_id   UUID,
    actor_label     TEXT        NOT NULL,
    actor_run_id    UUID,
    action          TEXT        NOT NULL,
    entity_type     TEXT,
    entity_id       UUID,
    before_state    JSONB,
    after_state     JSONB,
    justification   TEXT,
    correlation_id  TEXT,
    ip_address      INET,

    CONSTRAINT pk_audit_event PRIMARY KEY (id),

    -- Só os dois valores previstos pelo domínio (TDS 7.2).
    CONSTRAINT ck_audit_event_actor_type
        CHECK (actor_type IN ('USER', 'SYSTEM')),

    -- Um ator humano é sempre identificável; o sistema não precisa ser.
    CONSTRAINT ck_audit_event_actor_user_id_required
        CHECK (actor_type <> 'USER' OR actor_user_id IS NOT NULL)
);

COMMENT ON TABLE audit_event IS
    'Registro imutável de uma ação relevante (Domain glossário). actor_user_id não tem FK para app_user — decisão deliberada, ver FD-7.';

-- Consulta por entidade afetada: "o que aconteceu com este registro", mais recente primeiro.
CREATE INDEX idx_audit_event_entity ON audit_event (entity_type, entity_id, occurred_at DESC);

-- Consulta por ator: "o que este usuário fez", mais recente primeiro.
CREATE INDEX idx_audit_event_actor_user ON audit_event (actor_user_id, occurred_at DESC);

-- Linha do tempo geral.
CREATE INDEX idx_audit_event_occurred_at ON audit_event (occurred_at DESC);

-- Consulta por tipo de ação.
CREATE INDEX idx_audit_event_action ON audit_event (action, occurred_at DESC);

-- Imutabilidade (I-2). A função é genérica de propósito — não para "preparar" nada além
-- do já decidido: o Implementation Plan §7 já a reaplica em financial_record (V4),
-- rejected_record (V5) e divergence_resolution (V10) nos milestones em que essas tabelas
-- nascerem. TG_TABLE_NAME faz a mensagem apontar a tabela certa sem duplicar a função.
CREATE FUNCTION fincore_reject_mutation() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'fincore: % em % não é permitido — % é imutável', TG_OP, TG_TABLE_NAME, TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_event_reject_mutation
    BEFORE UPDATE OR DELETE ON audit_event
    FOR EACH ROW
    EXECUTE FUNCTION fincore_reject_mutation();
