# 07 — Testing

## 1. How to run

| Suite | Command (PowerShell, from the repo root) | Needs |
|---|---|---|
| Backend unit + integration | `cd backend; .\mvnw.cmd test` | Dev MySQL running (Docker, port 3307) with the `hms_test` schema; see §3 |
| Frontend unit + component | `cd frontend; npm test` | — |
| Design-system contrast gate | `cd frontend; npm run check:contrast` | — |
| Everything frontend (contrast + tests + build) | `cd frontend; npm run check` | — |
| DB constraint checks | see §4 | Docker MySQL |
| Browser: roles, responsive, accessibility | `cd frontend; npm run e2e` | Backend on 8080 and `npm run dev` on 5173 against the dev DB; Microsoft Edge (used through Playwright, no browser download); `HMS_DEV_ADMIN_PASSWORD` in `.env` |
| Production build under the production CSP | `cd frontend; npm run build; npm run preview` (port 4173) | Backend on 8080 |

## 2. What is covered

### Phase 2: backend 16 tests

| Test | Proves |
|---|---|
| `PasswordPolicyTest` (6) | Length, letter, digit and "not containing the username" rules; the 72-character BCrypt limit; the `WEAK_PASSWORD` error code |
| `AuthIntegrationTest` (8), MockMvc with the full filter chain and real MySQL | 401 JSON without a session · login rejected without a CSRF token · login returns permissions (never the password), is case-insensitive, and writes `audit_logs` + `login_attempts` · wrong password and unknown user give identical responses · **5 failures lock the account even for the correct password (423)** · an inactive account is revealed only after the correct password · a temporary password blocks every other endpoint (403 `PASSWORD_CHANGE_REQUIRED`); weak or reused new passwords are rejected; a successful change rotates the session ID · **a password change signs out the user's other sessions but keeps the current one** · logout invalidates the session |
| `AuthCookiesHttpTest` (1), a real embedded server over HTTP | `XSRF-TOKEN` cookie is readable by the SPA and `SameSite=Strict`; no session is created before login; `HMS_SESSION` is `HttpOnly; SameSite=Strict`; the CSRF token rotates at login; security headers are present |

### Phase 2: frontend 22 tests (Vitest + Testing Library)

| Test | Proves |
|---|---|
| `LoginPage.test.jsx` (5) | Fields are labelled (accessibility); empty submit shows inline errors and makes no API call; the server error is shown and the password cleared; a temporary password routes to change-password; the show/hide password toggle |
| `AppRouting.test.jsx` (6) | **Regression**: a user with a temporary password is sent from any URL to the change-password screen (this used to render a blank page); normal users get the app shell; anonymous visitors are sent to sign-in; unknown routes show 404 |
| `units.test.js` (11) | The client-side password rules mirror the server; API error normalisation never shows raw responses; status→badge tone mapping; permission-filtered navigation |

### Manual end-to-end check (browser, 2026-11-18)

Run against Vite (5173), Spring Boot (8080) and Docker MySQL. Results:

- **Sign-in page**: wrong password shows "Incorrect username or password." and clears the field.
- **Bootstrap admin login**: forced to the change-password screen; the live policy checklist and the confirm-mismatch error both work; the change lands on the dashboard.
- **Sign-out and back in** with the new password works.
- **Mobile (375 px)**: the drawer navigation works and there is no horizontal scroll.
- **Audit log**: contains LOGIN, PASSWORD_CHANGED, LOGOUT and LOGIN with IP and request ID. `login_attempts` contains every attempt, including the unknown user.

### Phase 3 additions: backend 49 tests in total, frontend 26 in total

