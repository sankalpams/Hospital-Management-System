# 03 — Database Schema

- **Engine**: MySQL 8.0, InnoDB, `utf8mb4_0900_ai_ci`. All timestamps are stored in UTC as `DATETIME(3)`.
- **Source of truth**: the Flyway migrations in [`backend/src/main/resources/db/migration`](../backend/src/main/resources/db/migration). This document explains them; the SQL files are the authority.

| Migration | Contents |
|---|---|
| `V1__identity_security_audit.sql` | roles, permissions, role_permissions, users, user_roles, login_attempts, SPRING_SESSION(_ATTRIBUTES), audit_logs, sequences, system_settings, notifications |
| `V2__organisation_staff_doctors.sql` | departments, employees, doctors, doctor_schedules, doctor_schedule_exceptions, attendance, leave_types, leave_requests |
| `V3__patients_appointments.sql` | patients, patient_emergency_contacts, patient_documents, appointments |
| `V4__pharmacy_inventory.sql` | medicine_categories, suppliers, medicines, medicine_batches, stock_transactions |
| `V5__clinical_emr.sql` | encounters, diagnoses, clinical_notes, vital_signs, allergies, prescriptions, prescription_items, dispensations, dispensation_items |
| `V6__inpatient.sql` | wards, rooms, beds, admissions, bed_assignments, nurse_ward_assignments |
| `V7__laboratory.sql` | lab_tests, lab_requests, lab_request_items, lab_results |
| `V8__billing.sql` | service_catalogue, invoices, charge_items, invoice_items, payments |
| `V9__roles_permissions_reference.sql` | Reference data: 9 roles, 58 permissions, grants, leave types, settings |

Brief entities → tables: every table the brief lists exists. Where real workflows need more, extra tables were added:

| Added table | Reason |
|---|---|
| `encounters` | The anchor for every clinical action (an OPD visit or an admission) |
| `clinical_notes`, `vital_signs`, `allergies` | EMR detail the brief asks for |
| `doctor_schedules`, `doctor_schedule_exceptions` | Availability and conflict checks |
| `bed_assignments` | Bed history and transfers |
| `nurse_ward_assignments` | Defines "assigned patients" for nurses |
| `dispensations`, `dispensation_items` | Batch-level traceability of what was dispensed |
| `lab_request_items` | Status per test (one request can hold several tests) |
| `charge_items`, `service_catalogue` | Charges from source events, then invoicing |
| `leave_types` | Entitlements per leave type |
| `sequences` | Gap-free human-facing numbers |
| `system_settings` | Runtime configuration |
| `SPRING_SESSION*` | Server-side sessions |

The brief's `medical_records` is realised as encounters + diagnoses + clinical_notes + vital_signs.

## 1. Entity relationships

```
users ─┬─< user_roles >── roles ──< role_permissions >── permissions
       ├── 0..1 employees ──> departments <── wards ──< rooms ──< beds
       ├─< login_attempts, notifications, audit_logs (actor)
       │
employees ── 0..1 doctors ──< doctor_schedules, doctor_schedule_exceptions
employees ──< attendance, leave_requests >── leave_types
employees >──< wards  (nurse_ward_assignments)

patients ──< patient_emergency_contacts, patient_documents, allergies, vital_signs
patients ──< appointments >── doctors, departments      (appointments.rescheduled_from_id → appointments)
patients ──< encounters >── doctors, departments; encounters ── 0..1 appointment | admission
encounters ──< diagnoses, clinical_notes (addenda → parent), vital_signs, prescriptions, lab_requests
prescriptions ──< prescription_items >── medicines
prescriptions ──< dispensations ──< dispensation_items >── medicine_batches, prescription_items
medicines >── medicine_categories;  medicines ──< medicine_batches >── suppliers
medicine_batches ──< stock_transactions (append-only ledger)
patients ──< admissions ──< bed_assignments >── beds
lab_requests ──< lab_request_items >── lab_tests;  lab_request_items ── 0..1 lab_results
patients ──< charge_items >── 0..1 invoices;  invoices ──< invoice_items ── 1 charge_item
invoices ──< payments (refunds: payments.refund_of_payment_id → payments)
```

## 2. Integrity guarantees enforced by the database

These are the last line of defence. The services check the same rules first so they can return clear error messages. All of them were tested against MySQL 8.0 (see [07-testing.md](07-testing.md)).

