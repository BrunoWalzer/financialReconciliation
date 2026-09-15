-- V2 — Identidade e autenticação. Milestone M2 (TDS 7.1; Implementation Plan M2).
--
-- Quatro tabelas: app_user, user_role, refresh_token, login_throttle.
--
-- Extensão pgcrypto, adicionada agora — justificativa localizada aqui, como V0 pede.
-- O único uso é a linha de seed abaixo: o hash BCrypt do administrador inicial precisa
-- nascer já no formato que Spring Security (BCryptPasswordEncoder) vai verificar depois,
-- e isso não é possível em SQL puro sem uma função de hash. `crypt()`/`gen_salt('bf', ...)`
-- do pgcrypto implementam o mesmo algoritmo (OpenBSD bcrypt) e produzem o mesmo formato
-- $2a$/$2b$ — verificado por teste de integração que faz login de verdade contra este
-- seed. Nenhum outro uso de pgcrypto nesta migration; identificadores continuam sendo
-- UUID v7 gerados na aplicação (ver V0__extensions.sql).
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE app_user (
    id            UUID        NOT NULL,
    email         TEXT        NOT NULL,
    password_hash TEXT        NOT NULL,
    display_name  TEXT        NOT NULL,
    active        BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL,
    version       BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT pk_app_user PRIMARY KEY (id),
    CONSTRAINT uq_app_user_email UNIQUE (email)
);

COMMENT ON TABLE app_user IS 'Usuário autenticado do FINCORE (Domain §4).';

CREATE TABLE user_role (
    user_id UUID NOT NULL,
    role    TEXT NOT NULL,

    CONSTRAINT pk_user_role PRIMARY KEY (user_id, role),
    CONSTRAINT fk_user_role_app_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    -- Só os três papéis do Domain §4: RECONCILIATION_ANALYST, AUDITOR, ADMINISTRATOR.
    CONSTRAINT ck_user_role_role CHECK (role IN ('RECONCILIATION_ANALYST', 'AUDITOR', 'ADMINISTRATOR'))
);

CREATE TABLE refresh_token (
    id           UUID        NOT NULL,
    user_id      UUID        NOT NULL,
    token_hash   TEXT        NOT NULL,
    issued_at    TIMESTAMPTZ NOT NULL,
    expires_at   TIMESTAMPTZ NOT NULL,
    revoked_at   TIMESTAMPTZ,
    replaced_by_id UUID,
    user_agent   TEXT,
    ip           INET,

    CONSTRAINT pk_refresh_token PRIMARY KEY (id),
    CONSTRAINT fk_refresh_token_app_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT uq_refresh_token_token_hash UNIQUE (token_hash),
    -- Um token tem no máximo um sucessor: a cadeia de rotação nunca se ramifica. É o que
    -- torna "a família" caminhável de forma determinística ao detectar reuso (TDS 21.1).
    CONSTRAINT uq_refresh_token_replaced_by_id UNIQUE (replaced_by_id),
    CONSTRAINT fk_refresh_token_replaced_by FOREIGN KEY (replaced_by_id) REFERENCES refresh_token (id)
);

COMMENT ON TABLE refresh_token IS
    'Opaco, hash no banco (TDS 21.1). O token cru nunca é persistido — token_hash é SHA-256 dele.';

-- O conjunto de tokens ainda ativos de um usuário — usado ao autenticar o refresh e,
-- futuramente, para revogar tudo de uma vez (ex.: troca de papel, comprometimento).
CREATE INDEX idx_refresh_token_active_by_user ON refresh_token (user_id) WHERE revoked_at IS NULL;

CREATE TABLE login_throttle (
    email          TEXT        NOT NULL,
    failed_count   INTEGER     NOT NULL DEFAULT 0,
    first_failed_at TIMESTAMPTZ,
    locked_until   TIMESTAMPTZ,

    CONSTRAINT pk_login_throttle PRIMARY KEY (email)
);

COMMENT ON TABLE login_throttle IS
    'Força bruta em login (TDS 21.4). Chave é o e-mail submetido, exista ou não o usuário — a resposta precisa ser idêntica nos dois casos.';

-- Seed: administrador inicial (TDS 29.4). Senha vinda de variável de ambiente via
-- placeholder do Flyway (spring.flyway.placeholders.bootstrapAdminPassword), nunca em
-- texto puro nesta migration. Ver Relatório do M2, seção Desvios, sobre a ausência da
-- flag de troca obrigatória: o Technical Design não define coluna para ela e nenhum
-- endpoint de troca de senha existe neste milestone.
--
-- id fixo (não gerado): é a única linha do sistema criada fora do fluxo normal de
-- aplicação, então não há por que gerar um UUID v7 em SQL para ela.
INSERT INTO app_user (id, email, password_hash, display_name, active, created_at, updated_at, version)
VALUES (
    '00000000-0000-7000-8000-000000000001',
    'admin@fincore.dev',
    crypt('${bootstrapAdminPassword}', gen_salt('bf', 12)),
    'Administrador',
    TRUE,
    now(),
    now(),
    0
);

INSERT INTO user_role (user_id, role)
VALUES ('00000000-0000-7000-8000-000000000001', 'ADMINISTRATOR');
