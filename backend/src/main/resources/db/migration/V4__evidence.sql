-- V4 — Evidência financeira imutável. Milestone M4 (TDS 7.5, 8; Implementation Plan M4).
--
-- financial_record é o átomo do sistema (Domain §5.2): nenhum papel, nenhuma rotina o
-- altera depois de criado. A garantia técnica é a trigger fincore_reject_mutation, já
-- definida em V1 (audit_event) e reaplicada aqui — TG_TABLE_NAME faz a mensagem apontar a
-- tabela certa sem duplicar a função.
--
-- import_batch_id não tem FK: import_batch só nasce no M6 (FD-9 do Implementation Plan). A
-- FK é adicionada em V6__evidence_fk_import.sql quando a tabela existir.
--
-- currency e fingerprint são CHAR(3)/CHAR(64), não TEXT — convenção explícita da seção 7 do
-- TDS ("dinheiro em BIGINT... com CHAR(3) de moeda ao lado"). O mapeamento Hibernate usa
-- @JdbcTypeCode(SqlTypes.CHAR) para que a validação de schema bata com o tipo real (ver
-- FinancialRecord.java) — diferente da correção aplicada a tolerance_config.currency em V3,
-- onde o próprio TDS não especificava CHAR(3) para aquela coluna.

CREATE TABLE financial_record (
    id                        UUID        NOT NULL,
    source_id                 UUID        NOT NULL,
    import_batch_id           UUID        NOT NULL,
    line_number               INTEGER     NOT NULL,
    external_id               TEXT,
    correlation_key           TEXT,
    direction                 TEXT        NOT NULL,
    record_type               TEXT        NOT NULL,
    gross_amount_minor        BIGINT      NOT NULL,
    declared_fee_amount_minor BIGINT,
    net_amount_minor          BIGINT,
    currency                  CHAR(3)     NOT NULL,
    business_date             DATE        NOT NULL,
    source_timestamp          TIMESTAMPTZ,
    counterparty_document     TEXT,
    payment_method            TEXT,
    description               TEXT,
    description_normalized    TEXT,
    raw_line                  TEXT        NOT NULL,
    fingerprint               CHAR(64)    NOT NULL,
    created_at                TIMESTAMPTZ NOT NULL,

    CONSTRAINT pk_financial_record PRIMARY KEY (id),
    CONSTRAINT fk_financial_record_source FOREIGN KEY (source_id) REFERENCES source (id),

    CONSTRAINT ck_financial_record_direction CHECK (direction IN ('CREDIT', 'DEBIT')),
    CONSTRAINT ck_financial_record_record_type
        CHECK (record_type IN ('SALE', 'SETTLEMENT', 'BANK_MOVEMENT', 'REFUND', 'FEE', 'ADJUSTMENT')),
    CONSTRAINT ck_financial_record_currency CHECK (currency IN ('BRL')),

    -- Valor zero é rejeitado na importação (TDS 7.5) — não existe evidência de valor nulo.
    CONSTRAINT ck_financial_record_gross_amount_nonzero CHECK (gross_amount_minor <> 0),

    -- Coerência estrutural das duas fontes do MVP (achado C-6 do Implementation Plan): um
    -- valor líquido sem taxa declarada não é representável pelos layouts aprovados em DR-1.
    -- Registrado como custo aceito para quando uma fonte só-líquido entrar (ex.: extrato
    -- bancário) — exigirá migration, não é resolvido preventivamente aqui.
    CONSTRAINT ck_financial_record_net_requires_fee
        CHECK (net_amount_minor IS NULL OR declared_fee_amount_minor IS NOT NULL)
);

COMMENT ON TABLE financial_record IS
    'Evidência financeira imutável (Domain §5.2). Sem status de conciliação — isso é Match/Divergence (M9+). Sem updated_at nem version: a ausência é declaração de intenção (TDS 7.5).';

-- I-7: identificador de origem único por fonte, quando presente.
CREATE UNIQUE INDEX uq_financial_record_source_external_id
    ON financial_record (source_id, external_id)
    WHERE external_id IS NOT NULL;