| Test | Proves |
|---|---|
| `UserAdminIntegrationTest` (11) | 403 for roles without `user:*`; a created user gets a 14-character temporary password (only a BCrypt hash is stored) and must change it; duplicate usernames are rejected; per-field validation; **a Hospital Admin cannot grant or modify Super Admin**; deactivation ends live sessions; you cannot deactivate yourself; **the last active Super Admin cannot be demoted**; a role change ends sessions and stale `version` is rejected; reset password ends sessions; search filters; audit endpoints are permission-gated |
| `DepartmentIntegrationTest` (3) | Unique, immutable codes; case-insensitive unique names; deactivation is blocked while active staff are assigned; read vs manage permissions |
| `PatientIntegrationTest` (7) | MRNs `HMS-YYYY-NNNNNN` are sequential; NIC, phone and future-dated birth date are rejected per field; old-format NIC is normalised and NIC is unique (409 names the existing MRN); a **possible duplicate requires explicit confirmation**; search by MRN, NIC, phone and name; optimistic locking; deceased status needs a date and is final; **a script disguised as `.pdf` is rejected (415) while a real PDF is stored**, a path-traversal file name is sanitised, and the download is an attachment with identical bytes; per-role access (pharmacist reads demographics but not documents; HR has no patient access) |
| `AppointmentIntegrationTest` (12), with a controllable clock fixed at Mon 2030-06-03 08:30 Colombo | Availability = session slots capped by max patients; Tuesday shows no session; booking rules (taken slot, beyond capacity, misaligned time, wrong weekday, past date, **same patient with another doctor at the same time**); token numbers; local→UTC conversion; unavailability blocks booking; **full lifecycle** check-in → start → complete, where only the treating doctor can start and a completed appointment cannot be cancelled; check-in only on the day; no-show only after the slot starts; cancel needs a reason and frees the slot; reschedule cancels and links; walk-in takes the next running slot and is checked in; **6 concurrent bookings of one slot → exactly 1 created, 5 × 409**; calendar per-day counts; schedule overlap; doctor login link rules |
| `AppointmentActions.test.jsx` (4) | The UI shows only actions that are both allowed by the server and permitted for the role; cancellation requires a reason |

**Ordering note:** for `@RequestBody` endpoints, Spring validates the body before the method-level `@PreAuthorize` check runs. A user without permission who sends a malformed body therefore gets **400** (field errors) instead of 403. A well-formed request is always refused with 403; this is tested. The only thing exposed is the request schema, which is public in the API docs anyway.

### Manual end-to-end walkthrough (browser, 2026-11-18, Phase 3)

As Super Admin:
1. Created the *General Medicine* department.
2. Added *Dr. Nalin Perera* (EMP-000001), linked to a new `dr.perera` login, with a Wednesday 08:00–18:00 session.
3. Registered a patient: the invalid NIC and the empty emergency contact were caught inline; the patient became HMS-2026-000001.
4. Booked 13:00 from the live slot grid. Past times were crossed out. The booking became APT-2026-000001, token 1.
5. Checked the patient in.

Signed in as `dr.perera`:
6. Forced password change.
7. The appointments view defaulted to "Only my patients".
8. Started and completed the consultation.

The month view showed "1 total · 1 done". The browser console showed no errors.

Issues found and fixed during the walkthrough:
- The page widened horizontally because of an absolutely positioned screen-reader label inside the table.
- A new record showed "Registered —", because the database default timestamp was not read back. Timestamps are now set by Hibernate.
- Focus did not move to the first invalid field after validation.
- The slot counter counted past slots as free.
- A stray vertical scrollbar appeared on the patient tab row.
- Links wrapped buttons. These are now `LinkButton`s.

### Phase 4 additions: backend 83 tests in total, frontend 26 in total

| Test | Proves |
|---|---|
| `NotificationIntegrationTest` (2) | Users see only their own notifications; read and read-all; unread count |
| `EmrIntegrationTest` | Start opens an encounter and complete needs a diagnosis; only the treating doctor writes; notes are edited until signed, then only addenda; vitals validation; **a prescription that conflicts with a recorded allergy is blocked unless an override reason is given**; break-the-glass access needs a reason and is audited |
| `LabIntegrationTest` | Order → sample (SMP barcode) → processing → result → review; H/L flags; text results; each test raises a charge and cancelling removes it; lab staff cannot review; only the treating doctor orders |
| `PharmacyIntegrationTest` (4) | **FEFO dispensing that never touches expired stock**; partial dispensing; **3 concurrent dispenses of the last stock → exactly 1 succeeds**; the stock ledger equals on-hand for every batch |
| `BillingIntegrationTest`, `InvoiceMathTest` | Discount needs a reason; tax after discount; a charge sits on one live invoice only; partial payments and PAID; non-cash needs a reference; overpayment refused; refund limits; void releases charges; **4 concurrent payments of the balance → exactly 1** |
| `InpatientIntegrationTest` (4) | One open admission per patient; admit → transfer (old bed to cleaning) → discharge with bed-day charges per stay; an occupied or maintenance bed cannot be allocated; **4 concurrent admissions to one bed → exactly 1**; doctor requests, desk admits |
| `StaffIntegrationTest` (4) | Every non-doctor category; doctors refused here; NIC unique; resignation sets the leave date; attendance register rules (no future dates, clock-out after clock-in, no times when absent); leave overlap and yearly balance; nobody approves their own leave; approved leave shows on the register |
| `PatientJourneyIntegrationTest` (1) | **One patient through every module**: booking → consultation with diagnosis, lab order and prescription → lab result and review → dispense → admission → discharge after 26 h → one invoice of 12,992.50 (consultation, lab, medicine, two bed-days) → paid in full, nothing left unbilled |

