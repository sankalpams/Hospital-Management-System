-- =====================================================================
-- V3  Patients, emergency contacts, documents, appointments
-- =====================================================================

CREATE TABLE patients (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    mrn                VARCHAR(20)  NOT NULL,           -- HMS-2026-000123
    first_name         VARCHAR(60)  NOT NULL,
    last_name          VARCHAR(60)  NOT NULL,
    nic                VARCHAR(12)  NULL,               -- Sri Lankan NIC (old 9+V/X or new 12 digits)
    passport_no        VARCHAR(20)  NULL,
    gender             VARCHAR(10)  NOT NULL,
    date_of_birth      DATE         NOT NULL,
    blood_group        VARCHAR(5)   NULL,
    marital_status     VARCHAR(15)  NULL,
    phone              VARCHAR(20)  NOT NULL,
    email              VARCHAR(120) NULL,
    address_line1      VARCHAR(150) NULL,
    address_line2      VARCHAR(150) NULL,
    city               VARCHAR(60)  NULL,
    district           VARCHAR(40)  NULL,
    occupation         VARCHAR(80)  NULL,
    status             VARCHAR(15)  NOT NULL DEFAULT 'ACTIVE',
    deceased_at        DATE         NULL,
    registered_by      BIGINT       NULL,
    version            BIGINT       NOT NULL DEFAULT 0,
    created_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_patients_mrn (mrn),
    UNIQUE KEY uk_patients_nic (nic),
    UNIQUE KEY uk_patients_passport (passport_no),
    KEY ix_patients_name (last_name, first_name),
    KEY ix_patients_phone (phone),
    KEY ix_patients_dup_check (date_of_birth, last_name, first_name),
    KEY ix_patients_created (created_at),
    KEY ix_patients_status (status),
    CONSTRAINT fk_patients_registered_by FOREIGN KEY (registered_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_patients_gender  CHECK (gender IN ('MALE','FEMALE','OTHER')),
    CONSTRAINT ck_patients_blood   CHECK (blood_group IS NULL OR blood_group IN ('A+','A-','B+','B-','AB+','AB-','O+','O-')),
    CONSTRAINT ck_patients_marital CHECK (marital_status IS NULL OR marital_status IN ('SINGLE','MARRIED','DIVORCED','WIDOWED')),
    CONSTRAINT ck_patients_status  CHECK (status IN ('ACTIVE','INACTIVE','DECEASED')),
    CONSTRAINT ck_patients_deceased CHECK ((status = 'DECEASED') = (deceased_at IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE patient_emergency_contacts (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    patient_id    BIGINT       NOT NULL,
    name          VARCHAR(120) NOT NULL,
    relationship  VARCHAR(40)  NOT NULL,
    phone         VARCHAR(20)  NOT NULL,
    alt_phone     VARCHAR(20)  NULL,
    is_primary    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY ix_emergency_patient (patient_id),
    CONSTRAINT fk_emergency_patient FOREIGN KEY (patient_id) REFERENCES patients (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Files stored on disk outside web root; DB keeps metadata only.
CREATE TABLE patient_documents (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    patient_id      BIGINT       NOT NULL,
    category        VARCHAR(30)  NOT NULL,     -- ID_PROOF, REFERRAL, LAB_EXTERNAL, IMAGING, DISCHARGE, CONSENT, OTHER
    title           VARCHAR(150) NOT NULL,
    original_name   VARCHAR(255) NOT NULL,
    stored_name     CHAR(36)     NOT NULL,     -- UUID, no extension from user input
    content_type    VARCHAR(60)  NOT NULL,
    size_bytes      BIGINT       NOT NULL,
    sha256          CHAR(64)     NOT NULL,
    uploaded_by     BIGINT       NULL,
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_documents_stored (stored_name),
    KEY ix_documents_patient (patient_id, created_at),
    CONSTRAINT fk_documents_patient  FOREIGN KEY (patient_id)  REFERENCES patients (id) ON DELETE RESTRICT,
    CONSTRAINT fk_documents_uploader FOREIGN KEY (uploaded_by) REFERENCES users (id)    ON DELETE SET NULL,
    CONSTRAINT ck_documents_category CHECK (category IN ('ID_PROOF','REFERRAL','LAB_EXTERNAL','IMAGING','DISCHARGE','CONSENT','OTHER')),
    CONSTRAINT ck_documents_size     CHECK (size_bytes > 0 AND size_bytes <= 10485760)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE appointments (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    appointment_no       VARCHAR(20)  NOT NULL,       -- APT-2026-000123
    patient_id           BIGINT       NOT NULL,
    doctor_id            BIGINT       NOT NULL,
    department_id        BIGINT       NOT NULL,
    slot_start           DATETIME     NOT NULL,       -- UTC
    slot_end             DATETIME     NOT NULL,
    token_no             SMALLINT     NOT NULL,       -- queue number for the doctor's session that day
    type                 VARCHAR(20)  NOT NULL DEFAULT 'CONSULTATION',  -- CONSULTATION, FOLLOW_UP, WALK_IN
    status               VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    reason               VARCHAR(255) NULL,
    cancel_reason        VARCHAR(255) NULL,
    rescheduled_from_id  BIGINT       NULL,
    booked_by            BIGINT       NULL,
    confirmed_at         DATETIME(3)  NULL,
    checked_in_at        DATETIME(3)  NULL,
    started_at           DATETIME(3)  NULL,
    completed_at         DATETIME(3)  NULL,
    cancelled_at         DATETIME(3)  NULL,
    -- Only "live" appointments occupy the slot: NULL for CANCELLED / NO_SHOW so the slot frees up.
    active_slot_start    DATETIME GENERATED ALWAYS AS (CASE WHEN status IN ('CANCELLED','NO_SHOW') THEN NULL ELSE slot_start END) STORED,
    version              BIGINT       NOT NULL DEFAULT 0,
    created_at           DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at           DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_appointments_no (appointment_no),
    UNIQUE KEY uk_appointments_doctor_active_slot (doctor_id, active_slot_start),   -- no double booking
    KEY ix_appointments_patient (patient_id, slot_start),
    KEY ix_appointments_doctor_day (doctor_id, slot_start, status),
    KEY ix_appointments_dept_day (department_id, slot_start, status),
    KEY ix_appointments_status_day (status, slot_start),
    CONSTRAINT fk_appointments_patient     FOREIGN KEY (patient_id)          REFERENCES patients (id)     ON DELETE RESTRICT,
    CONSTRAINT fk_appointments_doctor      FOREIGN KEY (doctor_id)           REFERENCES doctors (id)      ON DELETE RESTRICT,
    CONSTRAINT fk_appointments_department  FOREIGN KEY (department_id)       REFERENCES departments (id)  ON DELETE RESTRICT,
    CONSTRAINT fk_appointments_rescheduled FOREIGN KEY (rescheduled_from_id) REFERENCES appointments (id) ON DELETE RESTRICT,
    CONSTRAINT fk_appointments_booked_by   FOREIGN KEY (booked_by)           REFERENCES users (id)        ON DELETE SET NULL,
    CONSTRAINT ck_appointments_type   CHECK (type IN ('CONSULTATION','FOLLOW_UP','WALK_IN')),
    CONSTRAINT ck_appointments_status CHECK (status IN ('PENDING','CONFIRMED','CHECKED_IN','IN_PROGRESS','COMPLETED','CANCELLED','NO_SHOW')),
    CONSTRAINT ck_appointments_time   CHECK (slot_end > slot_start),
    CONSTRAINT ck_appointments_cancel CHECK (status <> 'CANCELLED' OR cancel_reason IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
