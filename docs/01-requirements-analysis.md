# 01 — Requirements Analysis

Sources:

1. **Software Specification Document — Hospital Management System** (`Hospital_system.pdf`, 9 pages). It is an outline: module names, user roles, a layered architecture and backup goals, with no workflows, fields or business rules.
2. **Master prompt** (project brief). It fixes the technology stack, the colour palette, the role list and the module-level behaviour.

Where the two disagree, the master prompt wins: it is newer and more specific. The PDF is used as the coverage checklist in the traceability table below.

## 1. Traceability: PDF items → where they are delivered

| PDF section | Item | Delivered by | Phase |
|---|---|---|---|
| 3.1 User Management | Login, logout, password management, roles, access control | `auth`, `users` modules; Spring Security; permission matrix ([05](05-roles-permissions.md)) | 2–3 |
| 3.2 Patient Management | Register, update, search, medical history, document upload | `patients`, `documents` modules | 3 |
| 3.3 Doctor Management | Add, update, department, schedule | `doctors`, `doctor_schedules` | 3 |
| 3.4 Appointments | Book, cancel, reschedule, status tracking | `appointments` with state machine | 3 |
| 3.5 EMR | Diagnosis, prescriptions, treatment history, medical reports | `encounters`, `diagnoses`, `prescriptions`, `vital_signs`, `allergies` | 4 |
| 3.6 Laboratory | Requests, sample collection, result entry, reports | `lab_requests`, `lab_request_items`, `lab_results` | 4 |
| 3.7 Pharmacy | Inventory, prescription processing, stock, expiry | `medicines`, `medicine_batches`, `stock_transactions`, `dispensations` | 4 |
| 3.8 Billing | Consultation / lab / pharmacy / admission charges, invoices, payments | `charge_items`, `invoices`, `invoice_items`, `payments` | 4 |
| 3.9 Staff | Registration, attendance, department, leave | `employees`, `attendance`, `leave_requests` | 4 |
| 3.10 Reports | Patient, appointment, revenue, pharmacy, lab, staff reports | `analytics` module (SQL aggregates) | 5 |
| 4 Performance | Concurrent users, fast response, optimised DB | Indexed schema, paginated APIs, HikariCP pool, no N+1 (fetch joins / projections) | all |
| 4 Security | Secure login, password encryption, RBAC, audit logs, session timeout | BCrypt(12), session cookie + CSRF, `@PreAuthorize` permissions, `audit_logs`, 30-min idle timeout | 2 |
| 4 Reliability | Daily backup, availability, recovery | `deploy/backup/*.ps1` + Task Scheduler, recovery runbook | 7 |
| 4 Usability | Friendly, responsive, easy navigation | Design system, responsive layouts, role-filtered navigation | all |
| 5 Architecture | Presentation → Application → Business → Database | React → Controllers → Services → Repositories/MySQL ([02](02-architecture.md)) | 1 |
| 6 Database modules | 13 modules | Full schema, 50+ tables ([03](03-database-schema.md)) | 1 |
| 7 UI | Login, dashboard, patient, appointment, billing screens | React pages | 2–5 |
| 9 Software | Windows, Apache, Java, MySQL | Spring Boot 3 / Java 17, MySQL 8, Apache 2.4 on Windows | 2, 7 |
| 10 Backup | Daily + weekly full backups, DR plan, audit trail | `mysqldump` scripts, binlog retention, recovery doc | 7 |
| 12 Future | Mobile app, portal, SMS, telemedicine, insurance, AI, biometrics, cloud | **Out of scope.** The schema leaves room for insurance fields and notifications. No AI decision support will be built. | — |

## 2. Functional requirements (consolidated)

Each module lists **business rules**: the logic the backend enforces, not just the fields it stores.

