# Slice 0 — Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A running monorepo where all 7 demo users can log in. It includes:
- server-side sessions with idle and absolute timeouts;
- permission checks on every route;
- audit logging;
- admin screens for users, settings and the audit log;
- an app shell built on the design tokens.

Every later slice builds on this.

**Architecture:** npm-workspaces monorepo:
- `apps/server`: Express 5 API, layered as routes → controller → service → repository (Prisma).
- `apps/client`: React SPA (Vite).
- `packages/shared`: Zod schemas, the role/permission map and locale formatters, used by both apps.

PostgreSQL 16 runs in Docker: a dev database on port 5433 and a test database on 5434.

**Tech Stack:**

| Area | Choices |
|---|---|
| Language / runtime | TypeScript 5.6, Node 22 |
| Server | Express 5, Prisma 6, Zod 3, bcryptjs, helmet, express-rate-limit, pino, cookie-parser |
| Client | React 18.3, Vite 6, React Router 6, TanStack Query 5, React Hook Form 7, Tailwind 3.4, Radix UI |
| Testing | Vitest 3, Supertest, React Testing Library, Playwright |

**Spec:** `docs/superpowers/specs/2026-11-17-hms-design.md`. Slice 0 is defined in §9. It
covers §5, §6, §7.1 and §8.2, and lays the groundwork for §11.

**Branch:** do all work on `slice-0-foundation`, created from `main` before Task 1.

## Global Constraints

**Layering and conventions**
- Layer rule (§5.2): controllers never import Prisma; services never touch `req`/`res`. Each
  module lives in `apps/server/src/modules/<module>/` with `*.routes.ts`, `*.controller.ts`,
  `*.service.ts`, `*.repository.ts` and `*.test.ts`.
- API prefix: `/api/v1`. Error body: `{ "error": { "code": string, "message": string, "details"?: unknown } }`.
- Money is stored as integer cents and displayed as `Rs. 1,500.00`. Time zone `Asia/Colombo`.
  Date format `DD/MM/YYYY`; 24 h time.
- Records are never hard-deleted (§6.4): users are deactivated, not deleted.

**Security**
- Passwords: bcrypt cost 12. Policy: at least 8 characters, with at least one letter and one
  digit.
- Lockout: 5 failed logins → 15 min lockout.
- Session cookie `hms_sid`: `httpOnly`, `SameSite=Lax`, `Secure` only in production. Only the
  SHA-256 hash of the token is stored.
- Session lifetime: 15 min idle (sliding; the value comes from setting
  `sessionTimeoutMinutes`), 8 h absolute. The client shows a warning when 1 min is left.

**Audit**
- Actions: `CREATE`, `UPDATE`, `DELETE`, `LOGIN`, `LOGIN_FAILED`, `LOGOUT`, `VIEW_EMR`,
  `EXPORT`.
- Each entry is written in the same transaction as the change. Audit rows are never updated or
  deleted.

**UI**
- Design tokens (§8.2) are authoritative, light theme only. ui-ux-pro-max may advise on
  layout and accessibility but never changes a token.
- Accessibility target: WCAG 2.1 AA.

**Demo data**
- Users: `admin`, `doctor`, `nurse`, `reception`, `lab`, `pharmacist`, `accountant`, all with
  password `Password1!`.
- Hospital: "Lanka General Hospital (Demo)", Colombo. Currency LKR. Tax 0%.

## Review Focus

1. **Username typed with different case or spaces** (`" Admin "`) → logs in as `admin`.
   Usernames are trimmed and lowercased on create and on login. Test in Task 4.
2. **Unknown username vs. wrong password** → both return the same 401
   `"Invalid username or password"`. Failed attempts against an unknown username don't crash
   and don't reveal that the user is missing. Test in Task 4.
3. **Deactivated user who still has an open tab** → their next request gets 401, because
   deactivating a user revokes all their sessions. Test in Task 6.
4. **Admin locks themselves out** → an admin can't deactivate or demote their own account, or
   the last active admin (409 `LAST_ADMIN`). Test in Task 6.
5. **Absolute session limit** → a session that stays active is still cut off at 8 h, even
   though the idle timer keeps sliding. Test in Task 4.

---

### Task 1: Monorepo, Docker Postgres, tooling

