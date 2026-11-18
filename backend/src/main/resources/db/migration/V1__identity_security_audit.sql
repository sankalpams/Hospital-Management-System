-- =====================================================================
-- V1  Identity, access control, sessions, audit, sequences, settings
-- Conventions: InnoDB, utf8mb4, UTC DATETIME(3), enums as VARCHAR + CHECK
-- =====================================================================

CREATE TABLE roles (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    code         VARCHAR(40)  NOT NULL,
    name         VARCHAR(80)  NOT NULL,
    description  VARCHAR(255) NULL,
    is_system    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_roles_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE permissions (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    code         VARCHAR(60)  NOT NULL,           -- e.g. patient:create
    module       VARCHAR(40)  NOT NULL,           -- e.g. patients
    description  VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_permissions_code (code),
    KEY ix_permissions_module (module)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE role_permissions (
    role_id        BIGINT NOT NULL,
    permission_id  BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    KEY ix_role_permissions_permission (permission_id),
    CONSTRAINT fk_role_permissions_role       FOREIGN KEY (role_id)       REFERENCES roles (id)       ON DELETE CASCADE,
    CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_id) REFERENCES permissions (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE users (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    username              VARCHAR(50)  NOT NULL,           -- stored lower-case
    email                 VARCHAR(120) NULL,
    password_hash         VARCHAR(100) NOT NULL,           -- BCrypt
    full_name             VARCHAR(120) NOT NULL,
    status                VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    must_change_password  BOOLEAN      NOT NULL DEFAULT TRUE,
    password_changed_at   DATETIME(3)  NULL,
    failed_attempts       INT          NOT NULL DEFAULT 0,
    locked_until          DATETIME(3)  NULL,
    last_login_at         DATETIME(3)  NULL,
    version               BIGINT       NOT NULL DEFAULT 0,
    created_at            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_username (username),
    UNIQUE KEY uk_users_email (email),
    KEY ix_users_status (status),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE','INACTIVE')),
    CONSTRAINT ck_users_username_lower CHECK (username = LOWER(username))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE user_roles (
    user_id  BIGINT NOT NULL,
    role_id  BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    KEY ix_user_roles_role (role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Login activity log (successes and failures). Never deleted by the app.
CREATE TABLE login_attempts (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    username        VARCHAR(50)  NOT NULL,
    user_id         BIGINT       NULL,
    success         BOOLEAN      NOT NULL,
    failure_reason  VARCHAR(40)  NULL,           -- BAD_CREDENTIALS, LOCKED, INACTIVE
    ip_address      VARCHAR(45)  NULL,
    user_agent      VARCHAR(255) NULL,
    attempted_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY ix_login_attempts_user_time (user_id, attempted_at),
    KEY ix_login_attempts_username_time (username, attempted_at),
    CONSTRAINT fk_login_attempts_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Spring Session JDBC (server-side HTTP sessions; enables idle timeout + revocation)
CREATE TABLE SPRING_SESSION (
    PRIMARY_ID             CHAR(36)     NOT NULL,
    SESSION_ID             CHAR(36)     NOT NULL,
    CREATION_TIME          BIGINT       NOT NULL,
    LAST_ACCESS_TIME       BIGINT       NOT NULL,
    MAX_INACTIVE_INTERVAL  INT          NOT NULL,
    EXPIRY_TIME            BIGINT       NOT NULL,
    PRINCIPAL_NAME         VARCHAR(100) NULL,
    CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (PRIMARY_ID)
) ENGINE=InnoDB ROW_FORMAT=DYNAMIC;
CREATE UNIQUE INDEX SPRING_SESSION_IX1 ON SPRING_SESSION (SESSION_ID);
CREATE INDEX SPRING_SESSION_IX2 ON SPRING_SESSION (EXPIRY_TIME);
CREATE INDEX SPRING_SESSION_IX3 ON SPRING_SESSION (PRINCIPAL_NAME);

CREATE TABLE SPRING_SESSION_ATTRIBUTES (
    SESSION_PRIMARY_ID  CHAR(36)     NOT NULL,
    ATTRIBUTE_NAME      VARCHAR(200) NOT NULL,
    ATTRIBUTE_BYTES     BLOB         NOT NULL,
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_PRIMARY_ID) REFERENCES SPRING_SESSION (PRIMARY_ID) ON DELETE CASCADE
) ENGINE=InnoDB ROW_FORMAT=DYNAMIC;

-- Append-only audit trail. Written in the same transaction as the change.
CREATE TABLE audit_logs (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    actor_user_id BIGINT       NULL,
    actor_username VARCHAR(50) NULL,
    action        VARCHAR(40)  NOT NULL,     -- CREATE, UPDATE, STATUS_CHANGE, DELETE, VIEW_SENSITIVE, LOGIN, LOGOUT ...
    entity_type   VARCHAR(60)  NOT NULL,
    entity_id     VARCHAR(40)  NULL,
    summary       VARCHAR(255) NULL,
    before_json   JSON         NULL,
    after_json    JSON         NULL,
    ip_address    VARCHAR(45)  NULL,
    request_id    VARCHAR(40)  NULL,
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY ix_audit_entity (entity_type, entity_id, created_at),
    KEY ix_audit_actor_time (actor_user_id, created_at),
    KEY ix_audit_time (created_at),
    CONSTRAINT fk_audit_actor FOREIGN KEY (actor_user_id) REFERENCES users (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Gap-free human-facing numbers (MRN, invoice no, receipt no ...). Row-locked per key.
CREATE TABLE sequences (
    seq_key     VARCHAR(40) NOT NULL,    -- e.g. MRN-2026, INV-2026
    next_value  BIGINT      NOT NULL DEFAULT 1,
    PRIMARY KEY (seq_key),
    CONSTRAINT ck_sequences_positive CHECK (next_value > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE system_settings (
    setting_key    VARCHAR(80)  NOT NULL,
    setting_value  VARCHAR(500) NOT NULL,
    value_type     VARCHAR(20)  NOT NULL DEFAULT 'STRING',
    description    VARCHAR(255) NULL,
    updated_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (setting_key),
    CONSTRAINT ck_settings_type CHECK (value_type IN ('STRING','INT','DECIMAL','BOOLEAN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE notifications (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    user_id       BIGINT       NOT NULL,
    category      VARCHAR(30)  NOT NULL,     -- LAB, PHARMACY, APPOINTMENT, BILLING, SYSTEM
    severity      VARCHAR(10)  NOT NULL DEFAULT 'INFO',
    title         VARCHAR(150) NOT NULL,
    body          VARCHAR(500) NULL,
    link_path     VARCHAR(200) NULL,         -- client route, e.g. /lab/requests/12
    read_at       DATETIME(3)  NULL,
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY ix_notifications_user_unread (user_id, read_at, created_at),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_notifications_severity CHECK (severity IN ('INFO','WARNING','CRITICAL'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
