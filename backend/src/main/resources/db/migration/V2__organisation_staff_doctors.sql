-- =====================================================================
-- V2  Departments, employees, doctors, schedules, attendance, leave
-- =====================================================================

CREATE TABLE departments (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    code              VARCHAR(20)  NOT NULL,
    name              VARCHAR(100) NOT NULL,
    type              VARCHAR(20)  NOT NULL,         -- CLINICAL, DIAGNOSTIC, SUPPORT, ADMINISTRATIVE
    description       VARCHAR(255) NULL,
    location          VARCHAR(100) NULL,
    phone_extension   VARCHAR(10)  NULL,
    head_employee_id  BIGINT       NULL,             -- FK added after employees exists
    active            BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_departments_code (code),
    UNIQUE KEY uk_departments_name (name),
    CONSTRAINT ck_departments_type CHECK (type IN ('CLINICAL','DIAGNOSTIC','SUPPORT','ADMINISTRATIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE employees (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    employee_no        VARCHAR(20)  NOT NULL,         -- EMP-000123
    user_id            BIGINT       NULL,             -- login account (optional; not every employee logs in)
    department_id      BIGINT       NOT NULL,
    first_name         VARCHAR(60)  NOT NULL,
    last_name          VARCHAR(60)  NOT NULL,
    nic                VARCHAR(12)  NULL,
    gender             VARCHAR(10)  NOT NULL,
    date_of_birth      DATE         NULL,
    phone              VARCHAR(20)  NOT NULL,
    email              VARCHAR(120) NULL,
    address            VARCHAR(255) NULL,
    designation        VARCHAR(80)  NOT NULL,
    staff_category     VARCHAR(20)  NOT NULL,         -- DOCTOR, NURSE, LAB, PHARMACY, ADMIN, FINANCE, SUPPORT
    employment_type    VARCHAR(20)  NOT NULL,         -- PERMANENT, CONTRACT, VISITING, TRAINEE
    join_date          DATE         NOT NULL,
    leave_date         DATE         NULL,
    status             VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    version            BIGINT       NOT NULL DEFAULT 0,
    created_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_employees_no (employee_no),
    UNIQUE KEY uk_employees_user (user_id),
    UNIQUE KEY uk_employees_nic (nic),
    KEY ix_employees_department (department_id, status),
    KEY ix_employees_name (last_name, first_name),
    KEY ix_employees_category (staff_category, status),
    CONSTRAINT fk_employees_user       FOREIGN KEY (user_id)       REFERENCES users (id)       ON DELETE SET NULL,
    CONSTRAINT fk_employees_department FOREIGN KEY (department_id) REFERENCES departments (id) ON DELETE RESTRICT,
    CONSTRAINT ck_employees_gender     CHECK (gender IN ('MALE','FEMALE','OTHER')),
    CONSTRAINT ck_employees_category   CHECK (staff_category IN ('DOCTOR','NURSE','LAB','PHARMACY','ADMIN','FINANCE','SUPPORT')),
    CONSTRAINT ck_employees_type       CHECK (employment_type IN ('PERMANENT','CONTRACT','VISITING','TRAINEE')),
    CONSTRAINT ck_employees_status     CHECK (status IN ('ACTIVE','ON_LEAVE','SUSPENDED','RESIGNED','TERMINATED')),
    CONSTRAINT ck_employees_dates      CHECK (leave_date IS NULL OR leave_date >= join_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE departments
    ADD CONSTRAINT fk_departments_head FOREIGN KEY (head_employee_id) REFERENCES employees (id) ON DELETE SET NULL;

-- Doctor profile: 1:1 extension of an employee with staff_category = DOCTOR
CREATE TABLE doctors (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    employee_id         BIGINT        NOT NULL,
    registration_no     VARCHAR(30)   NOT NULL,       -- SLMC registration
    specialization      VARCHAR(100)  NOT NULL,
    qualifications      VARCHAR(255)  NULL,
    consultation_fee    DECIMAL(12,2) NOT NULL,
    accepting_patients  BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at          DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_doctors_employee (employee_id),
    UNIQUE KEY uk_doctors_registration (registration_no),
    KEY ix_doctors_specialization (specialization),
    CONSTRAINT fk_doctors_employee FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE RESTRICT,
    CONSTRAINT ck_doctors_fee CHECK (consultation_fee >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Recurring weekly consultation sessions
CREATE TABLE doctor_schedules (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    doctor_id      BIGINT      NOT NULL,
    day_of_week    TINYINT     NOT NULL,              -- 1 = Monday ... 7 = Sunday (ISO)
    start_time     TIME        NOT NULL,
    end_time       TIME        NOT NULL,
    slot_minutes   SMALLINT    NOT NULL DEFAULT 15,
    max_patients   SMALLINT    NOT NULL,
    room_label     VARCHAR(40) NULL,
    effective_from DATE        NOT NULL,
    effective_to   DATE        NULL,
    active         BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY ix_schedules_doctor_day (doctor_id, day_of_week, active),
    CONSTRAINT fk_schedules_doctor FOREIGN KEY (doctor_id) REFERENCES doctors (id) ON DELETE CASCADE,
    CONSTRAINT ck_schedules_day    CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT ck_schedules_time   CHECK (end_time > start_time),
    CONSTRAINT ck_schedules_slot   CHECK (slot_minutes BETWEEN 5 AND 120),
    CONSTRAINT ck_schedules_max    CHECK (max_patients > 0),
    CONSTRAINT ck_schedules_range  CHECK (effective_to IS NULL OR effective_to >= effective_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Dates on which a doctor is unavailable (leave, conference, blocked)
CREATE TABLE doctor_schedule_exceptions (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    doctor_id   BIGINT       NOT NULL,
    date_from   DATE         NOT NULL,
    date_to     DATE         NOT NULL,
    reason      VARCHAR(200) NOT NULL,
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY ix_sched_exc_doctor_dates (doctor_id, date_from, date_to),
    CONSTRAINT fk_sched_exc_doctor FOREIGN KEY (doctor_id) REFERENCES doctors (id) ON DELETE CASCADE,
    CONSTRAINT ck_sched_exc_dates  CHECK (date_to >= date_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE attendance (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    employee_id   BIGINT       NOT NULL,
    work_date     DATE         NOT NULL,
    clock_in      DATETIME(3)  NULL,
    clock_out     DATETIME(3)  NULL,
    status        VARCHAR(20)  NOT NULL,             -- PRESENT, ABSENT, HALF_DAY, ON_LEAVE
    remarks       VARCHAR(255) NULL,
    recorded_by   BIGINT       NULL,
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_attendance_employee_day (employee_id, work_date),
    KEY ix_attendance_date (work_date, status),
    CONSTRAINT fk_attendance_employee FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE RESTRICT,
    CONSTRAINT fk_attendance_recorder FOREIGN KEY (recorded_by) REFERENCES users (id)     ON DELETE SET NULL,
    CONSTRAINT ck_attendance_status   CHECK (status IN ('PRESENT','ABSENT','HALF_DAY','ON_LEAVE')),
    CONSTRAINT ck_attendance_times    CHECK (clock_out IS NULL OR clock_in IS NULL OR clock_out > clock_in)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE leave_types (
    id                 BIGINT      NOT NULL AUTO_INCREMENT,
    code               VARCHAR(20) NOT NULL,          -- ANNUAL, CASUAL, SICK, MATERNITY, NO_PAY
    name               VARCHAR(60) NOT NULL,
    days_per_year      SMALLINT    NULL,              -- NULL = unlimited (e.g. NO_PAY)
    paid               BOOLEAN     NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_leave_types_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE leave_requests (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    employee_id     BIGINT        NOT NULL,
    leave_type_id   BIGINT        NOT NULL,
    start_date      DATE          NOT NULL,
    end_date        DATE          NOT NULL,
    days            DECIMAL(4,1)  NOT NULL,
    reason          VARCHAR(255)  NOT NULL,
    status          VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    decided_by      BIGINT        NULL,
    decided_at      DATETIME(3)   NULL,
    decision_note   VARCHAR(255)  NULL,
    created_at      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY ix_leave_employee_dates (employee_id, start_date, end_date),
    KEY ix_leave_status (status, start_date),
    CONSTRAINT fk_leave_employee FOREIGN KEY (employee_id)   REFERENCES employees (id)   ON DELETE RESTRICT,
    CONSTRAINT fk_leave_type     FOREIGN KEY (leave_type_id) REFERENCES leave_types (id) ON DELETE RESTRICT,
    CONSTRAINT fk_leave_decider  FOREIGN KEY (decided_by)    REFERENCES users (id)       ON DELETE SET NULL,
    CONSTRAINT ck_leave_dates    CHECK (end_date >= start_date),
    CONSTRAINT ck_leave_days     CHECK (days > 0),
    CONSTRAINT ck_leave_status   CHECK (status IN ('PENDING','APPROVED','REJECTED','CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
