-- Esquema inicial de Mollet.
-- Las reglas críticas de dinero también se validan aquí (CHECK / UNIQUE) como segunda barrera,
-- por si algún día un bug en el código intenta guardar algo inválido.

CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(120) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    full_name     VARCHAR(120) NOT NULL,
    role          VARCHAR(20)  NOT NULL CHECK (role IN ('USER', 'ADMIN')),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE accounts (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT        NOT NULL UNIQUE REFERENCES users (id),
    account_number VARCHAR(20)   NOT NULL UNIQUE,
    currency       VARCHAR(3)    NOT NULL DEFAULT 'PEN',
    balance        NUMERIC(19, 2) NOT NULL DEFAULT 0 CHECK (balance >= 0),
    daily_limit    NUMERIC(19, 2) NOT NULL CHECK (daily_limit > 0),
    status         VARCHAR(20)   NOT NULL CHECK (status IN ('ACTIVE', 'BLOCKED')),
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- Un movimiento es inmutable: nunca se edita ni se borra (libro contable).
-- DEPOSIT no tiene cuenta origen; TRANSFER siempre la tiene.
CREATE TABLE movements (
    id                BIGSERIAL PRIMARY KEY,
    type              VARCHAR(20)    NOT NULL CHECK (type IN ('DEPOSIT', 'TRANSFER')),
    source_account_id BIGINT REFERENCES accounts (id),
    target_account_id BIGINT         NOT NULL REFERENCES accounts (id),
    amount            NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    description       VARCHAR(140),
    idempotency_key   VARCHAR(64),
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT chk_transfer_has_source CHECK (type <> 'TRANSFER' OR source_account_id IS NOT NULL),
    CONSTRAINT chk_not_same_account    CHECK (source_account_id IS NULL OR source_account_id <> target_account_id),
    -- Una misma clave de idempotencia no puede generar dos transferencias desde la misma cuenta
    CONSTRAINT uq_movement_idempotency UNIQUE (source_account_id, idempotency_key)
);

CREATE INDEX idx_movements_source_created ON movements (source_account_id, created_at DESC);
CREATE INDEX idx_movements_target_created ON movements (target_account_id, created_at DESC);