**Files:**
- Create: `package.json` (workspaces `apps/*`, `packages/*`), `tsconfig.base.json`, `eslint.config.js`, `.prettierrc`, `docker-compose.yml`, `.env.example`, `apps/server/package.json`, `apps/server/tsconfig.json`, `apps/client/package.json` (filled in Task 7), `packages/shared/package.json`, `packages/shared/tsconfig.json`, `packages/shared/src/index.ts`
- Test: `packages/shared/src/smoke.test.ts`

**Interfaces:**
- Produces: these root scripts:
  - `npm run dev`: server and client together, via `concurrently`.
  - `npm test`: Vitest in every workspace.
  - `npm run typecheck`, `npm run lint`.
  - `npm run db:migrate`, `npm run db:reset`, `npm run db:seed`: forwarded to the server.
- Produces: `docker-compose.yml` services `db` (postgres:16, port `5433:5432`, db `hms`) and `db_test` (port `5434:5432`, db `hms_test`, `tmpfs` data). User/password `hms`/`hms`.
- Produces: `.env.example` with:
  ```
  DATABASE_URL=postgresql://hms:hms@localhost:5433/hms
  TEST_DATABASE_URL=postgresql://hms:hms@localhost:5434/hms_test
  PORT=4000
  CLIENT_ORIGIN=http://localhost:5173
  NODE_ENV=development
  ```
- Produces: shared package name `@hms/shared`, imported via TS path / workspace link.

- [ ] **Step 1:** Write `packages/shared/src/smoke.test.ts`, asserting `expect(VERSION).toBe('0.1.0')`. `VERSION` is exported from `index.ts`.
- [ ] **Step 2:** Run `npm install && npm test -w @hms/shared`. Expected: FAIL (VERSION not exported).
- [ ] **Step 3:** Export `VERSION = '0.1.0'`. Set `tsconfig.base.json` to `strict`, `"module": "NodeNext"` for server/shared, `"moduleResolution": "Bundler"` for client.
- [ ] **Step 4:** Run:
  - `npm test -w @hms/shared` → PASS;
  - `docker compose up -d` → both containers healthy (`docker compose ps`);
  - `npm run typecheck` → exits 0.
- [ ] **Step 5:** `git add -A && git commit -m "chore: monorepo scaffold, docker postgres, tooling"`

### Task 2: Shared — roles, permissions, schemas, formatters

**Files:**
- Create: `packages/shared/src/roles.ts`, `packages/shared/src/permissions.ts`, `packages/shared/src/schemas/auth.ts`, `packages/shared/src/schemas/user.ts`, `packages/shared/src/schemas/settings.ts`, `packages/shared/src/format.ts`
- Test: `packages/shared/src/permissions.test.ts`, `packages/shared/src/schemas/auth.test.ts`, `packages/shared/src/format.test.ts`

**Interfaces:**
- Produces: `ROLES = ['ADMIN','DOCTOR','NURSE','RECEPTIONIST','LAB_STAFF','PHARMACIST','ACCOUNTANT'] as const`, `type Role`.
- Produces: `PERMISSIONS` (const object of strings), `type Permission`, `ROLE_PERMISSIONS: Record<Role, readonly Permission[]>`, `can(role: Role, perm: Permission): boolean`.
  - The full set for **all** modules is defined now, from the §6.2 table, so later slices only use it.
  - Names follow `<area>:<action>`, e.g.:
    - `user:manage`, `settings:manage`, `audit:view`
    - `patient:view|create|update`, `patient_doc:view|upload`
    - `doctor:view|manage`, `schedule:manage_own`
    - `appointment:view|manage|update_status|check_in`
    - `emr:view|write|vitals`
    - `lab:order|view|collect|result|verify`
    - `pharmacy:view|manage|dispense`
    - `ipd:view|admit|discharge|transfer`
    - `billing:view|invoice|payment|void`
    - `staff:manage|self`
    - `report:all|own|appointment|patient|lab|pharmacy|revenue`
