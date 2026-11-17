# Hospital Management System — Design

Date: 2026-11-17
Source spec: `docs/source/Hospital_system.pdf` (Software Specification Document, HMS).
This design covers every item in that PDF; spec section numbers are cited per module.
Inpatient management (§7.8) comes from spec §1.2 "Inpatient and Outpatient Management" and the
"Admissions" database module in spec §6.

## 1. Intent

A university project: a complete, working, browser-based Hospital Management System that
implements **every module and every role** in the source spec, end to end. It runs locally,
is filled with realistic fake data, and must hold up in a live demo and a code/architecture
review. It is not going to be used with real patients.

"End to end" means each module is real: data entered in one module flows correctly into the
others (a dispensed prescription reduces stock *and* creates a pharmacy charge *and* shows on
the invoice). No placeholder screens.

## 2. Goals, non-goals, anti-goals

**Goals**
- All 10 scope areas from the spec, for all 7 roles, with role-based access.
- All non-functional items that can be meaningfully demonstrated locally: hashed passwords,
  RBAC, audit log, automatic session timeout, daily + weekly backups with a restore script,
  responsive UI.
- Clean 4-layer architecture matching spec §5, easy to explain in a report.
- Automated tests (TDD) for business logic and API; E2E tests for the key flows.
- One-command local setup with seeded demo data and one demo login per role.

**Non-goals** (spec §12 "Future Enhancements" and anything production-only)
- Mobile app, patient portal, SMS/email, telemedicine, insurance, AI decision support,
  biometrics, cloud deployment.
- Real regulatory compliance (HIPAA etc.), real payment gateway, multi-hospital/multi-branch.
- True high availability (clustering/failover). Documented as a deployment note only.

**Anti-goals** (failure even if it "works")
- Stub modules or screens that look done but don't persist or don't connect to other modules.
- Inconsistent data across modules (stock, charges, statuses out of sync).
- A demo that breaks: missing seed data, setup that needs manual DB work, crashes on empty states.
- UI that ignores the design tokens or is inaccessible (no labels, no keyboard use).

## 3. Constraints

- Developer machine: Windows 11, Node 22, npm 11, Git, Docker Desktop. No local Postgres —
  Postgres runs in Docker.
- Deadline unknown → build in **vertical slices** (§9), each demo-able on its own, so there is
  always a working system.
- Workflow (chosen by the user):
  - superpowers: writing-plans, subagent-driven-development, test-driven-development,
    systematic-debugging, requesting-code-review, verification-before-completion,
    finishing-a-development-branch.
  - graphify: `/graphify . --update` after each slice; `/graphify query|path|explain` for
    navigating the codebase.
  - ui-ux-pro-max: layout, UX and accessibility guidance for every screen. It **never overrides
    the design tokens** in §8.

## 4. Approaches considered

| Approach | Trade-off |
|---|---|
| **A. React (Vite) + Node/Express API + PostgreSQL, TypeScript** — **chosen** | Maps 1:1 to the spec's 4 layers; popular, easy to defend. Two apps to run, more setup. |
| B. Laravel (PHP) + MySQL | Fastest CRUD, auth built in. Layers less visible; dated UI feel. |
| C. Spring Boot + React + PostgreSQL | Most "enterprise". Slowest, most code per feature. |
| (Next.js full-stack — dropped) | Blurs the presentation/application layers the spec calls out. |

The user picked **A**.

## 5. Architecture

### 5.1 Repository layout (npm workspaces monorepo)
```
/apps/server     Express API (TypeScript)
/apps/client     React SPA (Vite, TypeScript)
/packages/shared Zod schemas, DTO types, enums, role→permission map (used by both apps)
/docker-compose.yml  postgres (dev) + postgres-test
/scripts         backup / restore / seed helpers
/docs            specs, plans, architecture notes, user guide
```