### A. Authentication and user management
- Log in with username + password. Usernames are case-insensitive and unique.
- Passwords are BCrypt-hashed (cost 12). The policy: at least 10 characters, with at least one letter and one digit. The user cannot reuse their current password.
- **Lockout:** 5 failed attempts within 15 minutes locks the account for 15 minutes. Every attempt is written to `login_attempts`.
- Sessions are server-side (Spring Session JDBC). The session ID goes in an HttpOnly cookie with SameSite=Strict, and expires after **30 minutes idle**. Logout invalidates the session.
- Admin-issued accounts get a temporary password with `must_change_password = true`. The user cannot reach any other page until they change it.
- **Password reset:** an admin resets the password, which sets a new temporary one. Self-service email reset needs an SMTP server, which the brief doesn't provide; it's listed as a later enhancement.
- **Deactivation:** an admin can deactivate an account. Deactivated users cannot log in, and their sessions are revoked.

### B. Patient management
- **MRN** (Medical Record Number) format: `HMS-YYYY-NNNNNN`. It is generated in the same transaction from a `sequences` row locked `FOR UPDATE`, so it can never be duplicated.
- **Duplicate detection:**
  - NIC (national ID) is unique when present.
  - A soft duplicate warning fires on the same (first name, last name, date of birth, phone). The receptionist must confirm "not a duplicate" before saving.
- **Validation:**
  - Sri Lankan NIC: old format `^[0-9]{9}[VvXx]$` or new format `^[0-9]{12}$`.
  - Phone in E.164 format, or the local `0XXXXXXXXX` format.
  - Date of birth cannot be in the future. Age is calculated, not stored.
- **Statuses:** ACTIVE, INACTIVE, DECEASED. Records are never hard-deleted, only deactivated (to keep clinical history and referential integrity).
- **Documents:**
  - Allowed types: PDF, JPEG or PNG, up to 10 MB.
  - The file type is checked by its magic bytes, not just its extension.
  - Files are stored outside the web root under a random UUID name. Downloads go through an authorised endpoint only.

### C. Doctor management
- A doctor is an employee with a `doctors` profile holding: specialisation, licence number (SLMC registration, unique), consultation fee and department.
- **Weekly schedules:** day of week, start and end time, slot length in minutes, and maximum patients. Schedule exceptions (leave or blocked dates) mark slots as unavailable.

### D. Appointment management
- **State machine** (enforced in the service; any other move is rejected with HTTP 409):
  ```
  PENDING → CONFIRMED → CHECKED_IN → IN_PROGRESS → COMPLETED
  PENDING|CONFIRMED → CANCELLED (reason required)
  CONFIRMED → NO_SHOW (only after the slot start time has passed)
  ```
- **No double-booking:**
  - **Doctor:** a unique key on `(doctor_id, slot_start)` for active appointments, implemented as a generated `active_slot_key` column that is NULL when the appointment is cancelled or a no-show, so those slots free up.
  - **Patient:** a patient cannot hold two active appointments that overlap in time.
- The slot must fall inside the doctor's schedule, must not be in the past, and must not fall on a schedule exception.
- **Rescheduling** creates a new slot and records the link to the old one (`rescheduled_from_id`). The old appointment becomes CANCELLED with reason `RESCHEDULED`. History is kept for cancellation analytics.
- **Token number:** each doctor's appointments on a day are numbered in order.

### E. EMR (Electronic Medical Records)
- An **encounter** is created when an appointment moves to IN_PROGRESS, or on admission. Diagnoses, notes, prescriptions, vitals and lab requests attach to an encounter.
- **Diagnoses:** ICD-10 code (optional) plus free text. Type: PRIMARY or SECONDARY.
- **Allergies:** stored per patient, with severity. Prescribing a medicine whose generic name matches a recorded allergy shows a **warning that the doctor must acknowledge**. This is a factual match, not a clinical recommendation.
- **Vital signs:** temperature, pulse, blood pressure (systolic and diastolic), respiratory rate, SpO₂, weight and height. BMI is calculated, and each value is checked against a physiologically plausible range.
- **Sign-off:** a clinical note is editable by its author until it is signed. After that it is immutable, and corrections are made as addenda.
- **Access:** sensitive EMR data is visible only to roles holding `emr:read`. Every EMR read of another patient's chart is written to the audit log.