- Produces: Zod `loginSchema {username, password}`, `passwordSchema` (policy), `changePasswordSchema {currentPassword, newPassword}`, `createUserSchema {username, fullName, email?, role}`, `updateUserSchema` (partial `fullName`, `email`, `role`, `isActive`), `settingsSchema {hospitalName, hospitalAddress, hospitalPhone, currencyCode, currencySymbol, taxRatePercent, sessionTimeoutMinutes, expiryAlertDays}`.
- Produces: `formatMoney(cents: number, symbol = 'Rs.'): string`, `formatDate(d: Date | string): string` (DD/MM/YYYY in Asia/Colombo), `formatDateTime(d): string` (DD/MM/YYYY HH:mm).

- [ ] **Step 1: Write failing tests**
```ts
expect(can('ADMIN','user:manage')).toBe(true);
expect(can('DOCTOR','user:manage')).toBe(false);
expect(can('PHARMACIST','pharmacy:dispense')).toBe(true);
expect(can('RECEPTIONIST','emr:view')).toBe(false);
for (const r of ROLES) expect(can(r,'staff:self')).toBe(true);
expect(passwordSchema.safeParse('abcdefgh').success).toBe(false);   // no digit
expect(passwordSchema.safeParse('abc1234').success).toBe(false);    // 7 chars
expect(passwordSchema.safeParse('Password1!').success).toBe(true);
expect(loginSchema.parse({username:'  Admin ',password:'x'}).username).toBe('admin');
expect(formatMoney(150000)).toBe('Rs. 1,500.00');
expect(formatMoney(0)).toBe('Rs. 0.00');
expect(formatDate('2026-11-16T20:00:00Z')).toBe('17/11/2026'); // Colombo is UTC+5:30
```
- [ ] **Step 2:** Run `npm test -w @hms/shared`. Expected: FAIL.
- [ ] **Step 3:** Implement. Usernames go through `z.string().trim().toLowerCase()` and must match `^[a-z0-9._-]{3,32}$`. `formatMoney` uses `Intl.NumberFormat('en-LK', {minimumFractionDigits: 2})`. Dates use `Intl.DateTimeFormat('en-GB', {timeZone: 'Asia/Colombo'})`.
- [ ] **Step 4:** Run `npm test -w @hms/shared`. Expected: PASS.
- [ ] **Step 5:** Commit `feat(shared): roles, permission map, auth/user/settings schemas, LKR formatters`.

### Task 3: Server skeleton, Prisma schema, test DB harness

**Files:**
- Create: `apps/server/prisma/schema.prisma`, `apps/server/src/app.ts`, `apps/server/src/server.ts`, `apps/server/src/config.ts`, `apps/server/src/db.ts`, `apps/server/src/http/errors.ts`, `apps/server/src/http/errorHandler.ts`, `apps/server/src/http/validate.ts`, `apps/server/src/modules/health/health.routes.ts`, `apps/server/vitest.config.ts`, `apps/server/test/globalSetup.ts`, `apps/server/test/helpers.ts`
- Test: `apps/server/src/modules/health/health.test.ts`, `apps/server/src/http/errorHandler.test.ts`

**Interfaces:**
- Produces Prisma models (snake_case tables via `@@map`, cuid ids, `createdAt`/`updatedAt`):
  - `Role {id, name (unique, Role enum value), description}`.
  - `User {id, username unique, passwordHash, fullName, email?, roleId, isActive=true, mustChangePassword=false, failedLoginCount=0, lockedUntil?, lastLoginAt?}`.
  - `Session {id, tokenHash unique, userId, createdAt, lastSeenAt, idleExpiresAt, absoluteExpiresAt, ip?, userAgent?}`, with an index on `userId`.
  - `AuditLog {id, userId?, action (AuditAction enum), entityType, entityId?, before Json?, after Json?, ip?, createdAt}`, with indexes on `createdAt`, `(entityType, entityId)` and `userId`.
  - `Setting {key unique, value Json}`.
- Produces: `createApp(): Express`. It sets up `/api/v1`, helmet, json (1 mb), cookie-parser, CORS for `CLIENT_ORIGIN` with credentials, and the error handler.
- Produces: `class AppError(status: number, code: string, message: string, details?: unknown)` and the helpers `badRequest`, `unauthenticated`, `forbidden`, `notFound`, `conflict`.
- Produces: `validate(schema, 'body' | 'query' | 'params')`, middleware that puts the parsed value back on `req`. A `ZodError` becomes a 400 `VALIDATION_ERROR` with the field errors in `details`.
- Produces: `prisma` (singleton in `db.ts`); `resetDb()` in `test/helpers.ts`, which truncates every table except `_prisma_migrations` with `RESTART IDENTITY CASCADE`.
- Test harness:
  - `globalSetup` sets `DATABASE_URL = TEST_DATABASE_URL` and runs `prisma migrate reset --force --skip-seed`.
  - `vitest.config.ts` sets `fileParallelism: false`.

