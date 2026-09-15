-- V3 — Configuração e fontes. Milestone M3 (TDS 7.3; Implementation Plan M3).
--
-- Seis tabelas. "Regra de negócio parametrizada" (TDS 30.1): mora em tabela, é auditada
-- via audit_event (M1) — não há coluna de auditoria própria aqui além do que a TDS 7.3
-- lista explicitamente para tolerance_config — e é capturada em config_snapshot quando
-- o M11 existir. Nenhuma tabela financeira nasce aqui.
--
-- version: a TDS 7.3 só lista a coluna explicitamente para tolerance_config e fee_rule,
-- mas o Implementation Plan §7 (tabela de ordem de migrations) diz "version em todas" e
-- exige, com teste obrigatório, "If-Match com versão velha → 409" para os quatro
-- conceitos com endpoint de mutação (tolerâncias, regras de taxa, janelas, cobertura).
-- source e source_pair não têm endpoint de mutação no M3 (só seed e leitura) — sem eles,
-- version ficaria sem nenhum uso. Resolução: version nos quatro mutáveis, não nos dois
-- somente-seed. Ver relatório do M3, seção Desvios, para a discrepância completa.

CREATE TABLE source (
    id                   UUID    NOT NULL,
    code                 TEXT    NOT NULL,
    name                 TEXT    NOT NULL,
    timezone             TEXT    NOT NULL,
    decimal_separator    TEXT    NOT NULL,
    thousands_separator  TEXT    NOT NULL,
    date_formats         TEXT[]  NOT NULL,
    rounding_mode        TEXT    NOT NULL,
    active               BOOLEAN NOT NULL DEFAULT TRUE,

    CONSTRAINT pk_source PRIMARY KEY (id),
    CONSTRAINT uq_source_code UNIQUE (code),
    CONSTRAINT ck_source_rounding_mode CHECK (rounding_mode IN ('HALF_UP', 'HALF_EVEN', 'DOWN', 'UP'))
);

COMMENT ON TABLE source IS
    'Origem lógica de registros financeiros futuros (Domain §5). Não é evidência — nenhum valor financeiro mora aqui.';

CREATE TABLE source_pair (
    id              UUID NOT NULL,
    left_source_id  UUID NOT NULL,
    right_source_id UUID NOT NULL,
    code            TEXT NOT NULL,

    CONSTRAINT pk_source_pair PRIMARY KEY (id),
    CONSTRAINT uq_source_pair_code UNIQUE (code),
    CONSTRAINT fk_source_pair_left FOREIGN KEY (left_source_id) REFERENCES source (id),
    CONSTRAINT fk_source_pair_right FOREIGN KEY (right_source_id) REFERENCES source (id),
    CONSTRAINT uq_source_pair_left_right UNIQUE (left_source_id, right_source_id),
    CONSTRAINT ck_source_pair_distinct_sides CHECK (left_source_id <> right_source_id)
);

CREATE TABLE tolerance_config (
    id                             UUID        NOT NULL,
    source_pair_id                 UUID        NOT NULL,
    absolute_amount_minor          BIGINT      NOT NULL,
    currency                       TEXT        NOT NULL,
    aggregate_alert_threshold_minor BIGINT,
    updated_by                     UUID,
    updated_at                     TIMESTAMPTZ NOT NULL,
    version                        BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT pk_tolerance_config PRIMARY KEY (id),
    CONSTRAINT uq_tolerance_config_source_pair UNIQUE (source_pair_id),
    CONSTRAINT fk_tolerance_config_source_pair FOREIGN KEY (source_pair_id) REFERENCES source_pair (id),
    -- updated_by não tem FK para app_user: configuration não depende de identity
    -- (TDS 4.2 não lista essa aresta) — o mesmo raciocínio de FD-7 aplicado aqui.
    CONSTRAINT ck_tolerance_config_amount_non_negative CHECK (absolute_amount_minor >= 0)
);

COMMENT ON TABLE tolerance_config IS
    'Regra de negócio parametrizada (TDS 30.1): altera se uma conciliação fecha. Nunca encerra divergência aberta (FD-6).';

CREATE TABLE fee_rule (
    id                  UUID    NOT NULL,
    source_id           UUID    NOT NULL,
    payment_method      TEXT,
    percentage_bp       INTEGER NOT NULL,
    fixed_amount_minor  BIGINT  NOT NULL DEFAULT 0,
    rounding_mode       TEXT    NOT NULL,
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    version             BIGINT  NOT NULL DEFAULT 0,

    CONSTRAINT pk_fee_rule PRIMARY KEY (id),
    CONSTRAINT fk_fee_rule_source FOREIGN KEY (source_id) REFERENCES source (id),
    CONSTRAINT ck_fee_rule_percentage_bp_range CHECK (percentage_bp BETWEEN 0 AND 10000),
    CONSTRAINT ck_fee_rule_rounding_mode CHECK (rounding_mode IN ('HALF_UP', 'HALF_EVEN', 'DOWN', 'UP'))
);