| Rule | Mechanism |
|---|---|
| A doctor can't be double-booked; cancelled or no-show slots free up | Stored generated column `active_slot_start` (NULL when CANCELLED or NO_SHOW) + `UNIQUE (doctor_id, active_slot_start)` |
| A cancellation must carry a reason | `CHECK (status <> 'CANCELLED' OR cancel_reason IS NOT NULL)` |
| A bed holds at most one patient; a patient holds at most one bed | `bed_assignments.active_bed_id` / `active_admission_id` generated columns + UNIQUE |
| At most one open admission per patient | `admissions.active_patient_id` generated column + UNIQUE |
| Stock never goes negative | `CHECK (quantity_on_hand >= 0)` on batches, `CHECK (balance_after >= 0)` on the ledger |
| Ledger direction is consistent | `CHECK` ties the sign of `quantity` to the movement type |
| Invoice arithmetic | `CHECK (total_amount = subtotal - discount_amount + tax_amount)`, discount ≤ subtotal |
| No overpayment | `CHECK (amount_paid BETWEEN 0 AND total_amount)`; `balance_due` is a generated column |
| Only unpaid invoices can be voided, and a void needs a reason | `CHECK (status <> 'VOID' OR (void_reason IS NOT NULL AND amount_paid = 0))` |
| Refunds reference the original payment | `CHECK` on the sign of `amount` vs `refund_of_payment_id` |
| An event is charged only once | `UNIQUE (source_type, source_id)` on `charge_items` |
| Prescription quantities | `CHECK (quantity_dispensed BETWEEN 0 AND quantity_prescribed)` |
| Plausible vital signs | Range CHECKs, and systolic > diastolic |
| A discharge needs a summary | `CHECK` on status DISCHARGED |
| Deceased status is consistent | `CHECK ((status = 'DECEASED') = (deceased_at IS NOT NULL))` |
| One attendance row per employee per day | `UNIQUE (employee_id, work_date)` |
| No orphans | A foreign key on every reference. Clinical and financial parents are `ON DELETE RESTRICT`; pure child detail is `CASCADE`; actor references are `SET NULL`. |
| Unique identifiers | MRN, NIC, passport, SLMC registration, employee no, invoice / receipt / appointment numbers, batch per medicine, lab barcode |

## 3. Index strategy

- **Every FK column is indexed** (InnoDB requires this, and it makes joins cheap).
- **Search**:
  - `patients (last_name, first_name)`, `(phone)`, and `(date_of_birth, last_name, first_name)` for duplicate detection
  - unique `mrn` and `nic`
- **Time-range analytics**: composite indexes lead with the filter column and include the time column, so `GROUP BY DATE(...)` queries scan a range. Examples:
  - `appointments (status, slot_start)`, `(department_id, slot_start, status)`, `(doctor_id, slot_start, status)`
  - `charge_items (category, service_date)`, `(department_id, service_date)`
  - `payments (received_at, method)`, `invoices (status, issued_at)`
  - `stock_transactions (medicine_id, created_at, type)`, `lab_request_items (status, created_at)`
- **FEFO dispensing**: `medicine_batches (medicine_id, expiry_date, quantity_on_hand)`
- **Audit**: `(entity_type, entity_id, created_at)` and `(actor_user_id, created_at)`

## 4. Data types

| Kind | Type |
|---|---|
| Surrogate keys | `BIGINT AUTO_INCREMENT` |
| Money | `DECIMAL(12,2)` (up to LKR 9,999,999,999.99) |
| Lab values | `DECIMAL(12,3)` |
| Statuses | `VARCHAR` + `CHECK` (maps to Java enums with `EnumType.STRING`; easier to migrate than MySQL `ENUM`) |
| Optimistic locking | `version BIGINT` on aggregates that are edited concurrently |
| Audit snapshots | `JSON` |

## 5. Seed data

| Kind | Where | Loaded in |
|---|---|---|
| Reference (roles, permissions, leave types, settings) | `V9` migration | every environment |
| First Super Admin | `BootstrapAdminRunner`, from `HMS_BOOTSTRAP_ADMIN_*` env vars, only when the users table is empty | every environment |
| Demo data (departments, staff, doctors, patients, appointments, medicines, lab tests, wards…) | 🔜 Phase 5: a dev-only seeder enabled by a `HMS_SEED_DEMO` flag | dev only |

Demo data is clearly synthetic, with fictitious names and NIC numbers. It is never loaded in production.

## 6. Setup

See [08-windows-deployment.md](08-windows-deployment.md) §MySQL for production. For development:

```powershell
copy .env.example .env      # then edit the passwords
cd deploy\docker
docker compose --env-file ..\..\.env -f docker-compose.dev.yml up -d
```

Flyway creates and upgrades the schema automatically when the backend starts.
