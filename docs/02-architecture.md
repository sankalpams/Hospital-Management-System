# 02 — System Architecture

## 1. Three tiers

```
 Browser (Chrome / Edge / Firefox on Windows 10/11)
   │  http://localhost/
   ▼
 ┌──────────────────────────── Apache HTTP Server 2.4 (Windows service) ───────────────────────────┐
 │  /           → C:\hms\frontend\dist   (React production build, SPA fallback to index.html)      │
 │  /api/*      → ProxyPass http://127.0.0.1:8080/api/   (mod_proxy + mod_proxy_http)              │
 │  Security headers (CSP, X-Frame-Options, nosniff, Referrer-Policy), gzip, cache for /assets     │
 └─────────────────────────────────────────────────────────────────────────────────────────────────┘
   │ 127.0.0.1:8080 (not exposed beyond localhost)
   ▼
 ┌──────────────── Spring Boot 3.4 / Java 17 (Windows service via WinSW) ────────────────┐
 │  Controller  →  Service (@Transactional, business rules)  →  Repository (JPA)         │
 │  DTOs + Bean Validation · GlobalExceptionHandler · AuditService · Spring Security     │
 └───────────────────────────────────────────────────────────────────────────────────────┘
   │ JDBC (HikariCP)
   ▼
 MySQL 8.0  (InnoDB, utf8mb4, Flyway-managed schema, binlog on for point-in-time recovery)
```

The PDF's four layers map onto this as follows:

| PDF layer | Implementation |
|---|---|
| Presentation | React (pages and components) |
| Application | Spring MVC controllers + DTO mapping + validation |
| Business logic | `@Service` classes (state machines, money, stock, bed rules) |
| Database | JPA repositories + MySQL |

## 2. Repository layout

```
HMS/
├── backend/                     Spring Boot (Maven wrapper: mvnw.cmd)
│   └── src/main/java/lk/hms/
│       ├── HmsApplication.java
│       ├── config/              Security, CORS, Jackson, OpenAPI, clock
│       ├── common/              ApiError, exceptions, PageResponse, audit, sequences
│       └── modules/
│           ├── auth/            login, logout, me, change-password
│           ├── users/           users, roles, permissions
│           ├── departments/
│           ├── patients/
│           ├── doctors/
│           ├── appointments/
│           ├── emr/             encounters, diagnoses, vitals, allergies, notes
│           ├── inpatient/       wards, rooms, beds, admissions
│           ├── laboratory/
│           ├── pharmacy/
│           ├── billing/
│           ├── staff/           employees, attendance, leave
│           ├── notifications/
│           └── analytics/
│       Each module: controller/ · service/ · repository/ · entity/ · dto/ · mapper/
│   └── src/main/resources/
│       ├── application.yml, application-dev.yml, application-prod.yml
│       └── db/migration/        Flyway V1__… V9__ (schema), R__seed_reference.sql
├── frontend/                    React 18 + Vite + Tailwind
│   └── src/
│       ├── api/                 axios client + one module per backend resource
│       ├── auth/                AuthProvider, RequirePermission, usePermission
│       ├── components/ui/       Button, Input, Select, Card, Badge, DataTable, Modal, ConfirmDialog, Toast …
│       ├── components/layout/   AppShell, Sidebar, Topbar, PageHeader
│       ├── features/<module>/   pages + hooks (React Query) per module, no business rules
│       ├── lib/                 formatters (LKR, dates), validators shared with forms
│       └── styles/              tokens.css (generated), index.css
├── design-system/hms/           MASTER.md + tokens.json (single source of truth)
├── database/                    ERD notes, backup/restore scripts
├── deploy/
│   ├── apache/                  httpd-hms.conf (vhost), instructions
│   ├── windows/                 WinSW service XML, install scripts
│   └── docker/                  docker-compose.dev.yml (MySQL for development)
└── docs/                        01 … 0n (analysis, architecture, schema, API, roles, plan, guides)
```

## 3. Backend conventions

- **Controllers** are thin: they validate input (`@Valid`), call one service method, and return a DTO. Entities are never returned.
- **Services** own the transactions. Workflows that change several rows (book appointment, admit, dispense, pay) are one `@Transactional` method. They take pessimistic locks (`@Lock(PESSIMISTIC_WRITE)`) where concurrent writes are possible.
- **Repositories** are Spring Data JPA. Analytics use native SQL aggregate queries mapped to projections; nothing is aggregated in Java loops.
- **Optimistic locking**: an `@Version` column on mutable aggregates (patients, appointments, invoices, medicines) stops lost updates. A conflict returns HTTP 409 with a "record was changed by someone else" error.
- **Errors**: one `GlobalExceptionHandler` turns every error into a body like:
  ```json
  { "status": 409, "code": "APPOINTMENT_SLOT_TAKEN", "message": "…", "fieldErrors": [...], "requestId": "…", "timestamp": "…" }
  ```
  Stack traces and SQL never reach the client.
