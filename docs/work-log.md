# Hospital Management System (HMS) — Project Implementation Log

This document serves as the single source of truth for the engineering work completed across all phases of the Hospital Management System (HMS). It records architectural decisions, database schemas, API contracts, security rules, frontend implementations, and verification results.

---

## 1. Project Overview & Architectural Standards

### 1.1 Core Stack
- **Backend**: Java 21, Spring Boot 3.4.2, Maven, Spring Web, Spring Data JPA, Spring Security (Stateless JWT), Spring Validation, PostgreSQL 16, Flyway migrations, MapStruct, Lombok, springdoc-openapi, Spring Mail, OpenPDF, JUnit 5, Mockito, Testcontainers.
- **Frontend**: React 18, TypeScript, Vite, React Router v6, TanStack Query v5, TanStack Table v8, React Hook Form, Zod, Tailwind CSS, Radix UI Dialog, Lucide React, date-fns, Axios.
- **Infrastructure**: Docker & Docker Compose (PostgreSQL 16, Spring Boot backend, Nginx frontend, MailHog SMTP server), GitHub Actions CI.

### 1.2 Non-Negotiable Architecture Rules
- **Monorepo Structure**: `/backend`, `/frontend`, `/docker`, `/docs`.
- **Package-by-Feature Layering**: `controller -> service -> repository`. Entities remain confined to the service layer; DTOs and records are used at all controller boundaries via MapStruct.
- **Uniform Error Envelope**: Standardized JSON structure across all `/api/v1` routes:
  ```json
  {
    "timestamp": "2026-10-31T00:00:00Z",
    "status": 400,
    "code": "VALIDATION_ERROR",
    "message": "Please correct the highlighted fields.",
    "fieldErrors": [
      { "field": "phone", "message": "Use a Sri Lankan phone number in +94XXXXXXXXX format" }
    ],
    "path": "/api/v1/users"
  }
  ```
- **Data & Audit Integrity**:
  - All entities extend `BaseEntity` with `created_at`, `updated_at`, `created_by`, `updated_by`, and `@Version` for optimistic concurrency locking.
  - Soft deletes for sensitive clinical and financial records.
  - Database stores all timestamps in **UTC**; the presentation layer formats for **Asia/Colombo** (`UTC+05:30`).
  - Monetary values stored and rendered in **Sri Lankan Rupees (LKR)** using tabular numerals.
  - PII (NIC, phone numbers, home addresses, tokens, passwords) strictly redacted from application logs and audit records.
- **Role-Based Access Control (RBAC)**:
  - 7 Roles: `ADMIN`, `DOCTOR`, `NURSE`, `RECEPTIONIST`, `LAB_STAFF`, `PHARMACIST`, `ACCOUNTANT`.
  - Permissions stored in DB as string constants, enforced with method-level `@PreAuthorize` and mirrored in frontend navigation and route guards.
  - Clinical boundary: Receptionists are strictly forbidden from viewing diagnoses, EMR clinical notes, or vitals.

---

## 2. Phase Implementation Log

| Phase | Title | Status | Completion Date | Scope Summary |
|---|---|---|---|---|
| **Phase 1** | Foundation, Auth, Permissions, Audit & App Shell | **Completed** | 2026-10-31 | Monorepo scaffold, Docker Compose, DB migrations, JWT auth with refresh rotation, lockout, rate limiting, append-only audit aspect, user management, design tokens, app shell, 9 reusable UI components, and unit/integration tests. |
| **Phase 2** | Patients, Departments, Doctors & Staff Basics | **Completed** | 2026-10-31 | Departments CRUD, Doctors with SLMC credentials and consultation fees, Employees with designation/department, sequential MRN generation (`MRN-YYYY-NNNNNN`), Sri Lankan NIC/phone validation, fuzzy duplicate detection & Admin merge engine, structured allergies, categorized document management with external storage and virus scanner hook, consents, audit on every patient view, reception clinical hiding, and dev data seeder. |
| **Phase 3** | Appointments & Scheduling | *Pending* | — | Doctor scheduling, slot booking, double-booking prevention, calendar views. |
| **Phase 4** | Clinical EMR & Consultations | *Pending* | — | Clinical notes, vitals tracking, diagnosis coding, patient clinical banner. |
| **Phase 5** | Laboratory & Diagnostics | *Pending* | — | Specimen collection, test ordering, lab result entry and validation. |
| **Phase 6** | Pharmacy & Dispensing | *Pending* | — | Prescription fulfillment, inventory tracking, dispensing logs. |
| **Phase 7** | Billing, Invoicing & Payments | *Pending* | — | LKR invoices, receipts, payment recording, voiding with accountant permissions. |
| **Phase 8** | Reporting, Analytics & Hardening | *Pending* | — | OpenPDF export, operational dashboards, end-to-end audit review. |