- [ ] **Step 1:** Write tests:
  - `GET /api/v1/health` → `200 {status:'ok', db:'up'}`;
  - unknown route → `404 {error:{code:'NOT_FOUND'}}`;
  - a test-only route that throws a plain `Error` → `500 {error:{code:'INTERNAL'}}`, with no stack trace in the body.
- [ ] **Step 2:** Run `npm test -w apps/server`. Expected: FAIL.
- [ ] **Step 3:** Implement. Create the first migration: `npx prisma migrate dev --name init`. The health check runs `SELECT 1`.
- [ ] **Step 4:** Run `npm test -w apps/server`. Expected: PASS.
- [ ] **Step 5:** Commit `feat(server): express app skeleton, prisma schema, error handling, test harness`.

### Task 4: Authentication and sessions

**Files:**
- Create: `apps/server/src/modules/auth/{auth.routes,auth.controller,auth.service,session.service,session.repository,password}.ts`, `apps/server/src/modules/settings/settings.service.ts` (read-only part: `getSetting`), `apps/server/src/modules/audit/audit.service.ts` (write part), `apps/server/src/http/requireAuth.ts`, `apps/server/src/lib/clock.ts`
- Test: `apps/server/src/modules/auth/auth.test.ts`, `apps/server/src/modules/auth/session.service.test.ts`

**Interfaces:**
- Produces: `clock.now(): Date`, overridable in tests through `setNow(d)` and `resetClock()`. All expiry logic uses `clock.now()`.
- Produces: `hashPassword(pw): Promise<string>` and `verifyPassword(pw, hash): Promise<boolean>`. Both use bcryptjs with cost 12; tests set `BCRYPT_COST=4`.
- Produces: `sessionService`:
  - `create(userId, meta) → {token, session}`;
  - `validateAndTouch(token) → {session, user} | null`, which slides the idle expiry and deletes expired sessions;
  - `revoke(token)`;
  - `revokeAllForUser(userId)`.
- Produces: `getSetting<K>(key): Promise<SettingsValue[K]>`, which falls back to defaults: `sessionTimeoutMinutes=15`, `expiryAlertDays=90`, `taxRatePercent=0`, `currencyCode='LKR'`, `currencySymbol='Rs.'`.
- Produces: `auditService.record(tx, {userId?, action, entityType, entityId?, before?, after?, ip?})`.
- Produces: `requireAuth` middleware. It sets `req.user: {id, username, fullName, role: Role, mustChangePassword}` and `req.sessionToken`, and sends response header `X-Session-Expires-At` (ISO), the earlier of the idle and absolute expiry. When `mustChangePassword` is set, every route except `GET /auth/me`, `POST /auth/change-password` and `POST /auth/logout` returns 403 `PASSWORD_CHANGE_REQUIRED`.
- Routes:
  - `POST /api/v1/auth/login` → 200 `{user}` and sets the cookie. Rate limit: 20 requests/min per IP.
  - `POST /auth/logout` → 204 and clears the cookie.
  - `GET /auth/me` → `{user, expiresAt}`.
  - `POST /auth/change-password` → 204.
- Error codes:
  - 401 `INVALID_CREDENTIALS`: `"Invalid username or password"`.
  - 423 `ACCOUNT_LOCKED`: `"Account locked. Try again in N minutes."`
  - 403 `ACCOUNT_INACTIVE`.
  - 401 `UNAUTHENTICATED`.