### Manual end-to-end walkthrough (browser, 2026-11-18, Phase 4)

Run as the dev users for each role:
- **Doctor:** consultation with an allergy conflict blocked, lab order and prescription; admission requested from the patient record.
- **Admin / desk:** ward and rooms set up; patient admitted to a bed, the bed board showed the occupant; discharge freed the bed to cleaning and raised a LKR 4,500 bed charge.
- **Accountant:** service charge added, draft invoice with a discount, issued, card payment without a reference refused, then recorded with one; balance updated; receipt printed.
- **HR manager:** employee created and linked to a login; attendance register saved with clock-in; the employee applied for leave from *My leave*, the HR manager was notified and approved it.

No console errors from the app; the only 401s were from switching sessions.

### Phase 5 additions: backend 94 tests in total, frontend 31 in total

Analytics tests give each scenario its own department, so department-filtered figures are exact. They check hospital-wide figures against hand-written SQL with explicit UTC boundaries (local June 2030 = `[2030-05-31 18:30, 2030-06-30 18:30)` UTC).

| Test | Proves |
|---|---|
| `OperationalReportsIntegrationTest` (5) | Three appointments → total 3, completed 2, cancellation 33.3%, average wait 20.0 min (10 and 30), average consultation 15.0 min; 30 daily buckets with zeros, Monday-based weeks; department revenue = two invoiced consultations; hospital totals, trend sum and status-mix sum all equal the SQL count; registrations equal the SQL count; 403 for roles without the report permission; 404 for an unknown report; range and granularity validation; CSV headers and content; formula cells neutralised |
| `ClinicalReportsIntegrationTest` (3) | One 24 h stay in a 2-bed ward over an 80.5 h window → occupancy 14.9%, length of stay 1.0 day; lab turnaround 2.0 h within a 4 h target, backlog 1, low result counted abnormal; pharmacy 10 units worth LKR 42.50, low-stock and expiring tables, units equal the SQL sum |
| `FinanceStaffDashboardIntegrationTest` (3) | Department revenue uses invoice lines before the invoice discount; hospital billed, collected and outstanding equal SQL; ageing buckets and collection methods add up; attendance (1 present + 1 half-day) = 75.0%; approved leave days; the dashboard shows a doctor's own appointments and only the cards each role may see |
| `figures.test.js` (3), `ReportsPage.test.jsx` (2) | Unit formatting, period labels, series grouping; report tabs follow the role's permissions; the CSV link carries exactly the on-screen filters |

### Demo data and report timings (2026-11-18)
- The dev seeder (`--hms.demo.seed=true`) wrote a year of activity in about 5 minutes: 3,000 patients, 20,040 appointments, 18,347 encounters, 10,661 lab tests, 9,705 dispensations, 18,269 invoices, 1,852 admissions and 18,394 attendance rows.
- SQL checks afterwards found no mismatches: every batch's on-hand equals its stock-ledger sum, every invoice subtotal equals its lines, every paid amount equals its payments, and nothing is dated in the future.
- Each report over 12 months of this data answered in 0.09–0.46 s on the dev machine (pharmacy was the slowest).
- Browser check: dashboard, appointments, revenue, inpatient and laboratory reports at 1366 px and 375 px wide (no horizontal scroll), and CSV export from the page.

### Phase 6 additions: backend 107 tests, frontend 32 unit tests, 44 browser tests