---

## 3. Phase 1 Detailed Deliverables

### 3.1 Infrastructure & Deployment
- **Docker Compose (`docker-compose.yml`)**:
  - `postgres`: PostgreSQL 16 Alpine with custom DB healthcheck (`pg_isready`).
  - `backend`: Multi-stage build (Maven 3.9 + Eclipse Temurin 21) exposing port `8081` with actuator health check.
  - `frontend`: Multi-stage build (Node 22 + Nginx 1.27 Alpine) on port `8080`, proxying `/api/` and `/actuator/` requests to the backend.
  - `mailhog`: MailHog SMTP server on port `1025` with web inspection UI on port `8025`.
- **Environment Configuration**:
  - `.env.example` defining database credentials, JWT base64 secret, CORS origin, and mail settings.
- **CI Pipeline (`.github/workflows/ci.yml`)**:
  - Backend job: Ubuntu runner, Eclipse Temurin 21, Maven dependency caching, `mvn -B verify`.
  - Frontend job: Ubuntu runner, Node 22, npm caching, `npm run lint`, `npm run test -- --run`, `npm run build`.

### 3.2 Database & Migrations
- **Flyway Baseline (`V1__foundation_schema.sql`)**:
  - `roles`: `id`, `code` (unique), `name`, `description`, audit columns, `version`.
  - `permissions`: `id`, `code` (unique), `description`, audit columns, `version`.
  - `role_permissions`: Composite PK `(role_id, permission_id)`.
  - `users`: `id` (UUID), `username`, `email`, `password_hash`, `first_name`, `last_name`, `phone` (`+94` regex check), `role_id`, `active`, `force_password_change`, `failed_login_attempts`, `locked_until`, `last_login_at`, `deleted_at`, audit columns.
  - `refresh_tokens`: `token_hash` (SHA-256), `user_id`, `expires_at`, `revoked_at`, `replaced_by_id`.
  - `password_reset_tokens`: `token_hash` (SHA-256), `user_id`, `expires_at`, `used_at`.
  - `login_history`: `user_id`, `username_attempted`, `outcome` (`SUCCESS`, `FAILURE`, `LOCKED`, `RATE_LIMITED`, `LOGOUT`), `ip_address`, `user_agent`, `occurred_at`.
  - `audit_logs`: `user_id`, `role_code`, `action` (`VIEW`, `CREATE`, `UPDATE`, `DELETE`, `LOGIN`, `EXPORT`), `entity_type`, `entity_id`, `before_json` (JSONB), `after_json` (JSONB), `ip_address`, `occurred_at`.
  - **Append-Only Trigger**: PostgreSQL trigger `audit_logs_immutable` invoking `prevent_audit_log_mutation()` to raise an exception on any `UPDATE` or `DELETE` statement against `audit_logs`.

### 3.3 Security & Authentication Engine
- **Stateless JWT Tokens**:
  - Access Token: 15-minute expiration, contains `userId`, `username`, `role`, and permissions array.
  - HMAC-SHA256 signing using configured 256-bit secret key.
  - `JwtAuthenticationFilter` intercepts requests, validates the signature, and loads an authenticated `HmsPrincipal` into Spring's `SecurityContextHolder`.
- **Rotating Refresh Tokens**:
  - 7-day lifetime. Tokens are hashed with SHA-256 before storage in the database.
  - When refreshed, the existing token is marked `revoked_at = now()`, points to `replaced_by_id`, and a new pair is issued.
- **Brute-Force Protection & Lockout**:
  - `LoginSecurityService` records failed sign-in attempts in an independent database transaction (`Propagation.REQUIRES_NEW`).
  - Upon reaching 5 consecutive failures, the account is automatically locked for 15 minutes (`locked_until`).
  - Successful authentication resets `failedLoginAttempts` to `0` and clears `lockedUntil`.
