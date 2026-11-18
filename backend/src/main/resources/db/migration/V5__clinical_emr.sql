-- =====================================================================
-- V5  EMR: encounters, diagnoses, notes, vitals, allergies, prescriptions, dispensing
-- =====================================================================

CREATE TABLE encounters (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    encounter_no     VARCHAR(20)  NOT NULL,
    patient_id       BIGINT       NOT NULL,
    doctor_id        BIGINT       NOT NULL,
    department_id    BIGINT       NOT NULL,
    type             VARCHAR(15)  NOT NULL,          -- OPD, INPATIENT, EMERGENCY
    appointment_id   BIGINT       NULL,
    admission_id     BIGINT       NULL,              -- FK added in V6
    chief_complaint  VARCHAR(500) NULL,
    status           VARCHAR(15)  NOT NULL DEFAULT 'OPEN',
    started_at       DATETIME(3)  NOT NULL,
    closed_at        DATETIME(3)  NULL,
    version          BIGINT       NOT NULL DEFAULT 0,
    created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_encounters_no (encounter_no),
    UNIQUE KEY uk_encounters_appointment (appointment_id),
    KEY ix_encounters_patient (patient_id, started_at),
    KEY ix_encounters_doctor (doctor_id, started_at),
    KEY ix_encounters_dept (department_id, started_at),
    CONSTRAINT fk_encounters_patient     FOREIGN KEY (patient_id)     REFERENCES patients (id)     ON DELETE RESTRICT,
    CONSTRAINT fk_encounters_doctor      FOREIGN KEY (doctor_id)      REFERENCES doctors (id)      ON DELETE RESTRICT,
    CONSTRAINT fk_encounters_department  FOREIGN KEY (department_id)  REFERENCES departments (id)  ON DELETE RESTRICT,
    CONSTRAINT fk_encounters_appointment FOREIGN KEY (appointment_id) REFERENCES appointments (id) ON DELETE RESTRICT,
    CONSTRAINT ck_encounters_type   CHECK (type IN ('OPD','INPATIENT','EMERGENCY')),
    CONSTRAINT ck_encounters_status CHECK (status IN ('OPEN','CLOSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE diagnoses (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    encounter_id   BIGINT       NOT NULL,
    patient_id     BIGINT       NOT NULL,             -- denormalised for history queries
    icd10_code     VARCHAR(10)  NULL,
    description    VARCHAR(255) NOT NULL,
    type           VARCHAR(10)  NOT NULL,             -- PRIMARY, SECONDARY
    certainty      VARCHAR(12)  NOT NULL DEFAULT 'CONFIRMED',  -- PROVISIONAL, CONFIRMED, RULED_OUT
    diagnosed_by   BIGINT       NOT NULL,             -- doctors.id
    created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY ix_diagnoses_encounter (encounter_id),
    KEY ix_diagnoses_patient (patient_id, created_at),
    KEY ix_diagnoses_icd (icd10_code, created_at),
    CONSTRAINT fk_diagnoses_encounter FOREIGN KEY (encounter_id) REFERENCES encounters (id) ON DELETE RESTRICT,
    CONSTRAINT fk_diagnoses_patient   FOREIGN KEY (patient_id)   REFERENCES patients (id)   ON DELETE RESTRICT,
    CONSTRAINT fk_diagnoses_doctor    FOREIGN KEY (diagnosed_by) REFERENCES doctors (id)    ON DELETE RESTRICT,
    CONSTRAINT ck_diagnoses_type      CHECK (type IN ('PRIMARY','SECONDARY')),
    CONSTRAINT ck_diagnoses_certainty CHECK (certainty IN ('PROVISIONAL','CONFIRMED','RULED_OUT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Doctor / nursing notes. Immutable once signed; corrections are addenda (parent_note_id).
CREATE TABLE clinical_notes (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    encounter_id    BIGINT       NOT NULL,
    patient_id      BIGINT       NOT NULL,
    note_type       VARCHAR(20)  NOT NULL,           -- DOCTOR_NOTE, NURSING_NOTE, TREATMENT, DISCHARGE_SUMMARY, ADDENDUM
    content         TEXT         NOT NULL,
    author_user_id  BIGINT       NOT NULL,
    parent_note_id  BIGINT       NULL,
    signed_at       DATETIME(3)  NULL,
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY ix_notes_encounter (encounter_id, created_at),
    KEY ix_notes_patient (patient_id, created_at),
    CONSTRAINT fk_notes_encounter FOREIGN KEY (encounter_id)   REFERENCES encounters (id)     ON DELETE RESTRICT,
    CONSTRAINT fk_notes_patient   FOREIGN KEY (patient_id)     REFERENCES patients (id)       ON DELETE RESTRICT,
    CONSTRAINT fk_notes_author    FOREIGN KEY (author_user_id) REFERENCES users (id)          ON DELETE RESTRICT,
    CONSTRAINT fk_notes_parent    FOREIGN KEY (parent_note_id) REFERENCES clinical_notes (id) ON DELETE RESTRICT,
    CONSTRAINT ck_notes_type CHECK (note_type IN ('DOCTOR_NOTE','NURSING_NOTE','TREATMENT','DISCHARGE_SUMMARY','ADDENDUM'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE vital_signs (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    patient_id         BIGINT       NOT NULL,
    encounter_id       BIGINT       NULL,
    recorded_by        BIGINT       NOT NULL,
    recorded_at        DATETIME(3)  NOT NULL,
    temperature_c      DECIMAL(4,1) NULL,
    pulse_bpm          SMALLINT     NULL,
    systolic_mmhg      SMALLINT     NULL,
    diastolic_mmhg     SMALLINT     NULL,
    respiratory_rate   SMALLINT     NULL,
    spo2_percent       TINYINT      NULL,
    weight_kg          DECIMAL(5,2) NULL,
    height_cm          DECIMAL(5,1) NULL,
    blood_glucose_mmol DECIMAL(4,1) NULL,
    notes              VARCHAR(255) NULL,
    created_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY ix_vitals_patient_time (patient_id, recorded_at),
    KEY ix_vitals_encounter (encounter_id),
    CONSTRAINT fk_vitals_patient   FOREIGN KEY (patient_id)   REFERENCES patients (id)   ON DELETE RESTRICT,
    CONSTRAINT fk_vitals_encounter FOREIGN KEY (encounter_id) REFERENCES encounters (id) ON DELETE RESTRICT,
    CONSTRAINT fk_vitals_user      FOREIGN KEY (recorded_by)  REFERENCES users (id)      ON DELETE RESTRICT,
    -- physiologically plausible ranges (data-quality guard, not clinical thresholds)
    CONSTRAINT ck_vitals_temp  CHECK (temperature_c IS NULL OR temperature_c BETWEEN 25.0 AND 45.0),
    CONSTRAINT ck_vitals_pulse CHECK (pulse_bpm IS NULL OR pulse_bpm BETWEEN 20 AND 250),
    CONSTRAINT ck_vitals_sys   CHECK (systolic_mmhg IS NULL OR systolic_mmhg BETWEEN 40 AND 300),
    CONSTRAINT ck_vitals_dia   CHECK (diastolic_mmhg IS NULL OR diastolic_mmhg BETWEEN 20 AND 200),
    CONSTRAINT ck_vitals_bp    CHECK (systolic_mmhg IS NULL OR diastolic_mmhg IS NULL OR systolic_mmhg > diastolic_mmhg),
    CONSTRAINT ck_vitals_rr    CHECK (respiratory_rate IS NULL OR respiratory_rate BETWEEN 4 AND 80),
    CONSTRAINT ck_vitals_spo2  CHECK (spo2_percent IS NULL OR spo2_percent BETWEEN 50 AND 100),
    CONSTRAINT ck_vitals_wt    CHECK (weight_kg IS NULL OR weight_kg BETWEEN 0.3 AND 400),
    CONSTRAINT ck_vitals_ht    CHECK (height_cm IS NULL OR height_cm BETWEEN 20 AND 260)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE allergies (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    patient_id     BIGINT       NOT NULL,
    allergen_type  VARCHAR(15)  NOT NULL,       -- DRUG, FOOD, ENVIRONMENT, OTHER
    allergen       VARCHAR(120) NOT NULL,       -- for DRUG: generic name, matched against medicines.generic_name
    reaction       VARCHAR(255) NULL,
    severity       VARCHAR(10)  NOT NULL,       -- MILD, MODERATE, SEVERE
    status         VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE',
    recorded_by    BIGINT       NOT NULL,
    created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_allergies_patient_allergen (patient_id, allergen_type, allergen),
    CONSTRAINT fk_allergies_patient FOREIGN KEY (patient_id)  REFERENCES patients (id) ON DELETE RESTRICT,
    CONSTRAINT fk_allergies_user    FOREIGN KEY (recorded_by) REFERENCES users (id)    ON DELETE RESTRICT,
    CONSTRAINT ck_allergies_type     CHECK (allergen_type IN ('DRUG','FOOD','ENVIRONMENT','OTHER')),
    CONSTRAINT ck_allergies_severity CHECK (severity IN ('MILD','MODERATE','SEVERE')),
    CONSTRAINT ck_allergies_status   CHECK (status IN ('ACTIVE','RESOLVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE prescriptions (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    prescription_no   VARCHAR(20)  NOT NULL,
    encounter_id      BIGINT       NOT NULL,
    patient_id        BIGINT       NOT NULL,
    doctor_id         BIGINT       NOT NULL,
    status            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',   -- ACTIVE, PARTIALLY_DISPENSED, DISPENSED, CANCELLED
    notes             VARCHAR(500) NULL,
    allergy_override_reason VARCHAR(255) NULL,                  -- set when doctor acknowledged an allergy warning
    version           BIGINT       NOT NULL DEFAULT 0,
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_prescriptions_no (prescription_no),
    KEY ix_prescriptions_patient (patient_id, created_at),
    KEY ix_prescriptions_status (status, created_at),
    CONSTRAINT fk_prescriptions_encounter FOREIGN KEY (encounter_id) REFERENCES encounters (id) ON DELETE RESTRICT,
    CONSTRAINT fk_prescriptions_patient   FOREIGN KEY (patient_id)   REFERENCES patients (id)   ON DELETE RESTRICT,
    CONSTRAINT fk_prescriptions_doctor    FOREIGN KEY (doctor_id)    REFERENCES doctors (id)    ON DELETE RESTRICT,
    CONSTRAINT ck_prescriptions_status CHECK (status IN ('ACTIVE','PARTIALLY_DISPENSED','DISPENSED','CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE prescription_items (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    prescription_id     BIGINT       NOT NULL,
    medicine_id         BIGINT       NOT NULL,
    dose                VARCHAR(40)  NOT NULL,      -- e.g. 1 tablet
    frequency           VARCHAR(40)  NOT NULL,      -- e.g. TDS / every 8 hours
    route               VARCHAR(20)  NOT NULL,      -- ORAL, IV, IM, TOPICAL ...
    duration_days       SMALLINT     NOT NULL,
    quantity_prescribed INT          NOT NULL,
    quantity_dispensed  INT          NOT NULL DEFAULT 0,
    instructions        VARCHAR(255) NULL,
    PRIMARY KEY (id),
    KEY ix_rx_items_prescription (prescription_id),
    KEY ix_rx_items_medicine (medicine_id),
    CONSTRAINT fk_rx_items_prescription FOREIGN KEY (prescription_id) REFERENCES prescriptions (id) ON DELETE CASCADE,
    CONSTRAINT fk_rx_items_medicine     FOREIGN KEY (medicine_id)     REFERENCES medicines (id)     ON DELETE RESTRICT,
    CONSTRAINT ck_rx_items_qty      CHECK (quantity_prescribed > 0),
    CONSTRAINT ck_rx_items_disp     CHECK (quantity_dispensed BETWEEN 0 AND quantity_prescribed),
    CONSTRAINT ck_rx_items_duration CHECK (duration_days > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- One dispensing event (may cover several items, each drawn from one or more batches)
CREATE TABLE dispensations (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    dispensation_no  VARCHAR(20)  NOT NULL,
    prescription_id  BIGINT       NOT NULL,
    patient_id       BIGINT       NOT NULL,
    dispensed_by     BIGINT       NOT NULL,
    dispensed_at     DATETIME(3)  NOT NULL,
    notes            VARCHAR(255) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_dispensations_no (dispensation_no),
    KEY ix_dispensations_rx (prescription_id),
    KEY ix_dispensations_time (dispensed_at),
    CONSTRAINT fk_dispensations_rx      FOREIGN KEY (prescription_id) REFERENCES prescriptions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_dispensations_patient FOREIGN KEY (patient_id)      REFERENCES patients (id)      ON DELETE RESTRICT,
    CONSTRAINT fk_dispensations_user    FOREIGN KEY (dispensed_by)    REFERENCES users (id)         ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE dispensation_items (
    id                    BIGINT        NOT NULL AUTO_INCREMENT,
    dispensation_id       BIGINT        NOT NULL,
    prescription_item_id  BIGINT        NOT NULL,
    batch_id              BIGINT        NOT NULL,
    quantity              INT           NOT NULL,
    unit_price            DECIMAL(12,2) NOT NULL,   -- price snapshot at dispense time
    PRIMARY KEY (id),
    KEY ix_disp_items_dispensation (dispensation_id),
    KEY ix_disp_items_rx_item (prescription_item_id),
    KEY ix_disp_items_batch (batch_id),
    CONSTRAINT fk_disp_items_dispensation FOREIGN KEY (dispensation_id)      REFERENCES dispensations (id)      ON DELETE CASCADE,
    CONSTRAINT fk_disp_items_rx_item      FOREIGN KEY (prescription_item_id) REFERENCES prescription_items (id) ON DELETE RESTRICT,
    CONSTRAINT fk_disp_items_batch        FOREIGN KEY (batch_id)             REFERENCES medicine_batches (id)   ON DELETE RESTRICT,
    CONSTRAINT ck_disp_items_qty   CHECK (quantity > 0),
    CONSTRAINT ck_disp_items_price CHECK (unit_price >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