-- UNIQUE(source_id, payment_method) WHERE active, do jeito que a TDS 7.3 escreve, não
-- funciona sozinho no Postgres: um índice único trata cada NULL como distinto de
-- qualquer outro NULL, e payment_method NULL é justamente "qualquer meio" (TDS 7.3) — o
-- caso que mais precisa ser único. COALESCE(payment_method, '') torna os NULLs iguais
-- entre si para fins de unicidade, sem mudar o significado de "NULL = qualquer meio".
CREATE UNIQUE INDEX uq_fee_rule_active_source_payment_method
    ON fee_rule (source_id, COALESCE(payment_method, ''))
    WHERE active;

COMMENT ON TABLE fee_rule IS
    'Configuração de taxa esperada por fonte e meio de pagamento (Domain §14). Percentual em basis points inteiros — 250 = 2,5%.';

CREATE TABLE settlement_window (
    id              UUID    NOT NULL,
    source_pair_id  UUID    NOT NULL,
    payment_method  TEXT,
    min_days        INTEGER NOT NULL,
    max_days        INTEGER NOT NULL,
    version         BIGINT  NOT NULL DEFAULT 0,

    CONSTRAINT pk_settlement_window PRIMARY KEY (id),
    CONSTRAINT fk_settlement_window_source_pair FOREIGN KEY (source_pair_id) REFERENCES source_pair (id),
    CONSTRAINT ck_settlement_window_days CHECK (min_days >= 0 AND max_days >= min_days)
);

-- Mesmo problema de NULL do fee_rule acima: payment_method NULL é a janela padrão
-- ("qualquer meio não listado explicitamente") e precisa ser única por source_pair.
CREATE UNIQUE INDEX uq_settlement_window_pair_payment_method
    ON settlement_window (source_pair_id, COALESCE(payment_method, ''));

COMMENT ON TABLE settlement_window IS
    'Prazo esperado de liquidação por par de fontes e meio de pagamento (Domain D2: por meio de pagamento, não por fonte).';

CREATE TABLE coverage_expectation (
    id         UUID    NOT NULL,
    source_id  UUID    NOT NULL,
    schedule   TEXT    NOT NULL,
    grace_days INTEGER NOT NULL,
    active     BOOLEAN NOT NULL DEFAULT TRUE,
    version    BIGINT  NOT NULL DEFAULT 0,

    CONSTRAINT pk_coverage_expectation PRIMARY KEY (id),
    CONSTRAINT uq_coverage_expectation_source UNIQUE (source_id),
    CONSTRAINT fk_coverage_expectation_source FOREIGN KEY (source_id) REFERENCES source (id),
    CONSTRAINT ck_coverage_expectation_schedule CHECK (schedule IN ('DAILY', 'BUSINESS_DAYS', 'WEEKLY', 'NONE'))
);

-- Seed de referência (Implementation Plan M3, migration versionada — TDS 29.4 trata
-- referência assim, diferente do seed de desenvolvimento, que nunca entra aqui).
-- IDs fixos, não gerados: são as únicas linhas do sistema criadas fora do fluxo normal
-- de aplicação (mesmo raciocínio do administrador semeado em V2).
INSERT INTO source (id, code, name, timezone, decimal_separator, thousands_separator, date_formats, rounding_mode, active)
VALUES
    ('00000000-0000-7000-8000-000000000101', 'INTERNAL_SALES', 'Vendas Internas',
     'America/Sao_Paulo', ',', '.', ARRAY['dd/MM/yyyy'], 'HALF_UP', TRUE),
    ('00000000-0000-7000-8000-000000000102', 'ACQUIRER_SETTLEMENT', 'Liquidação da Adquirente',
     'America/Sao_Paulo', ',', '.', ARRAY['dd/MM/yyyy'], 'HALF_UP', TRUE);

INSERT INTO source_pair (id, left_source_id, right_source_id, code)
VALUES (
    '00000000-0000-7000-8000-000000000103',
    '00000000-0000-7000-8000-000000000101',
    '00000000-0000-7000-8000-000000000102',
    'INTERNAL_SALES_X_ACQUIRER_SETTLEMENT'
);

INSERT INTO tolerance_config (id, source_pair_id, absolute_amount_minor, currency, aggregate_alert_threshold_minor, updated_by, updated_at, version)
VALUES (
    '00000000-0000-7000-8000-000000000104',
    '00000000-0000-7000-8000-000000000103',
    2, 'BRL', NULL, NULL, now(), 0
);

-- Janela padrão (qualquer meio não listado): 1–3 dias. CREDIT_CARD: 1–31 dias — fecha a
-- decisão D2 do domínio (débito e crédito têm prazos muito diferentes).
INSERT INTO settlement_window (id, source_pair_id, payment_method, min_days, max_days, version)
VALUES
    ('00000000-0000-7000-8000-000000000105', '00000000-0000-7000-8000-000000000103', NULL, 1, 3, 0),
    ('00000000-0000-7000-8000-000000000106', '00000000-0000-7000-8000-000000000103', 'CREDIT_CARD', 1, 31, 0);

INSERT INTO coverage_expectation (id, source_id, schedule, grace_days, active, version)
VALUES (
    '00000000-0000-7000-8000-000000000107',
    '00000000-0000-7000-8000-000000000102',
    'BUSINESS_DAYS', 1, TRUE, 0
);