### F. Inpatient management
- Hierarchy: ward → room → bed. A bed's status is one of AVAILABLE, OCCUPIED, CLEANING, MAINTENANCE.
- **Admission** locks the bed (`SELECT … FOR UPDATE`) and checks that it is AVAILABLE. It then sets the bed to OCCUPIED and creates the `admissions` row and a `bed_assignments` row in **one transaction**. A unique key on the active bed assignment also blocks double allocation at the database level.
- **Transfer** closes the current bed assignment, opens a new one, and sets the old bed to CLEANING.
- **Discharge** requires a discharge summary. It closes the assignment, sets the bed to CLEANING, and raises the bed-day charges.

### G. Laboratory management
- A **test catalogue** stores each test's code, name, sample type, reference range, unit, turnaround time and price.
- **Request status:** REQUESTED → SAMPLE_COLLECTED → PROCESSING → COMPLETED → REVIEWED. Status is tracked per test item; the request's overall status is the least-advanced item.
- When a sample is collected, the system generates a sample barcode ID and records who collected it and when.
- **Results:** a numeric result outside the reference range is flagged H (high) or L (low). Only the requesting doctor, or a doctor in the same department, can mark a result REVIEWED.

### H. Pharmacy management
- **Stock** is held per batch: batch number, expiry date, quantity on hand, and cost price. The selling price is held on the medicine.
- **Dispensing:**
  - Batches are allocated **FEFO** (first expiry, first out). Expired batches are never dispensed.
  - The batch rows are locked `FOR UPDATE`, and the system checks that the total available covers the requested quantity. Each batch decrement writes a `stock_transactions` row.
  - A CHECK constraint keeps `quantity_on_hand >= 0` as the last line of defence.
- **Stock movement types:** RECEIPT, DISPENSE, ADJUSTMENT_IN, ADJUSTMENT_OUT, RETURN, EXPIRED_WRITE_OFF. The ledger is append-only, and current stock always equals the sum of the movements (checked by a test).
- **Alerts:**
  - Low stock: total available stock is at or below `reorder_level`.
  - Expiry: batches expiring within 90 days, configurable.

### I. Billing and payments
- **Charges** come from their source event:
  - consultation: appointment COMPLETED
  - laboratory: request item created
  - pharmacy: dispensation
  - admission: bed-days at discharge, or on demand
  - other services: added manually from the service catalogue
- An **invoice** collects a patient's unbilled charges. Status: DRAFT → ISSUED → PARTIALLY_PAID → PAID, or VOID (void requires a reason and has no payments).
- **Money** is stored as `DECIMAL(12,2)`, rounded HALF_UP. The invoice total equals the sum of its line totals, minus the discount, plus tax. This is calculated in the service and checked by a test.
- **Payments:**
  - Methods: CASH, CARD, BANK_TRANSFER, CHEQUE.
  - Partial payments are allowed. A payment can never take the paid total above the invoice total.
  - Each payment gets a receipt number from a locked sequence.
- **Refunds** are recorded as negative-amount payment rows that reference the original payment, so the history stays complete.

### J. Staff management
- **Employee:** employee number, name, contact details, department, designation, employment type, join date and status (ACTIVE, ON_LEAVE, SUSPENDED, RESIGNED, TERMINATED).
- **Attendance:** one row per employee per day (unique), with clock-in and clock-out times. Hours worked are calculated.
- **Leave:**
  - Types: ANNUAL, CASUAL, SICK, MATERNITY, NO_PAY.
  - Workflow: PENDING → APPROVED or REJECTED, or CANCELLED.
  - A request cannot overlap the employee's existing approved leave, and it cannot exceed the yearly entitlement for its type.

### K. Reports and analytics
- Every figure is a SQL aggregate over real tables, filtered by date range and department. No numbers are hard-coded.
- Reports open on screen and can be exported as CSV. PDF output (receipts, lab reports, medical reports) uses print-optimised HTML with `@media print`; this is enough for the PDF's "printer" requirement without a server-side PDF library.

## 3. Non-functional requirements

