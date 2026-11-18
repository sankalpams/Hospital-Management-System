-- =====================================================================
-- V8  Billing: service catalogue, charges, invoices, payments
-- Money: DECIMAL(12,2), HALF_UP rounding in the service layer.
-- Flow: source event -> charge_items (unbilled) -> invoice_items -> invoice -> payments
-- =====================================================================

CREATE TABLE service_catalogue (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    code          VARCHAR(20)   NOT NULL,
    name          VARCHAR(120)  NOT NULL,
    category      VARCHAR(20)   NOT NULL,     -- PROCEDURE, NURSING, IMAGING, AMBULANCE, OTHER
    price         DECIMAL(12,2) NOT NULL,
    active        BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at    DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at    DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_service_code (code),
    CONSTRAINT ck_service_category CHECK (category IN ('PROCEDURE','NURSING','IMAGING','AMBULANCE','OTHER')),
    CONSTRAINT ck_service_price    CHECK (price >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE invoices (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    invoice_no       VARCHAR(20)   NOT NULL,        -- INV-2026-000123
    patient_id       BIGINT        NOT NULL,
    admission_id     BIGINT        NULL,
    status           VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    subtotal         DECIMAL(12,2) NOT NULL DEFAULT 0,
    discount_amount  DECIMAL(12,2) NOT NULL DEFAULT 0,
    discount_reason  VARCHAR(255)  NULL,
    tax_amount       DECIMAL(12,2) NOT NULL DEFAULT 0,
    total_amount     DECIMAL(12,2) NOT NULL DEFAULT 0,
    amount_paid      DECIMAL(12,2) NOT NULL DEFAULT 0,
    balance_due      DECIMAL(12,2) GENERATED ALWAYS AS (total_amount - amount_paid) STORED,
    issued_at        DATETIME(3)   NULL,
    due_date         DATE          NULL,
    void_reason      VARCHAR(255)  NULL,
    voided_at        DATETIME(3)   NULL,
    created_by       BIGINT        NULL,
    version          BIGINT        NOT NULL DEFAULT 0,
    created_at       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_invoices_no (invoice_no),
    KEY ix_invoices_patient (patient_id, created_at),
    KEY ix_invoices_status_issued (status, issued_at),
    KEY ix_invoices_issued (issued_at),
    CONSTRAINT fk_invoices_patient   FOREIGN KEY (patient_id)   REFERENCES patients (id)   ON DELETE RESTRICT,
    CONSTRAINT fk_invoices_admission FOREIGN KEY (admission_id) REFERENCES admissions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_invoices_creator   FOREIGN KEY (created_by)   REFERENCES users (id)      ON DELETE SET NULL,
    CONSTRAINT ck_invoices_status  CHECK (status IN ('DRAFT','ISSUED','PARTIALLY_PAID','PAID','VOID')),
    CONSTRAINT ck_invoices_amounts CHECK (subtotal >= 0 AND discount_amount >= 0 AND tax_amount >= 0 AND total_amount >= 0
                                          AND discount_amount <= subtotal
                                          AND total_amount = subtotal - discount_amount + tax_amount),
    CONSTRAINT ck_invoices_paid    CHECK (amount_paid >= 0 AND amount_paid <= total_amount),
    CONSTRAINT ck_invoices_void    CHECK (status <> 'VOID' OR (void_reason IS NOT NULL AND amount_paid = 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Every billable event. invoice_id NULL = unbilled.
CREATE TABLE charge_items (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    patient_id       BIGINT        NOT NULL,
    category         VARCHAR(20)   NOT NULL,     -- CONSULTATION, LABORATORY, PHARMACY, ADMISSION, SERVICE
    description      VARCHAR(255)  NOT NULL,
    source_type      VARCHAR(30)   NOT NULL,     -- APPOINTMENT, LAB_REQUEST_ITEM, DISPENSATION_ITEM, BED_ASSIGNMENT, SERVICE
    source_id        BIGINT        NOT NULL,
    department_id    BIGINT        NULL,         -- for department revenue analytics
    quantity         DECIMAL(10,2) NOT NULL DEFAULT 1,
    unit_price       DECIMAL(12,2) NOT NULL,
    amount           DECIMAL(12,2) NOT NULL,
    invoice_id       BIGINT        NULL,
    service_date     DATETIME(3)   NOT NULL,
    created_by       BIGINT        NULL,
    created_at       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_charges_source (source_type, source_id),   -- an event is charged once
    KEY ix_charges_patient_unbilled (patient_id, invoice_id),
    KEY ix_charges_invoice (invoice_id),
    KEY ix_charges_category_date (category, service_date),
    KEY ix_charges_dept_date (department_id, service_date),
    CONSTRAINT fk_charges_patient    FOREIGN KEY (patient_id)    REFERENCES patients (id)    ON DELETE RESTRICT,
    CONSTRAINT fk_charges_invoice    FOREIGN KEY (invoice_id)    REFERENCES invoices (id)    ON DELETE RESTRICT,
    CONSTRAINT fk_charges_department FOREIGN KEY (department_id) REFERENCES departments (id) ON DELETE RESTRICT,
    CONSTRAINT fk_charges_creator    FOREIGN KEY (created_by)    REFERENCES users (id)       ON DELETE SET NULL,
    CONSTRAINT ck_charges_category CHECK (category IN ('CONSULTATION','LABORATORY','PHARMACY','ADMISSION','SERVICE')),
    CONSTRAINT ck_charges_qty      CHECK (quantity > 0),
    CONSTRAINT ck_charges_price    CHECK (unit_price >= 0 AND amount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Snapshot lines on the invoice (stable even if catalogue prices change later)
CREATE TABLE invoice_items (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    invoice_id      BIGINT        NOT NULL,
    charge_item_id  BIGINT        NOT NULL,
    category        VARCHAR(20)   NOT NULL,
    description     VARCHAR(255)  NOT NULL,
    quantity        DECIMAL(10,2) NOT NULL,
    unit_price      DECIMAL(12,2) NOT NULL,
    line_total      DECIMAL(12,2) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_invoice_items_charge (charge_item_id),
    KEY ix_invoice_items_invoice (invoice_id),
    CONSTRAINT fk_invoice_items_invoice FOREIGN KEY (invoice_id)     REFERENCES invoices (id)     ON DELETE CASCADE,
    CONSTRAINT fk_invoice_items_charge  FOREIGN KEY (charge_item_id) REFERENCES charge_items (id) ON DELETE RESTRICT,
    CONSTRAINT ck_invoice_items_amounts CHECK (quantity > 0 AND unit_price >= 0 AND line_total >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Payments; refunds are negative rows referencing the original payment.
CREATE TABLE payments (
    id                   BIGINT        NOT NULL AUTO_INCREMENT,
    receipt_no           VARCHAR(20)   NOT NULL,       -- RCP-2026-000123
    invoice_id           BIGINT        NOT NULL,
    amount               DECIMAL(12,2) NOT NULL,
    method               VARCHAR(20)   NOT NULL,
    reference_no         VARCHAR(60)   NULL,           -- card auth code / bank ref / cheque no
    refund_of_payment_id BIGINT        NULL,
    notes                VARCHAR(255)  NULL,
    received_by          BIGINT        NOT NULL,
    received_at          DATETIME(3)   NOT NULL,
    created_at           DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_payments_receipt (receipt_no),
    KEY ix_payments_invoice (invoice_id),
    KEY ix_payments_time_method (received_at, method),
    CONSTRAINT fk_payments_invoice  FOREIGN KEY (invoice_id)           REFERENCES invoices (id) ON DELETE RESTRICT,
    CONSTRAINT fk_payments_refund   FOREIGN KEY (refund_of_payment_id) REFERENCES payments (id) ON DELETE RESTRICT,
    CONSTRAINT fk_payments_receiver FOREIGN KEY (received_by)          REFERENCES users (id)    ON DELETE RESTRICT,
    CONSTRAINT ck_payments_method CHECK (method IN ('CASH','CARD','BANK_TRANSFER','CHEQUE')),
    CONSTRAINT ck_payments_amount CHECK (amount <> 0),
    CONSTRAINT ck_payments_refund CHECK ((amount > 0 AND refund_of_payment_id IS NULL) OR (amount < 0 AND refund_of_payment_id IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