### 5.2 Layers (spec §5)
| Spec layer | Implementation |
|---|---|
| Presentation | `apps/client` — React pages and components |
| Application | `apps/server/src/modules/*/*.routes.ts` + `*.controller.ts` — HTTP, auth guard, permission check, Zod validation, response shaping |
| Business logic | `*.service.ts` — rules, state transitions, cross-module effects, transactions |
| Database | Prisma schema + `*.repository.ts` wrapping Prisma; PostgreSQL 16 |

Each module lives in `apps/server/src/modules/<module>/` with routes, controller, service,
repository, and tests. Controllers never touch Prisma; services never touch `req`/`res`.

### 5.3 Technology (builder's calls, recorded here)
- **Server:** Express 5, Prisma, Zod, bcrypt, multer (uploads), node-cron (backups),
  pino (logging), helmet, express-rate-limit (on login).
- **Client:** React 18, React Router, TanStack Query, React Hook Form + Zod (shared schemas),
  Tailwind CSS driven by token CSS variables, Radix UI primitives (shadcn-style components),
  FullCalendar (appointments calendar), Recharts (dashboard/reports).
- **Testing:** Vitest + Supertest against a dedicated test Postgres; React Testing Library for
  complex components; Playwright for E2E.
- **Printing/exports:** print-optimised HTML pages + `window.print()` for receipts, invoices,
  lab reports, prescriptions; CSV export for all reports. (No server-side PDF generation.)

## 6. Cross-cutting concerns

### 6.1 Authentication & sessions
- Username + password login (spec §7). Passwords hashed with bcrypt (cost 12).
- Server-side sessions: opaque random token in an `httpOnly`, `SameSite=Lax` cookie;
  `sessions` table stores user, created, last-seen, expiry.
- **Automatic session timeout:** 15 min idle (sliding), 8 h absolute. Client shows a warning
  dialog at 1 min remaining with "Stay signed in"; on expiry, redirects to login.
- Password management: change own password (requires current), admin reset (forces change on
  next login), policy min 8 chars with letter + digit. 5 failed logins → 15 min lockout.
- Logout invalidates the session row.