| Test | Proves |
|---|---|
| `PermissionMatrixIntegrationTest` (4) | Generated from the code and the database, not written by hand. Every `/api/v1` endpoint has a `@PreAuthorize`, or is on a short reviewed list of endpoints for any signed-in user (they check ownership in the service). Every permission named in a guard exists. For all 9 roles × every guarded GET endpoint (300+ checks), the answer is 403 exactly when the role lacks the permission in the V9 grants. Report endpoints check their own permission. |
| `SecurityHardeningIntegrationTest` (8) | Authenticated writes need the CSRF token, and a header that doesn't match the cookie is refused. Signing in issues a new session id (no session fixation). Sessions expire after 30 idle minutes. A lockout lifts after its 15 minutes. API responses send `nosniff`, `DENY` framing, `same-origin` referrer and `no-store`. Other origins get no CORS grant. SQL-shaped search input matches nothing. Error bodies carry a code, never stack traces or SQL. |
| `CurrentUserIntegrationTest` (1) | `/auth/me` returns the linked doctor profile, so the UI offers "my patients" only to linked doctors |
| `e2e/roles.spec.js` (19, Playwright) | For each of the 9 roles: the sidebar shows exactly the screens the role's permissions allow, and every one loads with no crash, console error, 5xx, or 403 from an API call the screen shouldn't make. A forbidden URL shows the access page. Signing in through the form and signing out work. |
| `e2e/responsive.spec.js` (5) | 10 busy screens at 375, 768, 1024 and 1440 px: the page never scrolls sideways (wide tables scroll inside their own box). Navigation works through the menu on a phone and the sidebar from 1024 px. |
| `e2e/a11y.spec.js` (20) | axe-core with WCAG 2.1 A/AA rules on 19 screens and forms plus the login page: no serious or critical violations |
| `safeAppPath.test.js` (1) | Redirect targets stay on the site (`//host`, `/\host` and absolute URLs are refused) |

What these tests found and fixed:
- A doctor login without a linked doctor profile got a 403 on Appointments.
- Long filter options and the audit date range made the page scroll sideways on phones and tablets.
- The KPI tiles had invalid list markup (links around `dt`/`dd`).
- Four high-severity advisories in shipped dependencies (axios, React Router).

## 3. Test database

Integration tests use the **`hms_test`** schema on the dev MySQL, never `hms`. `TestDatabaseConfig` refuses to run unless the connected schema is `hms_test`, then runs `flyway clean` + `migrate` once per test run.

One-time setup (root password from your `.env`):

```powershell
docker exec hms-java-mysql mysql -uroot -p<root-password> -e "CREATE DATABASE IF NOT EXISTS hms_test; GRANT ALL PRIVILEGES ON hms_test.* TO 'hms_app'@'%';"
```

Override the URL with `HMS_TEST_DB_URL` if needed.

## 4. Database constraint checks

[`database/tests/constraint-checks.sql`](../database/tests/constraint-checks.sql) proves the database itself rejects the following (each line states the expected error):

- double booking
- a bed allocated twice
- a second open admission for the same patient
- negative stock
- overpayment
- a wrong invoice total
- implausible vital signs
- an orphan refund

```powershell
docker exec hms-java-mysql mysql -uroot -p<root-password> -e "DROP DATABASE IF EXISTS hms_scratch; CREATE DATABASE hms_scratch"
Get-ChildItem backend\src\main\resources\db\migration\V*.sql | Sort-Object Name | ForEach-Object { Get-Content $_ -Raw | docker exec -i hms-java-mysql mysql -uroot -p<root-password> hms_scratch }
Get-Content database\tests\constraint-checks.sql -Raw | docker exec -i hms-java-mysql mysql --force -uroot -p<root-password> hms_scratch
docker exec hms-java-mysql mysql -uroot -p<root-password> -e "DROP DATABASE hms_scratch"
```

Last run on 2026-11-18: all 11 behaved as expected.

## 5. Security checklist (2026-11-18)