-- Nível A de matching (correlationKey compartilhada entre fontes).
CREATE INDEX idx_financial_record_correlation_key
    ON financial_record (correlation_key, source_id)
    WHERE correlation_key IS NOT NULL;

-- Blocking: reduz o espaço de busca do motor de matching (M9) por fonte/data/direção/tipo.
CREATE INDEX idx_financial_record_blocking
    ON financial_record (source_id, business_date, direction, record_type);

-- Nível B de matching (documento da contraparte).
CREATE INDEX idx_financial_record_counterparty
    ON financial_record (source_id, counterparty_document, business_date)
    WHERE counterparty_document IS NOT NULL;

-- Detecção de repetição (fingerprint identifica; nunca autoriza descarte — Domain §9.3).
CREATE INDEX idx_financial_record_fingerprint ON financial_record (source_id, fingerprint);

-- Detalhe por importação e corte de dados por instante de criação.
CREATE INDEX idx_financial_record_import_batch ON financial_record (import_batch_id);
CREATE INDEX idx_financial_record_created_at ON financial_record (created_at);

CREATE TRIGGER trg_financial_record_reject_mutation
    BEFORE UPDATE OR DELETE ON financial_record
    FOR EACH ROW
    EXECUTE FUNCTION fincore_reject_mutation();

-- record_integrity_flag — estrutura prevista pela TDS 7.5, sem uso neste milestone: a
-- varredura que a povoa é do M7 (Implementation Plan). Nasce aqui porque a tabela pertence
-- ao módulo evidence e sua forma já está fechada nos documentos; nenhuma classe Java a
-- mapeia ainda — evitaria código sem nenhum caso de uso.
CREATE TABLE record_integrity_flag (
    id                      UUID        NOT NULL,
    financial_record_id     UUID        NOT NULL,
    flag_type               TEXT        NOT NULL,
    detected_at             TIMESTAMPTZ NOT NULL,
    detected_by_batch_id    UUID        NOT NULL,
    resolved_at             TIMESTAMPTZ,
    resolved_by_divergence_id UUID,

    CONSTRAINT pk_record_integrity_flag PRIMARY KEY (id),
    CONSTRAINT fk_record_integrity_flag_record
        FOREIGN KEY (financial_record_id) REFERENCES financial_record (id),
    CONSTRAINT ck_record_integrity_flag_type
        CHECK (flag_type IN ('DUPLICATE_EXTERNAL_ID', 'DUPLICATE_CORRELATION_KEY', 'POSSIBLE_DUPLICATE', 'SOURCE_INTERNAL_INCONSISTENCY'))
    -- resolved_by_divergence_id sem FK: divergence só nasce no M12 (FD-9).
);

CREATE UNIQUE INDEX uq_record_integrity_flag_open
    ON record_integrity_flag (financial_record_id, flag_type)
    WHERE resolved_at IS NULL;

COMMENT ON TABLE record_integrity_flag IS
    'O que o sistema aprendeu sobre um registro, fora dele (Domain §5.2). Povoada a partir do M7 — vazia até lá.';

-- record_annotation — "o operador sabe o valor correto", sem tocar na evidência.
-- author_id sem FK para app_user: evidence não depende de identity (TDS 4.2/4.3), mesmo
-- raciocínio de tolerance_config.updated_by no M3.
CREATE TABLE record_annotation (
    id                  UUID        NOT NULL,
    financial_record_id UUID        NOT NULL,
    author_id           UUID        NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL,
    text                TEXT        NOT NULL,

    CONSTRAINT pk_record_annotation PRIMARY KEY (id),
    CONSTRAINT fk_record_annotation_record
        FOREIGN KEY (financial_record_id) REFERENCES financial_record (id)
);

COMMENT ON TABLE record_annotation IS
    'Anotação humana sobre um registro, append-only (TDS 7.5). Nunca altera a evidência.';

CREATE INDEX idx_record_annotation_record ON record_annotation (financial_record_id, created_at);

CREATE TRIGGER trg_record_annotation_reject_mutation
    BEFORE UPDATE OR DELETE ON record_annotation
    FOR EACH ROW
    EXECUTE FUNCTION fincore_reject_mutation();