- [ ] **Step 1: Write failing tests** (`auth.test.ts`, Supertest agent with a cookie jar)
```ts
it('logs in with trimmed/uppercased username and sets httpOnly hms_sid', ...)       // ' Admin ' → 200
it('returns identical 401 for unknown user and wrong password', ...)                 // same body
it('locks after 5 failures for 15 min, then allows login after', ...)                // 6th → 423; setNow(+15m) → 200
it('resets failedLoginCount on success', ...)
it('rejects inactive user with 403 ACCOUNT_INACTIVE', ...)
it('GET /auth/me 401 without cookie', ...)
it('expires session after 15 idle minutes', ...)                                     // setNow(+15m+1s) → 401
it('slides idle expiry on activity', ...)                                            // request every 10m for 1h → 200
it('enforces 8h absolute limit even with activity', ...)                             // every 10m until 8h+1s → 401
it('uses sessionTimeoutMinutes setting', ...)                                        // setting 30 → still valid at +20m
it('logout revokes session; reused cookie → 401', ...)
it('change-password requires correct current password and policy; clears mustChangePassword', ...)
it('mustChangePassword user gets 403 PASSWORD_CHANGE_REQUIRED on other routes', ...)
it('writes LOGIN, LOGIN_FAILED (userId null for unknown user, username in after), LOGOUT audit rows', ...)
it('stores only sha256 hash of token in sessions table', ...)
```
- [ ] **Step 2:** Run `npm test -w apps/server -- auth`. Expected: FAIL.
- [ ] **Step 3:** Implement.
  - Tokens are `crypto.randomBytes(32).toString('base64url')`; the table stores the `sha256` hex.
  - For an unknown username, still run `verifyPassword` against a fixed dummy hash, so the timing matches.
  - Failed-count and lockout updates happen in the same transaction as the `LOGIN_FAILED` audit row.
- [ ] **Step 4:** Run `npm test -w apps/server`. Expected: PASS (all).
- [ ] **Step 5:** Commit `feat(auth): login/logout, sliding+absolute sessions, lockout, change password, audit`.

### Task 5: Permission guard and audit query API

**Files:**
- Create: `apps/server/src/http/requirePermission.ts`, `apps/server/src/modules/audit/{audit.routes,audit.controller,audit.repository}.ts`
- Modify: `apps/server/src/modules/audit/audit.service.ts` (add `list`)
- Test: `apps/server/src/http/requirePermission.test.ts`, `apps/server/src/modules/audit/audit.test.ts`

**Interfaces:**
- Consumes: `can()` (Task 2), `requireAuth` (Task 4), `auditService.record` (Task 4).
- Produces: `requirePermission(...perms: Permission[])`, which passes if the user has **any** of them; otherwise it returns 403 `FORBIDDEN`. Also `requireAnyAuth = requireAuth`. Every non-auth route in every slice uses `requireAuth` followed by `requirePermission`.
- Produces: `GET /api/v1/audit-logs?userId&action&entityType&entityId&from&to&page=1&pageSize=50` (max 200), protected by `audit:view`. Returns `{items, page, pageSize, total}`, newest first; each item includes `user: {username, fullName}`.
- Produces: the shared pagination helper `parsePaging(query)` and response type `Paged<T>` in `apps/server/src/http/paging.ts`, reused by every list endpoint.

- [ ] **Step 1:** Write tests:
  - a test route guarded by `requirePermission('user:manage')`: `admin` gets 200, `doctor` gets 403 `FORBIDDEN`, no cookie gets 401;
  - the audit list filters by `action=LOGIN` and by a date range, pages correctly, and a doctor gets 403;
  - there is no update or delete route for audit logs (PUT/DELETE → 404).
- [ ] **Step 2:** Run the tests. Expected: FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run the tests. Expected: PASS.
- [ ] **Step 5:** Commit `feat(rbac): permission guard, audit log query API, paging helper`.

### Task 6: Users admin API and settings API

**Files:**
- Create: `apps/server/src/modules/users/{users.routes,users.controller,users.service,users.repository}.ts`, `apps/server/src/modules/settings/{settings.routes,settings.controller,settings.repository}.ts`
- Modify: `apps/server/src/modules/settings/settings.service.ts` (add `getAll`, `update`)
- Test: `apps/server/src/modules/users/users.test.ts`, `apps/server/src/modules/settings/settings.test.ts`

**Interfaces:**
- Users routes. All require `user:manage`.
  - `GET /api/v1/users?search&role&isActive&page&pageSize`: never returns `passwordHash`.
  - `POST /users` with `createUserSchema`: the server generates a temporary password and returns `{user, temporaryPassword}` **once**. It sets `mustChangePassword=true` and writes an audit `CREATE`.
  - `PATCH /users/:id` with `updateUserSchema`: writes an audit `UPDATE` with the before/after diff.
  - `POST /users/:id/reset-password` → `{temporaryPassword}`, sets `mustChangePassword`, revokes all of that user's sessions, writes an audit `UPDATE`.