| Area | Control | Evidence |
|---|---|---|
| Authentication | BCrypt cost 12; password policy; temporary passwords must be changed first; same message for wrong password and unknown user | `AuthIntegrationTest`, `PasswordPolicyTest` |
| Brute force | Per account: 5 failures in 15 minutes lock it for 15 minutes. Per address: 30 failures from one IP in 15 minutes pause sign-in from that IP (429), whatever the usernames, before any password hashing. Both limits are system settings. | `AuthIntegrationTest`, `SecurityHardeningIntegrationTest` |
| Sessions | Server-side (JDBC); HttpOnly, SameSite=Strict cookie, Secure in production; new id on sign-in; 30-minute idle expiry; password change and deactivation end other sessions | `AuthCookiesHttpTest`, `SecurityHardeningIntegrationTest`, `UserAdminIntegrationTest` |
| CSRF | Double-submit cookie on every state-changing request, including sign-in | `AuthIntegrationTest`, `SecurityHardeningIntegrationTest` |
| Authorisation | `@PreAuthorize` on every endpoint, checked against the grants for every role. Record-level rules in services (treating doctor, break-the-glass with a reason, own leave, no self-approval). The UI hides what the server would refuse. | `PermissionMatrixIntegrationTest`, module tests, `e2e/roles.spec.js` |
| Injection | JPA and named parameters only; LIKE wildcards escaped | `SecurityHardeningIntegrationTest` |
| Uploads | Magic-byte type check (a script disguised as a PDF is refused), size limit, stored under random names outside the web root, downloaded as an attachment | `PatientIntegrationTest` |
| Output | React escaping; CSV export neutralises formula cells; errors return a code and message, never internals | `OperationalReportsIntegrationTest`, `SecurityHardeningIntegrationTest` |
| Browser hardening | API: nosniff, DENY framing, same-origin referrer, no-store. Apache adds a strict CSP (`script-src 'self'`, no inline), Permissions-Policy and the same headers. The production build runs under that CSP with no violations. | `SecurityHardeningIntegrationTest`; `npm run preview` check (charts, tooltips and lazy chunks render, console clean) |
| CORS | Same-origin in production; only the configured dev origin in development | `SecurityHardeningIntegrationTest` |
| Secrets | None in the repository: `.env` is git-ignored and only `.env.example` is committed; e2e sessions and credentials are git-ignored | `.gitignore` |
| Audit | Every change and every security event is recorded with the request id: sign-ins, lockouts, break-the-glass access, and console admin recovery (`ADMIN_RECOVERY`) | `AuditService` tests in each module |
| Operations | Graceful shutdown; Flyway clean disabled. Secrets in the service file are readable only by Administrators and SYSTEM, and the one-time admin password is removed automatically after first start. Backups use a read-only database login. Console-only admin recovery. | `install-hms.ps1`, `create-database.ps1`, `AdminRecoveryIntegrationTest`, docs/09 |
| Dependencies | `npm audit --omit=dev`: no high or critical findings. Two moderate React Router advisories need v7: one is SSR-only (not used), the other (backslash redirect) is closed by `safeAppPath`. | `npm audit` 2026-11-18 |

## 6. Known gaps

- **Write endpoints in the matrix test are checked structurally, not over HTTP.** Body validation runs before method security, so a generic empty body returns 400. Every guard exists and names real permissions, and the module tests exercise representative 403s.
- **Automated accessibility scanning finds only part of the issues.** Keyboard-only and screen-reader passes are still manual.
- **React Router v7 migration** clears the two moderate advisories; it is a framework upgrade and should be planned on its own.
- **Apache, the WinSW service and native MySQL have not run on a real server yet.** The scripts were rehearsed with a plain process and a containerised MySQL client (§7). The docs/08 §8 checks must be ticked on the target server.

## 7. Deployment rehearsal (2026-11-19)

The release was built by `build-release.ps1` (tests run), unpacked, and installed by its own scripts on the development machine, into a scratch database on the dev MySQL. Apache, WinSW and a native MySQL client aren't installed here, so:
- the backend ran as a plain process (`install-hms.ps1 -NoService`)
- the MySQL client was a shim into the container
- the production frontend was served by `npm run preview`, with Apache's security headers and its proxy pointed at the installed backend

| Step | Result |
|---|---|
| `create-database.ps1` | Database, app login and read-only backup login created; `backup.cnf` restricted to Administrators and SYSTEM |
| `install-hms.ps1` (first install, prod profile, no dev `.env`) | Health UP. The one-time admin password was removed from the service file after the first start, and the backend restarted without it. The database password is kept. |
| First sign-in (API and browser, under the production CSP) | `superadmin` was forced to change the password, then reached the dashboard of an empty hospital |
| `backup-hms.ps1` | Complete dump zipped with a checksum; log line OK |
| `restore-hms.ps1` | Checksum OK. Loaded into a new database: 12 migrations, latest V12, no ledger mismatches. A patient name in Sinhala with en dash and Ñ came back byte-identical. |
| `install-hms.ps1 -Upgrade` | Stopped, swapped in place, health UP |
| Upgrade with a broken jar | Detected (not UP within 3 minutes), rolled back by itself, previous version running |
| `recover-admin.ps1` path (real jar, dev DB) | Temporary password printed to the console only, exit 0; a non-admin account refused with exit 1 |

Defects the rehearsal found and fixed before release:
- Windows PowerShell 5.1 re-encoded piped SQL and added a byte-order mark. All SQL now goes through byte-exact `cmd` redirection; the old manual restore steps in docs/08 had the same flaw.
- Non-ASCII characters in the scripts would have printed garbled; all deploy scripts are now ASCII.
- The XML adapter rejected non-string values.
- The release build failed confusingly when a local backend or Vite server held its files; it now says so up front.
- Audit logging in its own transaction deadlocked against the recovered user row.