- **Sliding-Window Rate Limiting**:
  - `LoginRateLimiter` enforces a sliding 60-second window limiting any single IP to 10 authentication requests.
- **Password Policy**:
  - Minimum 12 characters, requiring uppercase, lowercase, digit, and special symbol. Enforced on account creation, admin reset, password change, and token-based reset.
  - `forcePasswordChange` flag set on initial user provisioning, routing user to the change-password flow before application access is granted.
- **Password Reset Flow**:
  - `POST /api/v1/auth/forgot-password` generates a single-use token and dispatches an email via Spring Mail / MailHog.
  - `POST /api/v1/auth/reset-password` validates token validity and expiry, applies new password, clears lockout counters, and revokes all active sessions.

### 3.4 Append-Only Audit Framework
- **Reusable `@Audited` Aspect**:
  - Declared on service methods: `@Audited(action = AuditAction.CREATE, entityType = "User")`.
  - `AuditAspect` runs around method execution, serializing incoming arguments and return values.
  - **Sensitive PII Sanitizer**: Automatically traverses JSON trees and redacts keys containing `password`, `phone`, `nic`, `address`, and `token` to `[REDACTED]`.
  - Extracts entity ID via reflection or configured argument index.
  - Persists audit entries in a new transaction (`Propagation.REQUIRES_NEW`) to guarantee capture even if subsequent non-critical work fails.
- **Audit Search API**:
  - `GET /api/v1/audit-logs` accessible only to `AUDIT_READ` (`ADMIN`).
  - Filters by search query `q` (entity type or ID), `action`, `from`, `to`, with pagination and sorting.

### 3.5 User Management (Admin Only)
- `GET /api/v1/users`: Paginated, sorted list with full-text search `q`, role filter, and active/inactive filter.
- `POST /api/v1/users`: Create user with temporary password, validation of Sri Lankan phone format (`+94XXXXXXXXX`), and duplicate email/username conflict checks.
- `GET /api/v1/users/{id}`: Retrieve detailed user record.
- `PUT /api/v1/users/{id}`: Update user metadata, role, or active status.
- `POST /api/v1/users/{id}/deactivate`: Deactivate user (guarded against self-deactivation).
- `DELETE /api/v1/users/{id}`: Soft delete user (`deleted_at` timestamp, guarded against self-deletion).
- `POST /api/v1/users/{id}/reset-password`: Administrator-initiated password reset email.
- `PUT /api/v1/users/me`: Current user profile self-update.
- `GET /api/v1/users/{id}/login-history`: Paginated sign-in audit history for the user.

### 3.6 Frontend Application & Clinical Design System
- **Design System Tokens (`/docs/design-system.md`)**:
  - Palette: Deep teal primary (`#006064` / `#00838f`), warm neutral surfaces, semantic tones (emerald success, amber warning, red danger).
  - High-contrast true dark mode using layered charcoal tones (`#121417`, `#1a1d21`, `#24282f`).
  - 4/8px spacing grid, humanist sans typography, tabular numerals for monetary figures and clinical values.
- **Application Shell (`AppShell.tsx`)**:
  - Collapsible sidebar with navigation items dynamically filtered by user permissions.
  - Top bar with global search shortcut (`/` key), dark/light theme switch, user profile link, role badge, and logout.
  - Keyboard shortcut: `N` triggers new user creation when permitted.
  - **Idle Timeout**: 15-minute inactivity tracker monitoring keyboard, pointer, and touch events; presents a 1-minute warning modal before terminating the session.
- **Reusable Clinical Components**:
  1. `DataTable`: TanStack Table with sticky header, compact rows, and contextual empty state.
  2. `PageHeader`: Structured page title, subtitle description, and contextual action buttons.
  3. `PatientBanner`: Pinned clinical banner displaying patient name, age/sex, MRN, and bold red allergy tags.
  4. `StatusBadge`: Semantic color badges for account states and audit actions.
  5. `ConfirmDialog`: Accessible confirmation dialog with loading states for destructive operations.
  6. `FormField`: Label, child input, and localized validation error message container.
  7. `EmptyState`: Humanized microcopy with icon and optional call-to-action button.
  8. `MoneyText`: LKR currency formatting with tabular figures.
  9. `DateTimeText`: Formatted Asia/Colombo local timestamps with underlying ISO `<time>` elements.
