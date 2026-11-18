<div align="center">

#  HMS — Hospital Management System

**One system for the whole hospital: front desk, clinic, wards, laboratory, pharmacy, billing, staff and reports.**

![React](https://img.shields.io/badge/React-18-61DAFB?logo=react&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4-6DB33F?logo=springboot&logoColor=white)
![Java](https://img.shields.io/badge/Java-17-ED8B00?logo=openjdk&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-8-4479A1?logo=mysql&logoColor=white)
![Tailwind](https://img.shields.io/badge/Tailwind-3-06B6D4?logo=tailwindcss&logoColor=white)
![Apache](https://img.shields.io/badge/Apache-HTTP%20Server-D22128?logo=apache&logoColor=white)

[Features](#-features) · [Quick start](#-quick-start) · [Accounts](#-sign-in-accounts-development) · [Architecture](#-architecture) · [Roles](#-roles) · [Testing](#-testing) · [Docs](#-documentation)

</div>

---

##  Features

| Area | What it does |
|---|---|
|  **Patients** | Registration with MRNs (`HMS-2026-000001`), duplicate detection, emergency contacts, document uploads checked by file signature |
|  **Appointments** | Doctor schedules, slot availability, booking, walk-ins, check-in → consult → complete, rescheduling; double booking is impossible at the database level |
|  **Medical records** | Encounters, diagnoses (ICD-10), signed notes with addenda, vitals, allergies, prescriptions with allergy-conflict checks, break-the-glass access with a logged reason |
|  **Inpatients** | Live bed board, admit / transfer / discharge, bed-day charges; one patient per bed, enforced by locks and unique keys |
|  **Laboratory** | Test catalogue, sample barcodes, results flagged against reference ranges, doctor review, printable reports |
|  **Pharmacy** | Batches with expiry, first-expiry-first-out dispensing, a stock ledger that always matches stock on hand, low-stock and expiry alerts |
|  **Billing** | Charges collect automatically from every module → invoices with discounts → partial payments, refunds, voids, printable receipts |
|  **Staff** | Employees in every category, daily attendance register, leave with yearly balances and approval (nobody approves their own) |
|  **Reports** | Role-aware dashboard and 9 reports (appointments, revenue, occupancy, lab turnaround, …) with filters, charts, tables and CSV export, all computed from live records |
|  **Notifications** | Urgent lab orders, results, low stock and leave decisions reach the right people |

##  Quick start

> **Prerequisites:** JDK 17 · Node 20+ · Docker Desktop

```powershell
# 1. Secrets: copy, then edit the passwords
copy .env.example .env

# 2. MySQL on port 3307
cd deploy\docker; docker compose --env-file ..\..\.env -f docker-compose.dev.yml up -d; cd ..\..

# 3. Backend on :8080 (Flyway builds the schema and creates the first Super Admin)
cd backend; .\mvnw.cmd spring-boot:run

# 4. Frontend on :5173 (second terminal; proxies /api to :8080)
cd frontend; npm install; npm run dev
```

Open **http://localhost:5173** and sign in with `HMS_BOOTSTRAP_ADMIN_USERNAME`. You'll be asked to set a new password on first sign-in.

<details>
<summary><b>Optional: a year of demo data</b></summary>

Fills the development database with synthetic activity (about 3,000 patients, 20,000 appointments and 18,000 invoices) so dashboards and reports have something to show. It runs once, takes a few minutes, and exists only under the `dev` profile, so it never runs in production.

```powershell
cd backend; .\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--hms.demo.seed=true"
```
</details>

##  Sign-in accounts (development)

One login per role in the **local development database**, for trying each part of the system at http://localhost:5173.

> **Development only.** These passwords exist only in this machine's dev database. Never create them on a real server, and change or remove this table before the repository is shared publicly.

| Role | Username | Password |
|---|---|---|
| Super Admin | `sysadmin` | `Summit-Maple-886` |
| Hospital Admin | `hospital.admin` | `Granite-Amber-400` |
| Doctor | `doctor` | `Coral-Harbor-374` |
| Nurse | `nurse` | `Tide-Maple-337` |
| Receptionist | `reception` | `Indigo-Summit-618` |
| Lab Staff | `lab` | `Maple-Granite-169` |
| Pharmacist | `pharmacist` | `Cedar-Falcon-345` |
| Accountant | `accountant` | `Coral-Maple-751` |
| HR Manager | `hr.manager` | `Monsoon-Falcon-595` |

`doctor` is linked to the seeded doctor profile **Dr. Rizwan Abeysekera** (Paediatrics), so it sees its own appointments and can run consultations. A real installation starts with only the first administrator, who creates staff accounts in **Users**.

##  Architecture

```
Browser ──► Apache HTTP Server ──┬──► React build (static files)
            (CSP, TLS, headers)  └──► /api ──► Spring Boot 3 ──► MySQL 8
                                               (Security, JPA,     (Flyway
                                                Spring Session)     migrations)
```

- **Frontend:** React 18, Vite, Tailwind with design tokens, TanStack Query, Recharts, Lucide icons, self-hosted fonts.
- **Backend:** Spring Boot 3.4 with Spring Security (sessions, CSRF), Spring Data JPA, Bean Validation and Flyway; modules for each hospital area under `lk.hms.modules`.
- **Data integrity lives in the database:** generated columns with unique keys stop double booking and double bed occupancy, CHECK constraints guard stock and invoice arithmetic, and row locks protect concurrent bookings, dispensing and payments.

```
HMS/
├── backend/          Spring Boot app, Flyway migrations, integration tests
├── frontend/         React app, unit tests (Vitest), browser tests (Playwright)
├── design-system/    Tokens, MASTER.md, contrast checks
├── deploy/           Docker (dev MySQL), Apache config, Windows service
└── docs/             Requirements → deployment guide
```

##  Roles

Nine roles, each a set of permissions. The **server enforces every permission**; the UI just hides what a role can't use.

`Super Admin` · `Hospital Admin` · `Doctor` · `Nurse` · `Receptionist` · `Lab Staff` · `Pharmacist` · `Accountant` · `HR Manager`

See the full matrix in [docs/05](docs/05-roles-permissions.md).

##  Security

- No secrets in source code; configuration comes from environment variables (`.env` is git-ignored).
- BCrypt (cost 12) passwords; accounts lock after 5 failed sign-ins; temporary passwords must be changed.
- Server-side sessions with HttpOnly + SameSite=Strict cookies, a 30-minute idle timeout, and CSRF protection on every write.
- Strict Content-Security-Policy and protective headers at Apache.
- Every change is audited. CSV exports neutralise spreadsheet formulas.
- Privacy follows the principles of Sri Lanka's Personal Data Protection Act No. 9 of 2022. **This is not a compliance certification**: a legal review is required before real patient data is used.

##  Testing

```powershell
cd backend;  .\mvnw.cmd test     # integration tests against the hms_test schema
cd frontend; npm run check       # contrast gate + unit tests + production build
cd frontend; npm run e2e         # Playwright smoke test per role (dev stack running)
```

The suites cover concurrency (parallel bookings, dispenses, payments and bed allocations each succeed exactly once), a full patient journey from booking to a paid invoice, a role × endpoint permission matrix generated from the code, and security behaviour (CSRF, session expiry and fixation, lockout, CORS, SQL probes). Details are in [docs/07](docs/07-testing.md).


##  Documentation

| | |
|---|---|
| [01 Requirements](docs/01-requirements-analysis.md) | Traceability, business rules, decisions |
| [02 Architecture](docs/02-architecture.md) | Tiers, layout, security architecture |
| [03 Database](docs/03-database-schema.md) | Tables, relationships, integrity, indexes |
| [04 API](docs/04-api.md) | Conventions and endpoint catalogue |
| [05 Roles](docs/05-roles-permissions.md) | Permission matrix, record-level rules |
| [06 Plan](docs/06-implementation-plan.md) | Phases and definition of done |
| [07 Testing](docs/07-testing.md) | Suites, coverage, security checklist |
| [08 Deployment](docs/08-windows-deployment.md) | Java, MySQL, Apache, services, backups |
| [Design system](design-system/hms/MASTER.md) | Tokens, components, accessibility |