- Rules:
  - A duplicate username returns 409 `USERNAME_TAKEN`.
  - Setting `isActive=false` revokes all of that user's sessions.
  - An admin can't change their **own** `role` or `isActive`, and no change may leave zero active ADMIN users. Both return 409 `LAST_ADMIN`.
- Settings routes:
  - `GET /api/v1/settings/public`: any signed-in user. Returns `{hospitalName, hospitalAddress, hospitalPhone, currencyCode, currencySymbol, sessionTimeoutMinutes}`.
  - `GET /settings` and `PUT /settings`: require `settings:manage`. `PUT` validates with `settingsSchema` (`sessionTimeoutMinutes` 5–120, `taxRatePercent` 0–100, `expiryAlertDays` 1–365) and writes an audit `UPDATE`.
- Produces: `generateTemporaryPassword(): string`. It returns 12 characters that satisfy `passwordSchema`.

- [ ] **Step 1:** Write tests covering every rule above. These include:
  - `'deactivated user with open session gets 401 on next request'`;
  - `'admin cannot deactivate self → 409 LAST_ADMIN'`;
  - `'cannot demote the only active admin'`;
  - `'temporary password logs in and forces change'`;
  - `'settings PUT rejects sessionTimeoutMinutes 3'`;
  - `'non-admin gets 403 on PUT /settings but 200 on /settings/public'`.
- [ ] **Step 2:** Run the tests. Expected: FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run the tests. Expected: PASS.
- [ ] **Step 5:** Commit `feat(users,settings): user admin, password reset, settings API with guards`.

### Task 7: Seed framework, roles, demo users, settings

**Files:**
- Create: `apps/server/prisma/seed/index.ts`, `apps/server/prisma/seed/context.ts`, `apps/server/prisma/seed/modules/core.ts`, `apps/server/prisma/seed/data/README.md`
- Modify: `apps/server/package.json` (`prisma.seed`, `db:seed`, `db:reset` scripts)
- Test: `apps/server/prisma/seed/seed.test.ts`

**Interfaces:**
- Produces: `type SeedScale = 'small' | 'large'`. It's read from `SEED_SCALE` and defaults to `large`; tests use `small`.
- Produces: `SeedContext {prisma, faker (seeded 20261117), scale, now: Date, log(msg)}`.
- Produces: `SeedModule = {name: string, run(ctx): Promise<void>}`. `index.ts` runs an ordered `modules: SeedModule[]` array and prints the time each one takes. Later slices add modules to this array.
- `core` module seeds:
  - the 7 roles;
  - the settings (hospital "Lanka General Hospital (Demo)", address "No. 10, Kynsey Road, Colombo 08", phone "+94112345678", LKR / "Rs.", tax 0, timeout 15, expiry window 90);
  - the 7 demo users with Sri Lankan full names: admin "Nimal Perera", doctor "Dr. Kumari Fernando", nurse "Tharushi Jayasinghe", reception "Mohamed Rizwan", lab "Suresh Kumar", pharmacist "Dilani Wickramasinghe", accountant "Ruwan Bandara". All use password `Password1!` and `mustChangePassword=false`.
- `db:reset` = `prisma migrate reset --force` (it runs the seed).

- [ ] **Step 1:** Write a test that runs the seed against the test DB with `SEED_SCALE=small` and checks:
  - 7 roles;
  - 7 users, one per role;
  - each user can log in via Supertest with `Password1!`;
  - the `hospitalName` setting equals `"Lanka General Hospital (Demo)"`;
  - running the seed twice is idempotent (upserts), with the counts unchanged.
- [ ] **Step 2:** Run the test. Expected: FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run the test, then `npm run db:reset` against the dev DB. Expected: PASS, and the seed log lists the `core` module.
- [ ] **Step 5:** Commit `feat(seed): seed framework with scale, core roles/users/settings`.

### Task 8: Client foundation — tokens, API client, auth, login, app shell

