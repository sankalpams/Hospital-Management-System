CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE roles (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(40) NOT NULL UNIQUE,
    name VARCHAR(80) NOT NULL,
    description VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE permissions (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(80) NOT NULL UNIQUE,
    description VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE role_permissions (
    role_id BIGINT NOT NULL REFERENCES roles(id),
    permission_id BIGINT NOT NULL REFERENCES permissions(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(80) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    phone VARCHAR(20),
    role_id BIGINT NOT NULL REFERENCES roles(id),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    force_password_change BOOLEAN NOT NULL DEFAULT TRUE,
    failed_login_attempts SMALLINT NOT NULL DEFAULT 0 CHECK (failed_login_attempts >= 0),
    locked_until TIMESTAMPTZ,
    last_login_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT users_username_uk UNIQUE (username),
    CONSTRAINT users_email_uk UNIQUE (email),
    CONSTRAINT users_phone_lk CHECK (phone IS NULL OR phone ~ '^\\+94[0-9]{9}$')
);
CREATE INDEX users_name_idx ON users (last_name, first_name) WHERE deleted_at IS NULL;
CREATE INDEX users_role_idx ON users (role_id) WHERE deleted_at IS NULL;

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    token_hash CHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    replaced_by_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT refresh_expiry_check CHECK (expires_at > created_at)
);
CREATE INDEX refresh_tokens_user_idx ON refresh_tokens(user_id, expires_at);

CREATE TABLE password_reset_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    token_hash CHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT reset_expiry_check CHECK (expires_at > created_at)
);
CREATE INDEX reset_tokens_user_idx ON password_reset_tokens(user_id, expires_at);

CREATE TABLE login_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id),
    username_attempted VARCHAR(80) NOT NULL,
    outcome VARCHAR(20) NOT NULL CHECK (outcome IN ('SUCCESS', 'FAILURE', 'LOCKED', 'RATE_LIMITED', 'LOGOUT')),
    ip_address VARCHAR(64),
    user_agent VARCHAR(512),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX login_history_user_idx ON login_history(user_id, occurred_at DESC);

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id),
    role_code VARCHAR(40),
    action VARCHAR(20) NOT NULL CHECK (action IN ('VIEW', 'CREATE', 'UPDATE', 'DELETE', 'LOGIN', 'EXPORT')),
    entity_type VARCHAR(100) NOT NULL,
    entity_id VARCHAR(100),
    before_json JSONB,
    after_json JSONB,
    ip_address VARCHAR(64),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX audit_logs_lookup_idx ON audit_logs(entity_type, entity_id, occurred_at DESC);
CREATE INDEX audit_logs_user_idx ON audit_logs(user_id, occurred_at DESC);
CREATE INDEX audit_logs_action_idx ON audit_logs(action, occurred_at DESC);

CREATE OR REPLACE FUNCTION prevent_audit_log_mutation() RETURNS trigger AS $$
BEGIN
  RAISE EXCEPTION 'audit_logs is append-only';
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER audit_logs_immutable BEFORE UPDATE OR DELETE ON audit_logs
FOR EACH ROW EXECUTE FUNCTION prevent_audit_log_mutation();