### 6.2 Roles & access control
Roles are rows in `roles` (seeded, fixed set of 7). Permissions are string constants in
`packages/shared` (e.g. `patient:create`, `billing:payment:record`) mapped to roles in code;
the server enforces them on every route, the client uses the same map only to hide UI.
A user has exactly one role.
*(Builder's call)* Doctors can view any patient's full record, for continuity of care. Every
such view is audited (`VIEW_EMR`). Doctors can only create or edit encounters they own.
"Own patients" in reports means patients the doctor has had an encounter with.

| Area | Admin | Doctor | Nurse | Reception | Lab | Pharmacist | Accountant |
|---|---|---|---|---|---|---|---|
| Users/roles, settings, audit log | full | – | – | – | – | – | – |
| Patients (register/edit/search) | full | view | view | full | view | view | view |
| Patient documents | full | full | upload/view | upload/view | upload/view | view | – |
| Doctors & schedules, departments | full | own schedule | view | view | – | – | – |
| Appointments | full | own: view/status | view/check-in | full | – | – | view |
| EMR: diagnosis, notes, prescriptions | view | view all patients; edit own encounters | view + vitals/nursing notes | – | – | view prescriptions | – |
| Lab orders | view | create/view | view/collect sample | – | full | – | – |
| Pharmacy inventory & dispensing | view | view stock | – | – | – | full | – |
| Admissions (IPD), wards, beds | full | admit/discharge | view, transfer, vitals | admit (with doctor) | – | – | view |
| Billing, invoices, payments | full | – | – | create invoice, take payment | – | – | full |
| Staff, attendance, leave | full | own attendance/leave | own | own | own | own | own |
| Reports | all | own patients/appointments | – | appointment, patient | lab | pharmacy | revenue |

### 6.3 Audit log
`audit_logs`: user, action (CREATE/UPDATE/DELETE/LOGIN/LOGIN_FAILED/LOGOUT/VIEW_EMR/EXPORT),
entity type, entity id, before/after JSON diff, IP, timestamp. Written by service-layer helper
inside the same transaction as the change. Viewing a patient's medical record is audited.
Admin-only screen with filters. Audit rows are never updated or deleted by the app.

### 6.4 Records are never hard-deleted
Clinical and financial records use status changes (cancelled, void, inactive), not deletes.

### 6.5 Settings
`settings` table editable by Admin: hospital name/address/phone (for printouts), currency code
and symbol, tax rate, session timeout minutes, expiry-alert window (default 90 days).

**Locale: Sri Lanka.**
- Currency: LKR, displayed as `Rs. 1,500.00`. Amounts are stored in cents (factor 100).
- Tax: 0% by default, because hospital services are generally VAT-exempt. The rate stays
  configurable.
- Time zone `Asia/Colombo`, date format `DD/MM/YYYY`, 24 h time on clinical screens.
- The hospital is fictional: "Lanka General Hospital (Demo)", with a Colombo address.

## 7. Modules — behaviour

IDs shown to users: Patient MRN `P-000123`, invoice `INV-2026-00001`, lab order `LAB-…`,
admission `ADM-…`. All money stored as integer minor units.

### 7.1 User management (spec 3.1)
Admin creates users (username, name, email, role, linked employee), activates/deactivates,
resets password. Everyone can change own password and see own profile.

### 7.2 Patients (3.2)
Fields: MRN (auto), first/last name, DOB, gender, phone, email, address, NIC
(optional), blood group, allergies, chronic conditions, emergency contact (name, relation,
phone). Duplicate warning on same name + DOB or same phone. Search by MRN, name, phone,
NIC (paged, fuzzy on name).

**Sri Lankan validation** (shared Zod schemas in `packages/shared`, used by forms, API and seed):
- **Phone:** stored as `+94` followed by 9 digits. Input may be `07X XXX XXXX`,
  `0XX XXX XXXX` or `+94…`, and is normalised. Mobile prefixes are 70–78.
- **NIC:** optional; usually missing for patients under 16. Two formats are accepted:
  - Old: 9 digits followed by `V` or `X`.
  - New: 12 digits.

  Both encode the birth year, the day of the year, and gender (females get +500 added to the
  day). Those are checked against the entered DOB and gender. A mismatch shows a warning; it
  doesn't block saving.
- **Address:** line 1, line 2, city, district (one of the 25), province (one of the 9,
  filled in automatically from the district), postal code (5 digits). Patient profile page with tabs: Overview, Appointments,
Medical History (timeline of encounters, diagnoses, prescriptions, lab results, admissions),
Documents, Billing. Document upload: PDF/JPG/PNG, max 10 MB, stored in `apps/server/uploads`,
metadata (type: ID, referral, scan, external report, other) in DB.

### 7.3 Departments & doctors (3.3)
Departments: name, description, head. Doctor = employee with a user account + doctor profile:
specialization, license no., department, consultation fee, qualifications.
Schedule: weekly recurring availability (day, start, end, slot length default 15 min) plus
date-specific unavailability (leave, conference). Approved staff leave automatically blocks
the doctor's slots. *(Builder's call)* Approving leave (or adding unavailability) that overlaps
booked appointments is allowed: the approver first sees how many appointments are affected;
on approval those appointments get the flag `NEEDS_RESCHEDULE` and appear in reception's work
queue and dashboard. Appointments has a "block period" service that Staff (slice 9) calls.