Before building screens, consult **ui-ux-pro-max** about the login and app-shell layout, keyboard and focus order, and error presentation. Its advice must stay within the tokens below.

**Files:**
- Create:
  - setup: `apps/client/{index.html,vite.config.ts,tailwind.config.ts,postcss.config.js,tsconfig.json}`
  - styles and entry: `apps/client/src/styles/tokens.css`, `apps/client/src/styles/global.css`, `apps/client/src/main.tsx`, `apps/client/src/App.tsx`
  - lib: `apps/client/src/lib/api.ts`, `apps/client/src/lib/queryClient.ts`
  - auth: `apps/client/src/auth/{AuthProvider.tsx,useAuth.ts,RequireAuth.tsx,RequirePermission.tsx}`
  - components: `apps/client/src/components/ui/{Button,Input,Label,FormField,Card,Badge,Dialog,Table,Select,Spinner,EmptyState,Toast}.tsx`
  - layout: `apps/client/src/layout/{AppShell,Sidebar,TopBar}.tsx`, `apps/client/src/layout/nav.ts`
  - pages: `apps/client/src/pages/{LoginPage,HomePage,NotFoundPage,ForbiddenPage}.tsx`
- Test: `apps/client/src/pages/LoginPage.test.tsx`, `apps/client/src/layout/Sidebar.test.tsx`, `apps/client/src/lib/api.test.ts`

**Interfaces:**
- `tokens.css` defines CSS variables with the exact values from design §8.2:
  - colour: `--color-primary #0F766E`, `--color-primary-hover #115E59`, `--color-primary-subtle #CCFBF1`, `--color-text #0F172A`, `--color-muted #475569`, `--color-border #E2E8F0`, `--color-bg #F8FAFC`, `--color-surface #FFFFFF`, `--color-success #15803D`, `--color-warning #B45309`, `--color-danger #B91C1C`, `--color-info #1D4ED8`;
  - radius: `--radius-control 6px`, `--radius-card 10px`;
  - type: font Inter, falling back to system-ui; body 14px with line-height 1.5.

  `tailwind.config.ts` maps theme colours and radii to these variables and uses Tailwind's default 4px spacing scale. Components use only Tailwind classes backed by tokens; there are no raw hex values outside `tokens.css`.
- Vite dev server on port 5173 proxies `/api` to `http://localhost:4000`.
- `api<T>(path, init?)`:
  - Uses `fetch` with `credentials: 'include'`.
  - Parses the error body into `ApiError {status, code, message, details}`.
  - On a 401 it calls the registered `onUnauthenticated` handler.
  - Reads `X-Session-Expires-At` and passes it to `onSessionExpiry(date)`.
