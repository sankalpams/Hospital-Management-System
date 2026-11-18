# 06 — Implementation Plan

The work is incremental: every phase ends with a runnable application, green tests, and a commit. Each module is built as one vertical slice (migration, already in place → entity → repository → service with rules → controller + DTO → tests → React page), then verified before the next one starts.

## Definition of done (every module)

1. Service-layer business rules have unit tests, covering both the happy path and each rejected path.
2. The controller has integration tests (MockMvc + real MySQL): 200/201 for allowed roles, **403 for a role without the permission**, 400 for invalid input, and 409 for conflicts.
3. Every state change writes an audit entry, checked by a test.
4. The React page covers loading, empty, error and success states, and has permission-gated actions.
5. The design-system checklist (`design-system/hms/MASTER.md` §9) passes.
6. The API doc's status column is updated.

## Phases

| Phase | Scope | Exit criteria | Status |
|---|---|---|---|
| **1. Analysis** | Requirements, gaps, architecture, schema, API, roles, design system | Docs 01–06 + MASTER.md; migrations apply cleanly; DB integrity checks pass | ✅ |
| **2. Setup** | Spring Boot skeleton, security (session + CSRF + BCrypt + lockout), Flyway, bootstrap admin, audit service, error handling; React + Vite + Tailwind with design tokens; AppShell; login, change-password, dashboard shell; Docker MySQL; Apache config draft | Log in as the bootstrap admin through the UI; forced password change works; 401/403/CSRF behaviour tested | ✅ |
| **3. Core** | Users and roles admin, audit viewer, departments, patients (MRN, duplicate check, documents), doctors and schedules, appointments (availability, booking, state machine, walk-in, reschedule, calendar) | A receptionist registers a patient and books → confirms → checks in; a double booking is rejected (409) | ✅ |
| **4. Operations** | Notifications (moved from Phase 3), EMR (encounters, diagnoses, notes, vitals, allergies, prescriptions), inpatient (admit/transfer/discharge with bed locking), lab (catalogue → result → review), pharmacy (FEFO dispense, stock ledger, alerts), billing (charges → invoice → partial payments → receipt, void, refund), staff (employees, attendance, leave) | A full patient journey test: OPD visit → lab → prescription → dispense → invoice → payment; a concurrent dispense and bed allocation test | ✅ |
| **5. Analytics** | Role dashboards, the analytics endpoints in [04 §Analytics](04-api.md), Recharts visualisations, filters, CSV export, demo seed (≈12 months of synthetic activity) | Every chart is computed by SQL from seeded rows; numbers cross-checked against hand-written SQL in tests | ✅ |
| **6. QA** | Expand test coverage, a role × endpoint permission test (generated from the matrix), security tests (CSRF, session expiry, lockout, upload spoofing), Playwright smoke tests per role, responsive check at 375/768/1024/1440 | All tests green; a security checklist in docs/07 | ✅ |
| **7. Deployment** | Production build; Apache vhost + reverse proxy; Spring Boot as a Windows service (WinSW); native MySQL; backup scripts + Task Scheduler; recovery runbook | Fresh-machine walkthrough of docs/08 reaches `http://localhost/` and logs in | Rehearsed (docs/07 §7); walkthrough on the real server pending |

## Phase 2 task list (this round)

1. Backend skeleton: Maven wrapper, `pom.xml` (Spring Boot 3.4, Java 17), profiles, env-based config, `.env` import for dev.
2. Common: `ApiError` + `GlobalExceptionHandler`, `BusinessException` codes, request-ID filter, `AuditService`, `SequenceService`, `Clock` bean.
3. Identity: `User`/`Role`/`Permission` entities + repositories; `HmsUserDetailsService` (permissions as authorities).
4. Security: `SecurityFilterChain` with session auth, CSRF cookie, JSON 401/403 handlers, a lockout policy (`LoginAttemptService`), a must-change-password filter, Spring Session JDBC (30-min idle).
5. `AuthController`: csrf, login, logout, me, change-password. `BootstrapAdminRunner`.
6. Tests: login success and failure, lockout after 5, CSRF required, `/auth/me` without a session → 401, must-change-password blocks other endpoints, password policy.
7. Frontend: Vite + React 18 + Tailwind wired to `tokens.css`; UI kit (Button, Input, Card, Alert, Spinner, Badge); Axios client (XSRF, 401 handling); `AuthProvider`; `RequireAuth` / `RequirePermission`; AppShell (Sidebar driven by the permission-aware route config, Topbar); Login, Change Password, and a Dashboard shell that shows the user's role and permissions (no fake numbers; analytics arrive in Phase 5).
8. Contrast check script + token validation in `npm run check`.
9. Apache vhost draft (`deploy/apache/httpd-hms.conf`).
10. End-to-end verification in the browser: login → forced password change → dashboard.
