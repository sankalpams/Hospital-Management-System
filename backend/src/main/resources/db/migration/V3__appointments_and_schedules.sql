-- Phase 3: Appointments, Schedules, Queue, and Reminders Schema

-- 1. Sequence for Appointment Numbers (e.g. APT-20261031-0001)
CREATE SEQUENCE IF NOT EXISTS appointment_number_seq START WITH 1 INCREMENT BY 1;

-- 2. Doctor Weekly Schedule Templates
CREATE TABLE doctor_schedules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    doctor_id UUID NOT NULL REFERENCES doctors(id) ON DELETE CASCADE,
    day_of_week VARCHAR(10) NOT NULL CHECK (day_of_week IN ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY')),
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    slot_duration_minutes INT NOT NULL CHECK (slot_duration_minutes > 0),
    max_patients INT NOT NULL CHECK (max_patients > 0),
    room_number VARCHAR(50),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_doc_schedule_time CHECK (start_time < end_time)
);
CREATE INDEX idx_doctor_schedules_doctor ON doctor_schedules(doctor_id, day_of_week, active);

-- 3. Doctor Schedule Exceptions (Leave, Blocked Slots, Extra Sessions)
CREATE TABLE schedule_exceptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    doctor_id UUID NOT NULL REFERENCES doctors(id) ON DELETE CASCADE,
    exception_type VARCHAR(20) NOT NULL CHECK (exception_type IN ('LEAVE', 'BLOCKED_SLOT', 'EXTRA_SESSION')),
    start_date_time TIMESTAMPTZ NOT NULL,
    end_date_time TIMESTAMPTZ NOT NULL,
    reason VARCHAR(255),
    slot_duration_minutes INT,
    max_patients INT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_schedule_exception_dates CHECK (start_date_time < end_date_time)
);
CREATE INDEX idx_schedule_exceptions_doctor ON schedule_exceptions(doctor_id, start_date_time, end_date_time);

-- 4. Appointments Table
CREATE TABLE appointments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    appointment_number VARCHAR(32) NOT NULL UNIQUE,
    doctor_id UUID NOT NULL REFERENCES doctors(id),
    patient_id UUID NOT NULL REFERENCES patients(id),
    department_id UUID NOT NULL REFERENCES departments(id),
    appointment_date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    token_number INT NOT NULL CHECK (token_number > 0),
    status VARCHAR(30) NOT NULL CHECK (status IN (
        'SCHEDULED', 'CONFIRMED', 'CHECKED_IN', 'IN_CONSULTATION',
        'COMPLETED', 'CANCELLED', 'NO_SHOW', 'RESCHEDULED'
    )),
    type VARCHAR(20) NOT NULL DEFAULT 'STANDARD' CHECK (type IN (
        'STANDARD', 'WALK_IN', 'FOLLOW_UP', 'EMERGENCY'
    )),
    reason VARCHAR(500),
    cancellation_reason VARCHAR(500),
    rescheduled_from_id UUID REFERENCES appointments(id),
    consultation_start_time TIMESTAMPTZ,
    consultation_end_time TIMESTAMPTZ,
    reminder_sent_at TIMESTAMPTZ,
    reminder_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (reminder_status IN ('PENDING', 'SENT', 'FAILED', 'SKIPPED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_appointment_time CHECK (start_time < end_time)
);

-- Layer 1 Double Booking Prevention: DB Partial Unique Index
CREATE UNIQUE INDEX idx_appointments_no_double_booking
ON appointments (doctor_id, appointment_date, start_time)
WHERE status NOT IN ('CANCELLED', 'RESCHEDULED');

-- Query performance indexes
CREATE INDEX idx_appointments_doctor_date ON appointments(doctor_id, appointment_date);
CREATE INDEX idx_appointments_patient_date ON appointments(patient_id, appointment_date DESC);
CREATE INDEX idx_appointments_dept_date ON appointments(department_id, appointment_date);
CREATE INDEX idx_appointments_date_status ON appointments(appointment_date, status);
CREATE INDEX idx_appointments_token_order ON appointments(doctor_id, appointment_date, token_number);

-- 5. Appointment Status History Audit Log
CREATE TABLE appointment_status_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    appointment_id UUID NOT NULL REFERENCES appointments(id) ON DELETE CASCADE,
    from_status VARCHAR(30),
    to_status VARCHAR(30) NOT NULL,
    reason VARCHAR(500),
    changed_by VARCHAR(100) NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_appointment_status_history_apt ON appointment_status_history(appointment_id, changed_at DESC);

