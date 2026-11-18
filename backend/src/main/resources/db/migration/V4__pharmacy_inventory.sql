-- =====================================================================
-- V4  Pharmacy catalogue, suppliers, batches, stock ledger
-- Invariant: medicine_batches.quantity_on_hand = SUM(stock_transactions.quantity) per batch
-- =====================================================================

CREATE TABLE medicine_categories (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    name         VARCHAR(80)  NOT NULL,
    description  VARCHAR(255) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_med_categories_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE suppliers (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    name            VARCHAR(120) NOT NULL,
    contact_person  VARCHAR(100) NULL,
    phone           VARCHAR(20)  NOT NULL,
    email           VARCHAR(120) NULL,
    address         VARCHAR(255) NULL,
    registration_no VARCHAR(40)  NULL,     -- NMRA / business registration
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_suppliers_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE medicines (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    code            VARCHAR(30)   NOT NULL,
    generic_name    VARCHAR(120)  NOT NULL,
    brand_name      VARCHAR(120)  NULL,
    category_id     BIGINT        NOT NULL,
    dosage_form     VARCHAR(30)   NOT NULL,     -- TABLET, CAPSULE, SYRUP, INJECTION, ...
    strength        VARCHAR(40)   NOT NULL,     -- e.g. 500 mg
    unit            VARCHAR(20)   NOT NULL,     -- dispensing unit: tablet, ml, vial
    unit_price      DECIMAL(12,2) NOT NULL,     -- selling price per unit
    reorder_level   INT           NOT NULL DEFAULT 0,
    prescription_required BOOLEAN NOT NULL DEFAULT TRUE,
    active          BOOLEAN       NOT NULL DEFAULT TRUE,
    version         BIGINT        NOT NULL DEFAULT 0,
    created_at      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_medicines_code (code),
    UNIQUE KEY uk_medicines_identity (generic_name, strength, dosage_form, brand_name),
    KEY ix_medicines_generic (generic_name),
    KEY ix_medicines_category (category_id),
    CONSTRAINT fk_medicines_category FOREIGN KEY (category_id) REFERENCES medicine_categories (id) ON DELETE RESTRICT,
    CONSTRAINT ck_medicines_form    CHECK (dosage_form IN ('TABLET','CAPSULE','SYRUP','SUSPENSION','INJECTION','INFUSION','CREAM','OINTMENT','DROPS','INHALER','SUPPOSITORY','POWDER','OTHER')),
    CONSTRAINT ck_medicines_price   CHECK (unit_price >= 0),
    CONSTRAINT ck_medicines_reorder CHECK (reorder_level >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE medicine_batches (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    medicine_id       BIGINT        NOT NULL,
    supplier_id       BIGINT        NULL,
    batch_no          VARCHAR(40)   NOT NULL,
    manufacture_date  DATE          NULL,
    expiry_date       DATE          NOT NULL,
    received_date     DATE          NOT NULL,
    quantity_received INT           NOT NULL,
    quantity_on_hand  INT           NOT NULL,
    cost_price        DECIMAL(12,2) NOT NULL,     -- per unit
    version           BIGINT        NOT NULL DEFAULT 0,
    created_at        DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_batches_medicine_batch (medicine_id, batch_no),
    KEY ix_batches_fefo (medicine_id, expiry_date, quantity_on_hand),
    KEY ix_batches_expiry (expiry_date),
    CONSTRAINT fk_batches_medicine FOREIGN KEY (medicine_id) REFERENCES medicines (id) ON DELETE RESTRICT,
    CONSTRAINT fk_batches_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers (id) ON DELETE RESTRICT,
    CONSTRAINT ck_batches_qty_nonneg CHECK (quantity_on_hand >= 0),          -- never oversell
    CONSTRAINT ck_batches_received   CHECK (quantity_received > 0),
    CONSTRAINT ck_batches_cost       CHECK (cost_price >= 0),
    CONSTRAINT ck_batches_dates      CHECK (manufacture_date IS NULL OR expiry_date > manufacture_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Append-only stock ledger. quantity is signed: + in, - out.
CREATE TABLE stock_transactions (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    batch_id         BIGINT       NOT NULL,
    medicine_id      BIGINT       NOT NULL,          -- denormalised for reporting
    type             VARCHAR(20)  NOT NULL,
    quantity         INT          NOT NULL,
    balance_after    INT          NOT NULL,
    reference_type   VARCHAR(30)  NULL,              -- DISPENSATION, GRN, ADJUSTMENT
    reference_id     BIGINT       NULL,
    note             VARCHAR(255) NULL,
    performed_by     BIGINT       NULL,
    created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY ix_stock_tx_batch (batch_id, created_at),
    KEY ix_stock_tx_medicine_time (medicine_id, created_at, type),
    KEY ix_stock_tx_reference (reference_type, reference_id),
    CONSTRAINT fk_stock_tx_batch    FOREIGN KEY (batch_id)     REFERENCES medicine_batches (id) ON DELETE RESTRICT,
    CONSTRAINT fk_stock_tx_medicine FOREIGN KEY (medicine_id)  REFERENCES medicines (id)        ON DELETE RESTRICT,
    CONSTRAINT fk_stock_tx_user     FOREIGN KEY (performed_by) REFERENCES users (id)            ON DELETE SET NULL,
    CONSTRAINT ck_stock_tx_type     CHECK (type IN ('RECEIPT','DISPENSE','ADJUSTMENT_IN','ADJUSTMENT_OUT','RETURN','EXPIRED_WRITE_OFF')),
    CONSTRAINT ck_stock_tx_sign     CHECK ((type IN ('RECEIPT','ADJUSTMENT_IN','RETURN') AND quantity > 0)
                                        OR (type IN ('DISPENSE','ADJUSTMENT_OUT','EXPIRED_WRITE_OFF') AND quantity < 0)),
    CONSTRAINT ck_stock_tx_balance  CHECK (balance_after >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
