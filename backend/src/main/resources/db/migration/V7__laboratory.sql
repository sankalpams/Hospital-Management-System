-- =====================================================================
-- V7  Laboratory: test catalogue, requests, per-test items, results
-- Item status: REQUESTED -> SAMPLE_COLLECTED -> PROCESSING -> COMPLETED -> REVIEWED (or CANCELLED)
-- =====================================================================

CREATE TABLE lab_tests (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    code                VARCHAR(20)   NOT NULL,
    name                VARCHAR(120)  NOT NULL,
    category            VARCHAR(40)   NOT NULL,      -- HAEMATOLOGY, BIOCHEMISTRY, MICROBIOLOGY, ...
    sample_type         VARCHAR(20)   NOT NULL,      -- BLOOD, URINE, STOOL, SWAB, CSF, OTHER
    result_type         VARCHAR(10)   NOT NULL DEFAULT 'NUMERIC',  -- NUMERIC, TEXT
    unit                VARCHAR(20)   NULL,
    ref_range_low       DECIMAL(12,3) NULL,
    ref_range_high      DECIMAL(12,3) NULL,
    ref_range_text      VARCHAR(120)  NULL,
    turnaround_hours    SMALLINT      NOT NULL,
    price               DECIMAL(12,2) NOT NULL,
    active              BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at          DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_lab_tests_code (code),
    KEY ix_lab_tests_category (category),
    CONSTRAINT ck_lab_tests_sample CHECK (sample_type IN ('BLOOD','URINE','STOOL','SWAB','SPUTUM','CSF','TISSUE','OTHER')),
    CONSTRAINT ck_lab_tests_result CHECK (result_type IN ('NUMERIC','TEXT')),
    CONSTRAINT ck_lab_tests_range  CHECK (ref_range_low IS NULL OR ref_range_high IS NULL OR ref_range_high >= ref_range_low),
    CONSTRAINT ck_lab_tests_price  CHECK (price >= 0),
    CONSTRAINT ck_lab_tests_tat    CHECK (turnaround_hours > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE lab_requests (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    request_no        VARCHAR(20)  NOT NULL,
    patient_id        BIGINT       NOT NULL,
    encounter_id      BIGINT       NULL,
    requested_by      BIGINT       NOT NULL,          -- doctors.id
    department_id     BIGINT       NOT NULL,          -- requesting department
    priority          VARCHAR(10)  NOT NULL DEFAULT 'ROUTINE',   -- ROUTINE, URGENT, STAT
    clinical_notes    VARCHAR(500) NULL,
    requested_at      DATETIME(3)  NOT NULL,
    version           BIGINT       NOT NULL DEFAULT 0,
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_lab_requests_no (request_no),
    KEY ix_lab_requests_patient (patient_id, requested_at),
    KEY ix_lab_requests_doctor (requested_by, requested_at),
    KEY ix_lab_requests_time (requested_at, priority),
    CONSTRAINT fk_lab_requests_patient    FOREIGN KEY (patient_id)    REFERENCES patients (id)    ON DELETE RESTRICT,
    CONSTRAINT fk_lab_requests_encounter  FOREIGN KEY (encounter_id)  REFERENCES encounters (id)  ON DELETE RESTRICT,
    CONSTRAINT fk_lab_requests_doctor     FOREIGN KEY (requested_by)  REFERENCES doctors (id)     ON DELETE RESTRICT,
    CONSTRAINT fk_lab_requests_department FOREIGN KEY (department_id) REFERENCES departments (id) ON DELETE RESTRICT,
    CONSTRAINT ck_lab_requests_priority CHECK (priority IN ('ROUTINE','URGENT','STAT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE lab_request_items (
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    lab_request_id     BIGINT        NOT NULL,
    lab_test_id        BIGINT        NOT NULL,
    status             VARCHAR(20)   NOT NULL DEFAULT 'REQUESTED',
    price              DECIMAL(12,2) NOT NULL,         -- snapshot of lab_tests.price
    sample_barcode     VARCHAR(30)   NULL,
    sample_collected_at DATETIME(3)  NULL,
    sample_collected_by BIGINT       NULL,
    processing_started_at DATETIME(3) NULL,
    completed_at       DATETIME(3)   NULL,
    reviewed_at        DATETIME(3)   NULL,
    reviewed_by        BIGINT        NULL,             -- doctors.id
    cancel_reason      VARCHAR(255)  NULL,
    version            BIGINT        NOT NULL DEFAULT 0,
    created_at         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_lab_items_request_test (lab_request_id, lab_test_id),
    UNIQUE KEY uk_lab_items_barcode (sample_barcode),
    KEY ix_lab_items_status (status, created_at),
    KEY ix_lab_items_test_time (lab_test_id, created_at),
    CONSTRAINT fk_lab_items_request   FOREIGN KEY (lab_request_id)      REFERENCES lab_requests (id) ON DELETE CASCADE,
    CONSTRAINT fk_lab_items_test      FOREIGN KEY (lab_test_id)         REFERENCES lab_tests (id)    ON DELETE RESTRICT,
    CONSTRAINT fk_lab_items_collector FOREIGN KEY (sample_collected_by) REFERENCES users (id)        ON DELETE RESTRICT,
    CONSTRAINT fk_lab_items_reviewer  FOREIGN KEY (reviewed_by)         REFERENCES doctors (id)      ON DELETE RESTRICT,
    CONSTRAINT ck_lab_items_status CHECK (status IN ('REQUESTED','SAMPLE_COLLECTED','PROCESSING','COMPLETED','REVIEWED','CANCELLED')),
    CONSTRAINT ck_lab_items_price  CHECK (price >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE lab_results (
    id                   BIGINT        NOT NULL AUTO_INCREMENT,
    lab_request_item_id  BIGINT        NOT NULL,
    numeric_value        DECIMAL(12,3) NULL,
    text_value           VARCHAR(1000) NULL,
    unit                 VARCHAR(20)   NULL,           -- snapshot
    ref_range_low        DECIMAL(12,3) NULL,           -- snapshot
    ref_range_high       DECIMAL(12,3) NULL,           -- snapshot
    flag                 VARCHAR(2)    NULL,           -- H, L, N (factual comparison to reference range only)
    comments             VARCHAR(500)  NULL,
    entered_by           BIGINT        NOT NULL,
    entered_at           DATETIME(3)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_lab_results_item (lab_request_item_id),
    CONSTRAINT fk_lab_results_item FOREIGN KEY (lab_request_item_id) REFERENCES lab_request_items (id) ON DELETE RESTRICT,
    CONSTRAINT fk_lab_results_user FOREIGN KEY (entered_by)          REFERENCES users (id)             ON DELETE RESTRICT,
    CONSTRAINT ck_lab_results_value CHECK (numeric_value IS NOT NULL OR text_value IS NOT NULL),
    CONSTRAINT ck_lab_results_flag  CHECK (flag IS NULL OR flag IN ('H','L','N'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