| Area | Requirement | Measure |
|---|---|---|
| Performance | p95 API latency under 300 ms for list and detail endpoints on the seed dataset | Paginated endpoints (max 100 per page), indexes on every FK and search column |
| Concurrency | Correct under simultaneous booking, dispensing and bed allocation | Row locks plus unique keys; concurrency integration tests |
| Security | OWASP Top-10 basics | See [02 §Security](02-architecture.md#security-architecture) |
| Auditability | Every create, update, status change and sensitive read | `audit_logs` with actor, action, entity, before/after JSON, IP, timestamp |
| Availability | Single Windows host | Backend as a Windows service with auto-restart; daily backups |
| Privacy | Sri Lanka Personal Data Protection Act No. 9 of 2022 (PDPA) principles | Data minimisation, purpose-limited access, audit trail, retention notes. **This is not a compliance certification**; a legal review is needed before real patient data is used. |
| Accessibility | WCAG 2.1 AA contrast; keyboard navigation | Contrast check on the palette (see §5); focus rings; semantic HTML |

## 4. Gaps in the specification and how they are resolved

| # | Gap | Resolution |
|---|---|---|
| G1 | The PDF lists "Outpatient Management" but gives no workflow | OPD flow = appointment (or walk-in) → check-in → encounter → orders → billing. Walk-ins are appointments created directly as CHECKED_IN. |
| G2 | No patient identifier format | MRN `HMS-YYYY-NNNNNN` from a locked sequence (§2B) |
| G3 | No rule for what happens to medical data on deletion | No hard deletes of clinical or financial data. Deactivate or void with a reason; the audit trail is kept. |
| G4 | "Password management" undefined | Policy and flows in §2A; self-service email reset deferred (no SMTP in the brief) |
| G5 | Who may see which patients? | Doctors and nurses can **search** all patients (emergencies need this). Opening a chart outside their own appointments, admissions or department records a "break-the-glass" audit entry with a reason. |
| G6 | Ownership of charges and invoice timing | Charges are raised automatically by source events (§2I). The accountant builds invoices from unbilled charges. |
| G7 | No currency or tax | Currency LKR. Tax rate is a system setting (default 0%). |
| G8 | Nurse ↔ patient assignment | Nurses are assigned to wards; "assigned patients" means current admissions in their ward(s) |
| G9 | Lab result review | Only doctors review. Results are flagged against reference ranges (H/L), with no interpretation. |
| G10 | Notifications | In-app only (`notifications` table, polled by the client). SMS and email are future enhancements per PDF §12. |
| G11 | Super Admin vs Hospital Administrator | Super Admin manages system settings, roles and permissions, and other admins. Hospital Admin manages day-to-day users, departments, staff, wards and catalogues. |
| G12 | HR / Staff Manager overlaps with Admin on employees | HR owns employee records, attendance and leave. Admin owns user accounts and access. |
| G13 | Backup "high availability" on a single Windows host | Achievable here: automated backups, binlog point-in-time recovery, and auto-restarting services. Real HA (replication or failover) is documented as a production follow-up. |

## 5. Colour palette — contrast check

White text on each palette colour (WCAG 2.1 contrast ratio):

| Token | Hex | White text | Use |
|---|---|---|---|
| blue-100 | `#B7D3E3` | 1.5 : 1 ✗ | Page background tint, hover states |
| blue-200 | `#A4C8E1` | 1.7 : 1 ✗ | Borders, selected rows |
| blue-300 | `#8FB8D8` | 2.1 : 1 ✗ | Chart series, decorative |
| blue-400 | `#6FA3D8` | 2.7 : 1 ✗ | Chart series, icons on white |
| blue-500 | `#4A8BBE` | 3.7 : 1 — passes only for large text (≥ 18.66 px bold) | Large headings and buttons with bold 16px+ text |

None of the five colours reaches 4.5 : 1 against white for body text. To keep "deep blue primary buttons" accessible, the design system adds **two darker shades of the same hue**, derived from `#4A8BBE`:

- `blue-600 #346F9E` gives 5.37 : 1 with white. Used for primary button backgrounds and links.
- `blue-700 #24537A` gives 8.10 : 1. Used for text on light-blue backgrounds and for the sidebar.

The five supplied colours are used exactly as given for backgrounds, tints, borders and charts. Body text is slate-800 or slate-900.
