-- V5 — Importação síncrona de CSV. Milestone M5 (TDS 7.4, 9; Implementation Plan M6 —
-- ver relatório do M5, seção Decisões, sobre a renumeração de milestone desta sessão).
--
-- import_batch e rejected_record conforme TDS 7.4. uploaded_by não tem FK para app_user:
-- ingestion não depende de identity (TDS 4.2 não lista essa aresta) — mesmo raciocínio já
-- aplicado a tolerance_config.updated_by (V3) e record_annotation.author_id (V4).

CREATE TABLE import_batch (
    id                      UUID        NOT NULL,
    source_id               UUID        NOT NULL,
    original_filename       TEXT        NOT NULL,
    content_sha256          CHAR(64)    NOT NULL,
    byte_size               BIGINT      NOT NULL,
    reference_date          DATE        NOT NULL,
    status                  TEXT        NOT NULL,
    rejection_reason        TEXT,
    reimport_of_id          UUID,
    reimport_reason         TEXT,
    storage_key             TEXT        NOT NULL,
    total_lines             INTEGER,
    accepted_count          INTEGER,
    rejected_count          INTEGER,
    already_existing_count  INTEGER,
    uploaded_by             UUID        NOT NULL,
    uploaded_at             TIMESTAMPTZ NOT NULL,
    started_at              TIMESTAMPTZ,
    finished_at             TIMESTAMPTZ,
    correlation_id          TEXT,
    version                 BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT pk_import_batch PRIMARY KEY (id),
    CONSTRAINT fk_import_batch_source FOREIGN KEY (source_id) REFERENCES source (id),
    CONSTRAINT fk_import_batch_reimport_of FOREIGN KEY (reimport_of_id) REFERENCES import_batch (id),

    CONSTRAINT ck_import_batch_status
        CHECK (status IN ('RECEIVED', 'PROCESSING', 'COMPLETED', 'COMPLETED_WITH_REJECTS', 'REJECTED', 'FAILED')),

    -- Reimportação intencional exige motivo textual (Domain §9.3) — nunca um clique vazio.
    CONSTRAINT ck_import_batch_reimport_reason
        CHECK (reimport_of_id IS NULL OR length(trim(reimport_reason)) >= 10)
);

COMMENT ON TABLE import_batch IS
    'Um arquivo recebido de uma fonte, com seu resultado (Domain §9.1). Estado e contagens mutáveis até o estado terminal (Domain §6.3) — não é imutável por trigger.';

-- I-6: identidade do arquivo é conteúdo + fonte + data de referência (Domain §9.3) — nome
-- do arquivo é irrelevante. Isento quando é reimportação intencional (reimport_of_id).
CREATE UNIQUE INDEX uq_import_batch_content
    ON import_batch (source_id, content_sha256, reference_date)
    WHERE reimport_of_id IS NULL;

-- Base da verificação de cobertura (M7+) e do sweep de trabalhos travados (M8).
CREATE INDEX idx_import_batch_source_reference_date ON import_batch (source_id, reference_date);
CREATE INDEX idx_import_batch_in_progress ON import_batch (status) WHERE status IN ('RECEIVED', 'PROCESSING');

CREATE TABLE rejected_record (
    id                    UUID    NOT NULL,
    import_batch_id       UUID    NOT NULL,
    line_number           INTEGER NOT NULL,
    raw_line              TEXT    NOT NULL,
    reason_code           TEXT    NOT NULL,
    reason_detail         TEXT,
    extracted_amount_minor BIGINT,

    CONSTRAINT pk_rejected_record PRIMARY KEY (id),
    CONSTRAINT fk_rejected_record_batch FOREIGN KEY (import_batch_id) REFERENCES import_batch (id),
    CONSTRAINT uq_rejected_record_batch_line UNIQUE (import_batch_id, line_number)
);

COMMENT ON TABLE rejected_record IS
    'Linha rejeitada na importação, preservada com número e conteúdo bruto (Domain §9.3). Nunca some — contrapartida obrigatória da importação parcial.';

CREATE TRIGGER trg_rejected_record_reject_mutation
    BEFORE UPDATE OR DELETE ON rejected_record
    FOR EACH ROW
    EXECUTE FUNCTION fincore_reject_mutation();

-- Correção de dado de seed (não é edição de V3 — é uma migration nova, forward-only,
-- mesmo mecanismo de qualquer outra correção de configuração via migration versionada).
-- V3 semeou 'dd/MM/yyyy' para as duas fontes como valor de referência, antes de existir
-- qualquer parser real que o consumisse. INTERNAL_SALES.data_hora é data E hora (o
-- registro precisa de source_timestamp para derivar business_date pelo fuso da fonte,
-- Domain §5.5) — 'dd/MM/yyyy' sozinho não tem como capturar a hora. ACQUIRER_SETTLEMENT
-- não muda: data_liquidacao já é só data.
UPDATE source
   SET date_formats = ARRAY['dd/MM/yyyy HH:mm:ss']
 WHERE code = 'INTERNAL_SALES';