- `AuthProvider` exposes `{user, expiresAt, login(username, password), logout(), refresh(), can(perm)}`. It loads the user from `GET /auth/me` on startup.
- `nav.ts` holds `NAV_ITEMS: {label, path, icon, permission?}[]`. Slice 0 entries are Home, Users (`user:manage`), Settings (`settings:manage`) and Audit Log (`audit:view`). Later slices append their items. The sidebar shows only items allowed for the current role.
- Routes: `/login`, `/` (home: welcome card with the user's name, role and hospital name), `/403`, `*`.

- [ ] **Step 1:** Write failing tests (RTL + `msw` for the API):
  - The login form has labelled username and password fields.
  - Submitting empty fields shows inline errors.
  - A 401 shows `"Invalid username or password"` in an `role="alert"` region.
  - A 423 shows the lockout message.
  - A successful login navigates to `/`.
  - The sidebar shows Users for ADMIN but not for DOCTOR.
  - `api()` turns an error body into an `ApiError` with the right `code`.
- [ ] **Step 2:** Run `npm test -w apps/client`. Expected: FAIL.
- [ ] **Step 3:** Implement.
  - The shell is responsive: a fixed sidebar at ≥1024px, a drawer below that.
  - The top bar shows the hospital name, the user's name and role, and a user menu (Change password, Logout).
  - Visible focus ring: 2px primary with a 2px offset.
- [ ] **Step 4:** Run `npm test -w apps/client`. Expected: PASS. Then check by hand with `npm run dev`: log in as `admin` and see the shell.
- [ ] **Step 5:** Commit `feat(client): tokens, api client, auth provider, login page, app shell`.

### Task 9: Client — session timeout, change password, admin screens

Consult **ui-ux-pro-max** on the timeout dialog, the forced password-change flow and the admin data tables (including empty and loading states and accessible tables).

**Files:**
- Create: `apps/client/src/auth/SessionTimeoutWarning.tsx`, `apps/client/src/pages/ChangePasswordPage.tsx`, `apps/client/src/pages/admin/{UsersPage,UserFormDialog,SettingsPage,AuditLogPage}.tsx`, `apps/client/src/components/DataTable.tsx`, `apps/client/src/components/Pagination.tsx`
- Test: `apps/client/src/auth/SessionTimeoutWarning.test.tsx`, `apps/client/src/pages/ChangePasswordPage.test.tsx`, `apps/client/src/pages/admin/UsersPage.test.tsx`

**Interfaces:**
- Consumes: `AuthProvider.expiresAt` and `refresh()` (Task 8), and the APIs from Tasks 4–6.
- `SessionTimeoutWarning`:
  - When 60s or less remain, it opens a modal dialog with a live countdown and two buttons: "Stay signed in" (calls `refresh()`, i.e. `GET /auth/me`) and "Log out".
  - At 0 it logs out and redirects to `/login?reason=timeout`. The login page then shows "Your session expired. Please sign in again."
- Forced change: if `user.mustChangePassword`, every route redirects to `/change-password`. The page shows the policy hint, a current-password field and a confirm-new-password field.
- `DataTable<T>` takes `{columns, rows, loading, emptyMessage}` and is reused by every later list screen. `Pagination` takes `{page, pageSize, total, onChange}`.
- `UsersPage`: search, a role filter, a create dialog that shows the temporary password once with a copy button, an edit dialog, a reset-password action with an in-page confirm dialog, and an activate/deactivate action. The 409 `LAST_ADMIN` error appears as a toast.
- `SettingsPage`: a form for every `settingsSchema` field.
- `AuditLogPage`: filters (user, action, entity type, date range) and an expandable before/after JSON for each row.

- [ ] **Step 1:** Write failing tests:
  - The warning appears when `expiresAt` is 59s away (fake timers). "Stay signed in" calls `/auth/me`.
  - When the countdown reaches 0, the app navigates to `/login?reason=timeout`.
  - Change password shows a mismatch error when confirm ≠ new.
  - A mustChangePassword user visiting `/` is redirected.
  - The users page shows the temporary password after a create.
- [ ] **Step 2:** Run `npm test -w apps/client`. Expected: FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run `npm test -w apps/client`. Expected: PASS.
- [ ] **Step 5:** Commit `feat(client): session timeout warning, change password, users/settings/audit admin`.

### Task 10: E2E smoke, README, slice verification

**Files:**
- Create: `playwright.config.ts`, `e2e/auth.spec.ts`, `README.md`
- Modify: root `package.json` (add an `e2e` script)

**Interfaces:**
- Playwright `webServer` starts the server against the test DB, seeded with `SEED_SCALE=small`, and starts the client.
- The README covers:
  - prerequisites: Node 22 and Docker;
  - setup: `cp .env.example .env`, `docker compose up -d`, `npm install`, `npm run db:reset`, `npm run dev`;
  - a table of the demo logins;
  - the scripts;
  - the architecture layers (§5.2).

- [ ] **Step 1:** Write `e2e/auth.spec.ts`:
  - For each of the 7 demo users: log in → home shows their full name → log out → back at `/login`.
  - A wrong password shows the alert.
  - Admin creates a user → logs out → logs in as the new user with the temporary password → is forced to change password → reaches home.
- [ ] **Step 2:** Run `npm run e2e`. Expected: FAIL until the config is complete; after that, PASS.
- [ ] **Step 3:** Write the README.
- [ ] **Step 4:** Full verification: `npm run lint && npm run typecheck && npm test && npm run e2e`. Expected: everything exits 0.
- [ ] **Step 5:** Commit `test(e2e): auth smoke for all roles; docs: README`.

---

## After the slice
1. **superpowers:requesting-code-review** on the `slice-0-foundation` branch.
2. **superpowers:verification-before-completion**.
3. **superpowers:finishing-a-development-branch**: merge to `main`.
4. `/graphify . --update`.
