-- =====================================================================
-- V6  Wards, rooms, beds, admissions, bed assignments, nurse-ward assignment
-- Invariants:
--   * a bed has at most one open assignment     (uk_bed_assign_active_bed)
--   * a patient has at most one open admission  (uk_admissions_active_patient)
-- =====================================================================

CREATE TABLE wards (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    code            VARCHAR(20)   NOT NULL,
    name            VARCHAR(100)  NOT NULL,
    department_id   BIGINT        NOT NULL,
    ward_type       VARCHAR(20)   NOT NULL,      -- GENERAL, ICU, PAEDIATRIC, MATERNITY, SURGICAL, PRIVATE
    gender_policy   VARCHAR(10)   NOT NULL DEFAULT 'MIXED',
    floor           VARCHAR(10)   NULL,
    active          BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_wards_code (code),
    KEY ix_wards_department (department_id),
    CONSTRAINT fk_wards_department FOREIGN KEY (department_id) REFERENCES departments (id) ON DELETE RESTRICT,
    CONSTRAINT ck_wards_type   CHECK (ward_type IN ('GENERAL','ICU','PAEDIATRIC','MATERNITY','SURGICAL','PRIVATE')),
    CONSTRAINT ck_wards_gender CHECK (gender_policy IN ('MALE','FEMALE','MIXED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE rooms (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    ward_id       BIGINT        NOT NULL,
    room_no       VARCHAR(20)   NOT NULL,
    room_type     VARCHAR(20)   NOT NULL,       -- SHARED, PRIVATE, ISOLATION, ICU
    daily_rate    DECIMAL(12,2) NOT NULL,       -- bed-day charge
    active        BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at    DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at    DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_rooms_ward_no (ward_id, room_no),
    CONSTRAINT fk_rooms_ward FOREIGN KEY (ward_id) REFERENCES wards (id) ON DELETE RESTRICT,
    CONSTRAINT ck_rooms_type CHECK (room_type IN ('SHARED','PRIVATE','ISOLATION','ICU')),
    CONSTRAINT ck_rooms_rate CHECK (daily_rate >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE beds (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    room_id     BIGINT       NOT NULL,
    bed_no      VARCHAR(20)  NOT NULL,
    status      VARCHAR(15)  NOT NULL DEFAULT 'AVAILABLE',
    version     BIGINT       NOT NULL DEFAULT 0,
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_beds_room_no (room_id, bed_no),
    KEY ix_beds_status (status),
    CONSTRAINT fk_beds_room   FOREIGN KEY (room_id) REFERENCES rooms (id) ON DELETE RESTRICT,
    CONSTRAINT ck_beds_status CHECK (status IN ('AVAILABLE','OCCUPIED','CLEANING','MAINTENANCE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE admissions (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    admission_no          VARCHAR(20)  NOT NULL,
    patient_id            BIGINT       NOT NULL,
    attending_doctor_id   BIGINT       NOT NULL,
    department_id         BIGINT       NOT NULL,
    admission_type        VARCHAR(15)  NOT NULL,       -- ELECTIVE, EMERGENCY, TRANSFER_IN
    status                VARCHAR(15)  NOT NULL DEFAULT 'REQUESTED',   -- REQUESTED, ADMITTED, DISCHARGED, CANCELLED
    reason                VARCHAR(500) NOT NULL,
    requested_by          BIGINT       NULL,
    requested_at          DATETIME(3)  NOT NULL,
    admitted_at           DATETIME(3)  NULL,
    discharged_at         DATETIME(3)  NULL,
    discharge_type        VARCHAR(20)  NULL,           -- ROUTINE, AGAINST_ADVICE, TRANSFERRED, DECEASED
    discharge_summary     TEXT         NULL,
    active_patient_id     BIGINT GENERATED ALWAYS AS (CASE WHEN status IN ('REQUESTED','ADMITTED') THEN patient_id ELSE NULL END) STORED,
    version               BIGINT       NOT NULL DEFAULT 0,
    created_at            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_admissions_no (admission_no),
    UNIQUE KEY uk_admissions_active_patient (active_patient_id),
    KEY ix_admissions_patient (patient_id, requested_at),
    KEY ix_admissions_status (status, admitted_at),
    KEY ix_admissions_dept (department_id, admitted_at),
    CONSTRAINT fk_admissions_patient    FOREIGN KEY (patient_id)          REFERENCES patients (id)    ON DELETE RESTRICT,
    CONSTRAINT fk_admissions_doctor     FOREIGN KEY (attending_doctor_id) REFERENCES doctors (id)     ON DELETE RESTRICT,
    CONSTRAINT fk_admissions_department FOREIGN KEY (department_id)       REFERENCES departments (id) ON DELETE RESTRICT,
    CONSTRAINT fk_admissions_requester  FOREIGN KEY (requested_by)        REFERENCES users (id)       ON DELETE SET NULL,
    CONSTRAINT ck_admissions_type      CHECK (admission_type IN ('ELECTIVE','EMERGENCY','TRANSFER_IN')),
    CONSTRAINT ck_admissions_status    CHECK (status IN ('REQUESTED','ADMITTED','DISCHARGED','CANCELLED')),
    CONSTRAINT ck_admissions_discharge CHECK (status <> 'DISCHARGED' OR (discharged_at IS NOT NULL AND discharge_summary IS NOT NULL AND discharge_type IS NOT NULL)),
    CONSTRAINT ck_admissions_dtype     CHECK (discharge_type IS NULL OR discharge_type IN ('ROUTINE','AGAINST_ADVICE','TRANSFERRED','DECEASED')),
    CONSTRAINT ck_admissions_dates     CHECK (discharged_at IS NULL OR admitted_at IS NULL OR discharged_at >= admitted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Bed occupancy history (admission + every transfer)
CREATE TABLE bed_assignments (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    admission_id    BIGINT       NOT NULL,
    bed_id          BIGINT       NOT NULL,
    assigned_at     DATETIME(3)  NOT NULL,
    released_at     DATETIME(3)  NULL,
    release_reason  VARCHAR(20)  NULL,          -- TRANSFER, DISCHARGE, CANCELLED
    assigned_by     BIGINT       NULL,
    active_bed_id   BIGINT GENERATED ALWAYS AS (CASE WHEN released_at IS NULL THEN bed_id ELSE NULL END) STORED,
    active_admission_id BIGINT GENERATED ALWAYS AS (CASE WHEN released_at IS NULL THEN admission_id ELSE NULL END) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_bed_assign_active_bed (active_bed_id),             -- never two patients in one bed
    UNIQUE KEY uk_bed_assign_active_admission (active_admission_id), -- never one patient in two beds
    KEY ix_bed_assign_admission (admission_id, assigned_at),
    KEY ix_bed_assign_bed (bed_id, assigned_at),
    CONSTRAINT fk_bed_assign_admission FOREIGN KEY (admission_id) REFERENCES admissions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_bed_assign_bed       FOREIGN KEY (bed_id)       REFERENCES beds (id)       ON DELETE RESTRICT,
    CONSTRAINT fk_bed_assign_user      FOREIGN KEY (assigned_by)  REFERENCES users (id)      ON DELETE SET NULL,
    CONSTRAINT ck_bed_assign_dates   CHECK (released_at IS NULL OR released_at >= assigned_at),
    CONSTRAINT ck_bed_assign_reason  CHECK (release_reason IS NULL OR release_reason IN ('TRANSFER','DISCHARGE','CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE encounters
    ADD CONSTRAINT fk_encounters_admission FOREIGN KEY (admission_id) REFERENCES admissions (id) ON DELETE RESTRICT;

-- Nurses see patients admitted to wards they are assigned to
CREATE TABLE nurse_ward_assignments (
    employee_id  BIGINT      NOT NULL,
    ward_id      BIGINT      NOT NULL,
    assigned_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (employee_id, ward_id),
    KEY ix_nurse_ward_ward (ward_id),
    CONSTRAINT fk_nurse_ward_employee FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE CASCADE,
    CONSTRAINT fk_nurse_ward_ward     FOREIGN KEY (ward_id)     REFERENCES wards (id)     ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