- **Pages Delivered**:
  - `LoginPage`: Form with validation and error alerts.
  - `ForgotPasswordPage`: Email dispatch triggering MailHog notification.
  - `ResetPasswordPage`: Token-driven password reset with complexity checklist.
  - `ChangePasswordPage`: Forced or voluntary password rotation.
  - `ProfilePage`: Self-service contact information update.
  - `UsersPage`: User table with search, role filtering, pagination, and modal create/edit forms.
  - `AuditLogPage`: Audit record browser with action filters and detail views.
  - `RolesPage`: Interactive read-only 7-role permission matrix.
  - `HomePage`: Role-specific workspace cards with realistic clinical microcopy for all 7 hospital roles.

### 3.7 Deterministic Development Seeder
- Executed on startup when `dev` profile is active:
  - Seeds all 21 permission records and associates them with the 7 roles according to the matrix in [`permissions.md`](file:///d:/Projects/HMS/docs/permissions.md).
  - Creates 7 test accounts (all with password `Welcome@12345` and `forcePasswordChange = false`):
    - `admin` (Admin)
    - `doctor` (Doctor)
    - `nurse` (Nurse)
    - `reception` (Receptionist)
    - `lab` (Lab Staff)
    - `pharmacy` (Pharmacist)
    - `accounts` (Accountant)

---

## 4. Test Verification Suite

### 4.1 Backend Test Suite (JUnit 5 + Mockito + Testcontainers)
- [`AuthServiceTest`](file:///d:/Projects/HMS/backend/src/test/java/com/hms/auth/AuthServiceTest.java):
  - Valid sign-in returns JWT access token and refresh token.
  - Invalid password triggers failure recording and throws `BadCredentialsException`.
  - Locked account returns `ACCOUNT_LOCKED` error.
  - Rate-limited IP returns `RATE_LIMITED` error.
  - Refresh token rotation invalidates previous token and stores new replacement relationship.
  - Change password verifies old password, validates complexity, updates hash, and revokes prior tokens.
  - Forgot password issues reset token and sends mail.
  - Reset password updates hash, clears lockout, and invalidates active tokens.
- [`LoginSecurityServiceTest`](file:///d:/Projects/HMS/backend/src/test/java/com/hms/auth/LoginSecurityServiceTest.java):
  - Counter increments upon each failed attempt.
  - 5th failure activates account lockout with future timestamp.
- [`PasswordPolicyTest`](file:///d:/Projects/HMS/backend/src/test/java/com/hms/auth/PasswordPolicyTest.java):
  - Validates lengths >= 12 and required character classes.
  - Rejects missing uppercase, lowercase, numbers, or symbols.
- [`AuditAspectTest`](file:///d:/Projects/HMS/backend/src/test/java/com/hms/audit/AuditAspectTest.java):
  - Intercepts annotated methods and captures before/after JSON states.
  - Proves redaction of `password`, `phone`, `nicNumber`, `homeAddress`, and `refreshToken`.
- [`UserServiceTest`](file:///d:/Projects/HMS/backend/src/test/java/com/hms/user/UserServiceTest.java):
  - User creation with Sri Lankan phone validation.
  - Rejection of duplicate usernames and emails.
  - User deactivation and soft delete.
  - Prevents administrator self-deactivation and self-deletion.
- [`RolePermissionSecurityTest`](file:///d:/Projects/HMS/backend/src/test/java/com/hms/security/RolePermissionSecurityTest.java):
  - Verifies matrix integrity for all 7 roles.
  - Proves `RECEPTIONIST` has no access to `EMR_READ`, `EMR_WRITE`, or `VITALS_WRITE`.
  - Proves `NURSE` has `EMR_READ` and `VITALS_WRITE` but cannot write medical diagnoses (`EMR_WRITE`).
  - Proves only `ADMIN` has `USER_MANAGE` and `AUDIT_READ`.
- [`HmsApplicationTests`](file:///d:/Projects/HMS/backend/src/test/java/com/hms/HmsApplicationTests.java):
  - Testcontainers integration test with PostgreSQL 16 verifying Flyway schema execution and application context initialization.

### 4.2 Frontend Test Suite (Vitest + React Testing Library)
- [`components.test.tsx`](file:///d:/Projects/HMS/frontend/src/components/components.test.tsx):
  - `StatusBadge`: semantic color states for boolean and string statuses.
  - `MoneyText`: LKR currency formatting with tabular numerals.
  - `DateTimeText`: ISO timestamp rendering in `<time>` element.
  - `PatientBanner`: demographics display and red-highlighted allergy indicators.
  - `EmptyState`: rendering title, subtext, and call-to-action buttons.
  - `DataTable`: data display vs. empty placeholder.
  - `FormField`: label association and error text display.
  - `ConfirmDialog`: accessible modal, confirm callback, cancel dismissal.
- [`guards.test.tsx`](file:///d:/Projects/HMS/frontend/src/components/guards.test.tsx):
  - `RequireAuth`: unauthenticated redirect to `/login`; authenticated pass-through.
  - `RequirePermission`: authorized access granted; unauthorized redirected to `/forbidden`.
  - `RequirePasswordChange`: redirected to `/change-password` when `forcePasswordChange` is active.
- [`utils.test.ts`](file:///d:/Projects/HMS/frontend/src/lib/utils.test.ts):
  - Formatter verification for LKR and local timestamps.

**Total Frontend Tests: 15 passing across 3 test suites. TypeScript compilation and production build verified.**

---

## 5. API Reference Summary (Phase 1)

### Authentication (`/api/v1/auth`)
| Method | Endpoint | Description | Access |
|---|---|---|---|
| `POST` | `/login` | Authenticate with username/email and password | Public (rate limited) |
| `POST` | `/refresh` | Rotate refresh token and get fresh JWT access token | Public |
| `POST` | `/logout` | Revoke active refresh token | Public |
| `POST` | `/forgot-password` | Request password reset email | Public |
| `POST` | `/reset-password` | Set new password with email token | Public |
| `POST` | `/change-password` | Update password for authenticated user | Authenticated |
| `GET` | `/me` | Get current authenticated user profile | Authenticated |

### Users (`/api/v1/users`)
| Method | Endpoint | Description | Access |
|---|---|---|---|
| `GET` | `/` | Paginated search of staff accounts | `USER_READ` |
| `POST` | `/` | Create a new staff account | `USER_MANAGE` |
| `GET` | `/{id}` | Get staff account details | `USER_READ` |
| `PUT` | `/{id}` | Update staff account details | `USER_MANAGE` |
| `POST` | `/{id}/deactivate` | Deactivate staff account | `USER_MANAGE` |
| `DELETE` | `/{id}` | Soft delete staff account | `USER_MANAGE` |
| `POST` | `/{id}/reset-password` | Dispatch admin password reset link | `USER_MANAGE` |
| `PUT` | `/me` | Update personal profile details | Authenticated |
| `GET` | `/{id}/login-history` | View user sign-in history | `USER_READ` |

### Audit & Roles (`/api/v1`)
| Method | Endpoint | Description | Access |
|---|---|---|---|
| `GET` | `/audit-logs` | Filter and search append-only audit records | `AUDIT_READ` |
| `GET` | `/roles` | Read 7-role permission matrix | `ROLE_READ` |

---

## 6. Phase 2 Detailed Deliverables: Patients, Departments, Doctors & Staff Basics

### 6.1 Database Schema & Flyway Migration (`V2__patients_departments_doctors.sql`)
- **PostgreSQL Extension**:
  - `CREATE EXTENSION IF NOT EXISTS pg_trgm;` for trigram-based fuzzy patient and staff search.
- **Departments (`departments`)**:
  - `id` (UUID PK), `code` (unique, e.g. `CARDIO`, `PEDIATRICS`), `name`, `description`, `active`, audit columns (`created_at`, `updated_at`, `created_by`, `updated_by`, `version`).
- **Doctors (`doctors`)**:
  - `id` (UUID PK), `user_id` (FK `users`, unique), `department_id` (FK `departments`), `slmc_number` (unique, Sri Lanka Medical Council registration number), `specialization`, `qualification`, `consultation_fee` (numeric 12,2 in LKR), `active`, audit columns.
- **Employees (`employees`)**:
  - `id` (UUID PK), `user_id` (FK `users`, unique), `department_id` (FK `departments`), `employee_number` (unique, e.g. `EMP-00101`), `designation`, `contact_phone` (`+94` check), `active`, audit columns.
- **Patients (`patients`)**:
  - `id` (UUID PK), `mrn` (unique, format `MRN-YYYY-NNNNNN`), `nic` (nullable, format validation for 9-digit+V/X or 12-digit), `passport_number`, `first_name`, `last_name`, `date_of_birth`, `sex` (`MALE`, `FEMALE`, `OTHER`), `phone` (`+94` format check), `email`, `address_line1`, `address_line2`, `city`, `district` (Sri Lankan administrative district), `emergency_contact_name`, `emergency_contact_relation`, `emergency_contact_phone`, `blood_group` (`A+`, `A-`, `B+`, `B-`, `AB+`, `AB-`, `O+`, `O-`), `chronic_conditions`, `insurance_provider`, `insurance_policy_number`, `active`, `merged_into_id` (FK `patients`, self-referencing), `merged_at`, audit columns.
  - **Sequence**: Dedicated PostgreSQL sequence `patient_mrn_seq` cycling per year.
  - **Trigram & Multi-Column Indexes**:
    - `idx_patients_mrn` ON `patients(mrn)`
    - `idx_patients_nic` ON `patients(nic)`
    - `idx_patients_phone` ON `patients(phone)`
    - `idx_patients_name_trgm` USING `gin((first_name || ' ' || last_name) gin_trgm_ops)`
    - `idx_patients_dob` ON `patients(date_of_birth)`
- **Patient Allergies (`patient_allergies`)**:
  - `id` (UUID PK), `patient_id` (FK `patients` CASCADE), `allergen`, `reaction`, `severity` (`MILD`, `MODERATE`, `SEVERE`), `notes`, audit columns.
- **Patient Documents (`patient_documents`)**:
  - `id` (UUID PK), `patient_id` (FK `patients` CASCADE), `file_name`, `stored_file_name`, `content_type`, `file_size_bytes` (CHECK `<= 10485760` / 10MB), `category` (`LAB_REPORT`, `SCAN`, `REFERRAL`, `DISCHARGE_SUMMARY`, `OTHER`), `description`, audit columns.
- **Consents (`consents`)**:
  - `id` (UUID PK), `patient_id` (FK `patients` CASCADE), `purpose`, `given_date`, `withdrawn_date`, `witness_name`, audit columns.

### 6.2 Master Patient Index (MPI), Standards & Validation
- **MRN Generation**:
  - Thread-safe, non-blocking sequence generation using Spring's `JdbcTemplate` querying `patient_mrn_seq`.
  - Format: `MRN-{YEAR}-{000000}` (e.g. `MRN-2026-000123`).
- **Sri Lankan NIC Validation**:
  - Old NIC: 9 digits followed by `V` or `X` (e.g., `852341234V`).
  - New NIC: 12 numeric digits (e.g., `199512345678`).
- **Sri Lankan Phone Validation**:
  - Regex `^\+94\d{9}$` ensuring international format starting with `+94`.
- **Fuzzy Duplicate Detection**:
  - `PatientDuplicateService` detects possible duplicate entries before patient creation:
    1. Exact match on NIC / Passport.
    2. Fuzzy match combining exact Date of Birth with exact Phone number or matching Family Name.
  - Registration returns duplicates in a warning envelope (`DuplicateMatch`), allowing front-desk staff to review before confirming.
- **Admin Merge Engine**:
  - `PatientService.mergePatients(targetId, sourceId)` is restricted to `ADMIN` (`USER_MANAGE`).
  - Idempotent and audit-logged:
    - Transfers all allergies, documents, and consents from `source` to `target`.
    - Updates source record: `merged_into_id = target.getId()`, `merged_at = Instant.now()`, `active = false`.
    - Logs a structured `UPDATE` audit log with details of the merge.

### 6.3 Document Management & Virus Scanning Pipeline
- **Storage Isolation**:
  - Documents are stored in an external file directory (`app.storage.upload-dir`) outside the web root.
  - File names are sanitized with random UUID prefixes to prevent directory traversal or collision.
- **Whitelist Enforcement**:
  - Allowed types: `application/pdf`, `image/jpeg`, `image/png`, `image/webp`.
  - Size limit: 10 MB per file.
- **Pluggable Virus Scanner**:
  - `VirusScanner` interface with `scan(InputStream, filename, size)` method.
  - `NoOpVirusScanner` provides a safe default implementation for dev and testing, easily replaced with ClamAV or external scanner in production.

### 6.4 Clinical Audit & Receptionist Privacy Boundary
- **Mandatory View Audit**:
  - Every individual patient chart view (`GET /api/v1/patients/{id}`) writes a `VIEW` action to `audit_logs` capturing `user_id`, `role`, `patient_id`, and client IP.
- **Strict Receptionist Isolation**:
  - Demographics endpoints (`/api/v1/patients`, `/api/v1/patients/{id}`) require `PATIENT_READ`.
  - Clinical endpoints (`/api/v1/patients/{id}/allergies`, medical notes, labs, prescriptions) require `EMR_READ` or `EMR_WRITE`.
  - In the frontend:
    - Receptionist accounts see Demographics, Overview, Documents, and Consents.
    - Clinical tabs (`Medical History & Allergies`, `Lab & Diagnostics`, `Prescriptions`) are hidden when `canViewClinical = has('EMR_READ')` is false.
    - Direct API requests to clinical endpoints from receptionists return HTTP 403 Forbidden.

### 6.5 Patient Data Export
- **JSON & PDF Export**:
  - Admin-only action (`USER_MANAGE`) available via `GET /api/v1/patients/{id}/export?format=json|pdf`.
  - **JSON**: Complete record including demographics, emergency contacts, structured allergies, documents list, and consents.
  - **PDF**: Standalone PDF 1.4 stream generated using pure Java streams without third-party native dependencies, formatted with hospital headers, MRN banner, demographic grid, allergy tables, and consent signatures.
  - Every export is logged in `audit_logs` with action `EXPORT`.

### 6.6 Staff Management: Departments, Doctors & Employees
- **Departments**: Full CRUD (`/api/v1/departments`) with active status toggle, department code uniqueness, and doctor count aggregation.
- **Doctors**: Full CRUD (`/api/v1/doctors`) linked to Spring Security user accounts, department foreign keys, SLMC registration numbers, qualifications, and consultation fees formatted in LKR.
- **Employees**: Full CRUD (`/api/v1/employees`) linked to user accounts and departments, storing employee numbers and designations.

### 6.7 Dev Profile Seeding (`DevDataSeeder`)
- Automatically seeds rich clinical and operational data when running with `dev` profile:
  - **8 Departments**: Cardiology, General Medicine, Pediatrics, Orthopedics, General Surgery, Obstetrics & Gynecology, Dermatology, Radiology.
  - **12 Doctors**: Specialists mapped across departments with valid SLMC numbers and LKR consultation fees (e.g. Dr. Nihal Senaratne, Dr. Preethi Jayawardena).
  - **10 Employees / Nurses**: Staff members linked to roles and departments with `EMP-` identifiers.
  - **505 Realistic Patients**:
    - Sri Lankan names, valid old and new NIC formats, `+94` phone numbers, diverse districts (Colombo, Kandy, Galle, Gampaha, Kurunegala, Jaffna, Ampara, Matara).
    - Structured allergies across Mild, Moderate, and Severe categories (Penicillin, Sulfa, NSAIDs, Peanuts, Latex, Contrast Media).
    - Chronic conditions: Type 2 Diabetes, Hypertension, Asthma, Ischemic Heart Disease, Chronic Kidney Disease.
    - **5 Intentional Duplicate Pairs**: Seeded pairs (exact NICs or fuzzy name/DOB/phone matches) to test front-desk duplicate warnings and merge flows.

### 6.8 Phase 2 Test Verification
- **MRN Generation Concurrency Test (`MrnGeneratorServiceTest`)**:
  - Concurrency test with 50 threads generating 200 sequential MRNs simultaneously, proving thread-safety and zero collision.
- **Duplicate Detection Test (`PatientDuplicateServiceTest`)**:
  - Tests exact NIC match detection and fuzzy DOB + phone / name match detection.
- **Patient Service & Audit Test (`PatientServiceTest`)**:
  - Tests patient creation, view audit log generation, merge logic, and self-merge guard.
- **Document Storage & Whitelist Test (`PatientDocumentServiceTest`)**:
  - Tests valid PDF uploads, extension whitelist rejection (`.exe` rejected), file size limit rejection (>10MB rejected), and virus scanner rejection.
- **Security & Receptionist Barrier Test (`ReceptionistRestrictionSecurityTest`)**:
  - Tests that receptionist users without `EMR_READ` cannot access clinical allergies or medical records.
- **Search Performance Benchmark (`PatientSearchPerformanceTest`)**:
  - Benchmarks search performance against 100k synthetic patients, verifying p95 latency is well below 300 ms.
- **Frontend Vitest Suite (`PatientsPage.test.tsx`, `guards.test.tsx`, `components.test.tsx`, `utils.test.ts`)**:
  - 17/17 tests passing, confirming UI rendering, patient clinical banner, and receptionist clinical tab hiding.

---

## 7. API Reference Summary (Phase 2 Additions)

### Departments (`/api/v1/departments`)
| Method | Endpoint | Description | Access |
|---|---|---|---|
| `GET` | `/` | List all departments | Authenticated |
| `POST` | `/` | Create a new department | `USER_MANAGE` |
| `GET` | `/{id}` | Get department details | Authenticated |
| `PUT` | `/{id}` | Update department details | `USER_MANAGE` |
| `DELETE` | `/{id}` | Deactivate department | `USER_MANAGE` |

### Doctors (`/api/v1/doctors`)
| Method | Endpoint | Description | Access |
|---|---|---|---|
| `GET` | `/` | Paginated list of doctors (filter by department/specialization) | Authenticated |
| `POST` | `/` | Register doctor profile with SLMC number & fee | `USER_MANAGE` |
| `GET` | `/{id}` | Get doctor details | Authenticated |
| `PUT` | `/{id}` | Update doctor profile | `USER_MANAGE` |
| `DELETE` | `/{id}` | Deactivate doctor profile | `USER_MANAGE` |

### Employees (`/api/v1/employees`)
| Method | Endpoint | Description | Access |
|---|---|---|---|
| `GET` | `/` | Paginated list of staff members | `USER_READ` |
| `POST` | `/` | Register employee profile | `USER_MANAGE` |
| `GET` | `/{id}` | Get employee details | `USER_READ` |
| `PUT` | `/{id}` | Update employee profile | `USER_MANAGE` |
| `DELETE` | `/{id}` | Deactivate employee | `USER_MANAGE` |

### Patients (`/api/v1/patients`)
| Method | Endpoint | Description | Access |
|---|---|---|---|
| `GET` | `/` | Paginated search (MRN, NIC, name, phone, district, blood group) | `PATIENT_READ` |
| `GET` | `/quick-search` | Fast autocomplete search for topbar search | `PATIENT_READ` |
| `POST` | `/` | Register patient (with duplicate warning bypass support) | `PATIENT_WRITE` |
| `POST` | `/check-duplicates` | Pre-flight duplicate check against NIC, DOB, phone, name | `PATIENT_WRITE` |
| `GET` | `/{id}` | Get patient details (records `VIEW` audit log) | `PATIENT_READ` |
| `PUT` | `/{id}` | Update patient demographics | `PATIENT_WRITE` |
| `DELETE` | `/{id}` | Soft delete patient record | `USER_MANAGE` (Admin) |
| `POST` | `/{id}/merge` | Merge source patient into target patient | `USER_MANAGE` (Admin) |
| `GET` | `/{id}/export` | Export patient record to JSON or PDF | `USER_MANAGE` (Admin) |

### Allergies, Documents & Consents (`/api/v1/patients/{id}`)
| Method | Endpoint | Description | Access |
|---|---|---|---|
| `GET` | `/{id}/allergies` | List structured allergies | `EMR_READ` (Forbidden to Reception) |
| `POST` | `/{id}/allergies` | Add allergy (allergen, reaction, severity) | `EMR_WRITE` (Forbidden to Reception) |
| `DELETE` | `/{id}/allergies/{allergyId}` | Delete allergy record | `EMR_WRITE` (Forbidden to Reception) |
| `POST` | `/{id}/documents` | Upload medical document / report (max 10MB) | `PATIENT_WRITE` |
| `GET` | `/{id}/documents/{docId}/download`| Download document with authorized streaming | `PATIENT_READ` |
| `DELETE` | `/{id}/documents/{docId}` | Delete patient document | `PATIENT_WRITE` |
| `POST` | `/{id}/consents` | Record informed consent | `PATIENT_WRITE` |
| `POST` | `/{id}/consents/{consentId}/withdraw` | Withdraw consent | `PATIENT_WRITE` |


