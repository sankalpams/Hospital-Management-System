CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE SEQUENCE IF NOT EXISTS patient_mrn_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE departments (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    phone VARCHAR(20),
    location VARCHAR(100),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE doctors (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    department_id BIGINT NOT NULL REFERENCES departments(id),
    specialization VARCHAR(100) NOT NULL,
    qualification VARCHAR(200) NOT NULL,
    slmc_reg_number VARCHAR(50) NOT NULL UNIQUE,
    consultation_fee NUMERIC(10, 2) NOT NULL DEFAULT 2500.00,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX doctors_dept_idx ON doctors(department_id);
CREATE INDEX doctors_user_idx ON doctors(user_id);

CREATE TABLE employees (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id),
    department_id BIGINT REFERENCES departments(id),
    employee_number VARCHAR(50) NOT NULL UNIQUE,
    designation VARCHAR(100) NOT NULL,
    contact_phone VARCHAR(20),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX employees_dept_idx ON employees(department_id);
CREATE INDEX employees_user_idx ON employees(user_id);

CREATE TABLE patients (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mrn VARCHAR(32) NOT NULL UNIQUE,
    nic VARCHAR(20),
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    date_of_birth DATE NOT NULL,
    sex VARCHAR(10) NOT NULL CHECK (sex IN ('MALE', 'FEMALE', 'OTHER')),
    phone VARCHAR(20),
    email VARCHAR(255),
    address_line1 VARCHAR(255),
    address_line2 VARCHAR(255),
    city VARCHAR(100),
    district VARCHAR(50) NOT NULL,
    guardian_name VARCHAR(100),
    guardian_relationship VARCHAR(50),
    guardian_phone VARCHAR(20),
    blood_group VARCHAR(20),
    chronic_conditions TEXT,
    insurance_provider VARCHAR(100),
    insurance_policy_number VARCHAR(100),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    merged_into_id UUID REFERENCES patients(id),
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT patients_phone_lk CHECK (phone IS NULL OR phone ~ '^\\+94[0-9]{9}$')
);

CREATE INDEX patients_mrn_idx ON patients(mrn);
CREATE INDEX patients_nic_idx ON patients(nic) WHERE nic IS NOT NULL AND deleted_at IS NULL;
CREATE INDEX patients_phone_idx ON patients(phone) WHERE phone IS NOT NULL AND deleted_at IS NULL;
CREATE INDEX patients_dob_idx ON patients(date_of_birth) WHERE deleted_at IS NULL;
CREATE INDEX patients_name_trgm_idx ON patients USING gin ((first_name || ' ' || last_name) gin_trgm_ops);

CREATE TABLE patient_allergies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id UUID NOT NULL REFERENCES patients(id),
    allergen VARCHAR(100) NOT NULL,
    reaction VARCHAR(255) NOT NULL,
    severity VARCHAR(20) NOT NULL CHECK (severity IN ('MILD', 'MODERATE', 'SEVERE')),
    notes VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX patient_allergies_patient_idx ON patient_allergies(patient_id);

CREATE TABLE patient_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id UUID NOT NULL REFERENCES patients(id),
    file_name VARCHAR(255) NOT NULL,
    stored_path VARCHAR(500) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    category VARCHAR(50) NOT NULL CHECK (category IN ('LAB_REPORT', 'SCAN', 'REFERRAL', 'OTHER')),
    notes VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX patient_documents_patient_idx ON patient_documents(patient_id);

CREATE TABLE consents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id UUID NOT NULL REFERENCES patients(id),
    purpose VARCHAR(255) NOT NULL,
    given_date DATE NOT NULL,
    withdrawn_date DATE,
    witness_name VARCHAR(100),
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX consents_patient_idx ON consents(patient_id);