### 7.4 Appointments (3.4) — outpatient (OPD)
Book: pick department → doctor → free slot (calendar or list); patient search/quick-register.
Status flow: `SCHEDULED → CHECKED_IN → IN_CONSULTATION → COMPLETED`, plus `CANCELLED`
(reason required) and `NO_SHOW`. Reschedule moves the slot and keeps a history entry.
No double booking: enforced in service and by a DB unique constraint on (doctor, start).
Can't book in the past or outside availability. Calendar view: day/week per doctor or
department; colour by status. Walk-ins: book into the next free slot today.
Completing a consultation creates a consultation charge (doctor's fee).

### 7.5 Electronic medical records (3.5)
`Encounter` = one clinical visit, linked to an appointment (OPD) or an admission (IPD).
Contains: vitals (BP, pulse, temp, SpO₂, weight, height — nurses or doctors), chief complaint,
clinical notes, diagnoses (free text + optional ICD-10 code from a seeded subset; primary/
secondary), prescriptions, lab orders, follow-up date. Doctor signs/closes the encounter;
closed encounters are read-only except for an addendum. Prescriptions: items of medicine
(from pharmacy catalog), dose, frequency, duration, route, quantity, instructions; printable.
Treatment history = patient timeline (7.2). Medical reports = lab reports + uploaded documents
+ printable encounter summary / discharge summary.

### 7.6 Laboratory (3.6)
Test catalog: name, code, category, sample type, unit, reference range (text + numeric low/high),
price, turnaround hours; panels allowed (group of tests). Doctor orders tests in an encounter.
Flow: `ORDERED → SAMPLE_COLLECTED (sample ID, collector, time) → RESULTED → VERIFIED`; also
`CANCELLED`. Result entry per parameter with automatic abnormal flag (H/L) from range.
Verification by a second lab user (or same, recorded). Printable lab report once verified;
doctor sees results in the encounter and timeline. Order creates lab charges.

### 7.7 Pharmacy (3.7)
Medicine catalog: name, generic name, form, strength, unit, unit price, reorder level.
Stock is held in **batches** (batch no., expiry, quantity, cost, supplier, received date).
Stock in (goods received), adjustments (damage/expired/correction, reason required).
Prescription processing: pharmacist sees pending prescriptions, dispenses full or partial;
stock taken **first-expiry-first-out**, never from expired batches; creates pharmacy charges.
Over-the-counter sale to a registered patient is allowed. Alerts (also on dashboard): low stock
(below reorder level), expiring within the configured window, expired with stock on hand.

### 7.8 Inpatient management (IPD)
Wards (type: general/private/ICU, daily bed rate) and beds (status: available/occupied/
maintenance). Admit: patient, admitting doctor, ward/bed, reason, expected stay.
Transfer bed (history kept). Nursing: vitals and nursing notes during stay (encounter linked to
admission). Discharge: doctor writes discharge summary (diagnoses, treatment, medications,
follow-up); bed freed. Admission charges = bed rate × days (calendar days, admit day counts,
discharge day doesn't; minimum 1), split per ward if transferred.
*(Builder's call)* Bed charges are **not** stored day by day. They are calculated from the bed
history whenever an interim bill is shown. On discharge they are written as charge rows,
one per ward stay. The admitting doctor's consultation fee is charged once at admission.
Ward rounds create no charges, because the bed rate includes nursing and rounds. Lab and
pharmacy charges during the stay work the same way as for outpatients. Discharge is **not**
blocked by an unpaid balance: the final invoice is issued and the balance stays outstanding
on the patient.

### 7.9 Billing (3.8)
**Charges** are created automatically by other modules (consultation, lab, pharmacy, admission)
and can be added manually (misc service) by billing staff. Each charge: patient, source, ref,
description, qty, unit price, amount, status `UNBILLED/BILLED/VOID`.
**Invoice**: select patient → pulls unbilled charges (choose which) → optional discount
(amount or %, reason) → tax per settings → `DRAFT → ISSUED`. Issued invoices are immutable;
corrections by voiding (reason) and reissuing.
**Payments**: method (cash/card/bank transfer/other), amount, reference; partial payments →
`PARTIALLY_PAID → PAID`. Printable invoice and payment receipt. Patient balance shown on profile.
IPD: interim bill any time; final bill on discharge.

### 7.10 Staff management (3.9)
Employees: employee no., name, contact, department, designation, employment type, join date,
status; optional linked user account. Attendance: staff clock in/out themselves; admin can
mark/correct (reason audited); daily attendance sheet per department. Leave: types
(annual, sick, casual, unpaid) with yearly balances; request → admin approves/rejects; balance
updated; approved doctor leave blocks appointment slots.

### 7.11 Reports (3.10) & dashboard (spec §7)
Every report: date-range filter, department/doctor filter where relevant, table + chart,
CSV export, print view.
- Patient: new registrations, demographics (age/gender), visits.
- Appointment: by status, by doctor/department, no-show rate.
- Revenue: by source (consultation/lab/pharmacy/admission), by day/month, collections vs
  outstanding, payment method.
- Pharmacy: stock value, low stock, expiring, top dispensed.
- Laboratory: tests by type, turnaround time, pending.
- Staff: attendance summary, leave taken, headcount by department.

Dashboard (role-aware): Admin sees spec §7 widgets — total patients, today's appointments,
revenue summary (today / month), pending lab requests, pharmacy alerts — plus bed occupancy.
Other roles see their work queue first (doctor: today's patients; lab: pending samples;
pharmacist: pending prescriptions + alerts; reception: today's appointments; accountant:
unpaid invoices; nurse: admitted patients + vitals due).

## 8. UI

### 8.1 Screens
Login; app shell (sidebar nav filtered by role, top bar with user menu + session timer);
Dashboard; Patients (list/search, register/edit, profile with tabs); Appointments (calendar,
list, book/reschedule dialog); Doctors & Departments & Schedules; Encounter screen (doctor
workspace); Lab (worklist, result entry, report); Pharmacy (inventory, batches, dispense queue,
alerts); IPD (bed board, admit, discharge); Billing (charges, invoices, payment, receipt);
Staff (employees, attendance, leave); Reports; Admin (users, settings, audit log);
My profile / change password. Responsive down to tablet width; usable on phone for lookups.

### 8.2 Design tokens (authoritative — ui-ux-pro-max must not override)
Defined once as CSS variables in `apps/client/src/styles/tokens.css`, consumed by Tailwind config.
- Colour: primary teal `#0F766E` (hover `#115E59`, subtle `#CCFBF1`); neutral slate scale
  (`#0F172A` text, `#475569` muted, `#E2E8F0` border, `#F8FAFC` app background, `#FFFFFF` surface);
  success `#15803D`, warning `#B45309`, danger `#B91C1C`, info `#1D4ED8`. Status badges use these.
- Type: Inter (fallback system-ui); sizes 12/14/16/18/20/24/30; body 14, line-height 1.5.
- Spacing: 4 px scale (4, 8, 12, 16, 24, 32, 48). Radius: 6 (controls), 10 (cards).
- Shadow: one card shadow, one overlay shadow. Focus ring: 2 px primary, 2 px offset.
- Light theme only.

ui-ux-pro-max is consulted for each screen's layout, interaction and accessibility
(WCAG 2.1 AA contrast, labels, keyboard navigation, focus order, error messages, empty/loading
states), working within these tokens.

## 9. Delivery: vertical slices

Each slice = its own branch, plan → TDD build → code review → verification → merge
(finishing-a-development-branch) → `/graphify . --update`.

0. **Foundation:** monorepo, Docker Postgres, Prisma, layering skeleton, auth/sessions/timeout,
   RBAC, audit log, settings, app shell + tokens, users admin, seed (7 demo users).
1. Departments, employees, doctors & schedules.
2. Patients + documents.
3. Appointments + calendar.
4. EMR: encounters, vitals, diagnoses, prescriptions.
5. Laboratory.
6. Pharmacy.
7. Inpatient (wards, beds, admissions, discharge).
8. Billing (charges from slices 3–7, invoices, payments, printouts).
9. Staff attendance & leave.
10. Reports & role dashboards.
11. Backup/recovery, hardening, full E2E suite, docs (README, user guide, architecture notes).

## 10. Backup & recovery (spec §10)
- `scripts/backup.ts` runs `pg_dump` inside the Postgres container; node-cron in the server
  runs it **daily** (02:00, custom-format dump, keep 7) and **weekly full** (Sunday, plain SQL +
  uploads folder zipped, keep 4) into `/backups`. Admin can also trigger "Backup now" and see
  backup history.
- `scripts/restore.ts <file>` restores into the database (with confirmation).
- `docs/disaster-recovery.md`: restore procedure, RPO 24 h / RTO target, what HA would look
  like in production. Audit trail = §6.3.

## 11. Seed / demo data (large, Sri Lankan)

The seed is deterministic: it uses a fixed faker seed. It lives in `apps/server/prisma/seed/`,
with one module per domain plus a `data/` folder of Sri Lankan lists. Each slice adds its own
seed module.

**Realism**
- **Names and places:** Faker has no Sri Lankan locale, so names and places come from custom
  lists. Sinhala, Tamil and Muslim given names and surnames are mixed realistically. Towns are
  real and weighted by district: Colombo, Gampaha, Kandy, Galle, Kurunegala, Jaffna,
  Batticaloa and so on.
- **IDs and contact details:** NICs are generated from DOB and gender, so they pass §7.2
  validation. Phone numbers follow the local formats.
- **Doctors:** titled "Dr.", with qualifications like MBBS (Col), MD and MRCP, and SLMC
  registration numbers.
- **Prices (LKR):**

  | Item | Price |
  |---|---|
  | Consultation | Rs. 1,500–4,500 by speciality |
  | Lab test | Rs. 400–9,000 |
  | Bed per day | General Rs. 3,000; Private Rs. 12,000; ICU Rs. 45,000 |

- **Medicines:** generic names only, no brands.

**Volume.** `SEED_SCALE=large` is the default and is the demo dataset:

| Data | Volume |
|---|---|
| Departments | ~12 |
| Doctors | ~45 |
| Other staff | ~150 |
| Patients | ~3,000 |
| Appointments | ~30,000 (12 months of history + 3 weeks ahead) |
| Encounters | ~22,000 |
| Lab orders with results | ~12,000 |
| Medicines | ~250, with multiple batches; some low-stock, near-expiry and expired |
| Wards / beds | 10 wards, ~200 beds |
| Admissions | ~1,800 past; ~60% of beds currently occupied |
| Invoices | ~25,000, with ~8% still outstanding |
| Attendance and leave | 12 months for all staff |

`SEED_SCALE=small` (~50 patients) is for tests and quick resets.

**Consistency.** The seed obeys the same rules as the services:
- no double bookings;
- stock never goes negative, and stock changes follow FEFO;
- charges match invoices, and payments match invoice status;
- bed status matches admissions.

**Speed.** Inserts use batched `createMany` in dependency order. The target is under 3 minutes
at large scale.

**Logins.** One demo login per role, all with password `Password1!`: `admin`, `doctor`,
`nurse`, `reception`, `lab`, `pharmacist`, `accountant`. They're listed in the README.
`npm run db:reset` recreates everything at large scale.

## 12. Testing & quality
- TDD for all services and API routes (Vitest + Supertest, real test DB, reset per test file).
- Must-test rules: no double booking, state transitions, FEFO dispensing & stock never negative,
  charge creation per module, invoice totals/discount/tax, payment status, bed-day calculation,
  permission denial per role, session timeout, audit row written.
- Playwright E2E: login per role; register patient → book → check-in → consult with
  prescription + lab order → lab result → dispense → invoice → pay → print receipt;
  admit → transfer → discharge → final bill.
- Sri Lankan validators: unit tests for NIC (old and new formats, DOB and gender cross-check),
  phone normalisation, and district → province mapping.
- Seed integrity: a test runs the seed at `small` scale and checks every §11 consistency rule.
- Performance: on the `large` seed, every dashboard and report responds in under 1 s. Add
  indexes on date, status, patient_id and doctor_id columns.
- Lint (ESLint) + typecheck + tests must pass before each merge.

## 13. Left to the builder
Exact Prisma models/field names, API route naming (REST, `/api/v1/...`), component structure,
pagination style, seed volumes within the ranges above, chart choices per report, ICD-10
subset contents.