- **Request ID**: a filter assigns `X-Request-Id`, puts it in the logging MDC, and returns it in the response.
- **Time**: everything is stored as `DATETIME` in UTC. The hospital time zone (`Asia/Colombo`) is a setting used when working out "today" and slot times. An injectable `Clock` keeps tests deterministic.
- **Money**: `BigDecimal`, scale 2, `RoundingMode.HALF_UP`. Never `double`.
- **IDs**: `BIGINT` auto-increment internally. Human-facing numbers (MRN, invoice and receipt numbers, employee number) come from a `sequences` table, locked per key and per year.

## 4. Security architecture

| Concern | Control |
|---|---|
| Authentication | Username + password → `DaoAuthenticationProvider` with BCrypt (cost 12) |
| Session | Server-side session in MySQL (Spring Session JDBC). The cookie `HMS_SESSION` is HttpOnly, SameSite=Strict, and Secure in production. 30-min idle timeout. A new session ID is issued at login (session fixation protection). |
| CSRF | The token is sent as an `XSRF-TOKEN` cookie; Axios sends it back automatically as the `X-XSRF-TOKEN` header. Required on every state-changing request. |
| Authorisation | Permission strings (`patient:create`, `invoice:void` …) are granted to roles in the DB and loaded as authorities. `@PreAuthorize("hasAuthority('patient:create')")` guards every endpoint. Record-level checks (doctor sees own encounters, nurse sees own ward) live in the services. |
| Brute force | 5 failures in 15 minutes locks the account for 15 minutes (`login_attempts` table, checked per account) |
| Passwords | Policy enforced server-side. `must_change_password` blocks every other endpoint until the password is changed. |
| Input | Bean Validation on DTOs plus checks in services. JPA parameter binding throughout; native queries only use bound parameters. No string-concatenated SQL. |
| Output | React escapes by default. No `dangerouslySetInnerHTML`. Apache sends a strict CSP. |
| Uploads | Allowed types are checked by magic bytes; 10 MB limit; random file names; stored outside the web root; served only via an authorised endpoint with `Content-Disposition: attachment` and `nosniff` |
| CORS | Not needed in production (same origin through Apache). In dev, Vite proxies `/api`. CORS is limited to the origins configured in `HMS_CORS_ORIGINS`. |
| Secrets | Loaded only from environment variables (`HMS_DB_URL`, `HMS_DB_USERNAME`, `HMS_DB_PASSWORD`, …). `.env.example` holds placeholders. `.env` is git-ignored. |
| Audit | `audit_logs` records actor, action, entity, entity ID, before/after JSON, IP, user agent and request ID. Written in the same transaction as the change. Sensitive reads (EMR view, break-the-glass) are logged too. |
| Actuator | Only `health` is exposed, and only on localhost |

## 5. Frontend conventions

- **Data fetching**: React Query handles server state: caching, loading and error states, and invalidation after changes. Axios is created in one place, sets `withCredentials`, and handles XSRF and 401 (redirect to login, with a "session expired" message).
- **No business rules in components.** The server is the authority. The client only mirrors validation for user experience (required fields, formats) and reads permissions to decide what to show.
- **Navigation**: routes are declared with a `permission` field. The sidebar and the route guard both read the same list, so a hidden menu item is also a blocked route.
- **Design system**: components use only the semantic Tailwind classes generated from `design-system/hms/tokens.json`.

## 6. Environments

| Profile | Database | Frontend | Notes |
|---|---|---|---|
| `dev` | MySQL 8 in Docker on port **3307** (`deploy/docker/docker-compose.dev.yml`) | Vite dev server on 5173, proxying `/api` to 8080 | Seed data loaded; verbose SQL logging off by default |
| `test` | Testcontainers MySQL 8 (fallback: the dev DB with a separate schema) | Vitest + Testing Library | Run on every change |
| `prod` | Native MySQL 8 Windows service on 3306 | Apache serves `dist/` | `HMS_*` environment variables; `ddl-auto=validate`; Flyway migrates on start |
