# Hospital Management System (HMS)

A hospital management system built as a TypeScript monorepo (npm workspaces): an Express + Prisma
API, a React + Vite client, and a shared package of types, schemas and permissions. This is the
Slice 0 foundation: authentication, role-based access, users, settings and audit log.

## Prerequisites

- Node.js 22
- Docker (for PostgreSQL 16)

## Setup

```bash
cp .env.example .env
docker compose up -d      # dev DB on :5433, test DB on :5434
npm install
npm run db:reset          # run this yourself: applies migrations and seeds demo data
npm run dev               # API on :4000, client on :5173
```

Open http://localhost:5173 and sign in with one of the demo logins below.

## Demo logins

All demo users share the password `Password1!`.

| Username     | Name                  | Role          |
| ------------ | --------------------- | ------------- |
| `admin`      | Nimal Perera          | Administrator |
| `doctor`     | Dr. Kumari Fernando   | Doctor        |
| `nurse`      | Tharushi Jayasinghe   | Nurse         |
| `reception`  | Mohamed Rizwan        | Receptionist  |
| `lab`        | Suresh Kumar          | Lab staff     |
| `pharmacist` | Dilani Wickramasinghe | Pharmacist    |
| `accountant` | Ruwan Bandara         | Accountant    |

Re-running the seed overwrites the hospital settings and the demo users' names and roles (it does
not reset their passwords). The seed refuses to run when `NODE_ENV=production` unless
`SEED_ALLOW_PRODUCTION=1` is set. `SEED_SCALE` accepts `small` or `large` (case-insensitive,
default `large`); any other value is an error.

## Scripts

Run from the repository root.

| Script                   | What it does                                                                                       |
| ------------------------ | -------------------------------------------------------------------------------------------------- |
| `npm run dev`            | Starts the API (`:4000`) and the client (`:5173`) together                                         |
| `npm test`               | Runs the Vitest suites (shared, server, client). The server tests use the test DB (`hms_test`)     |
| `npm run e2e`            | Playwright auth smoke (Chromium). Uses the test DB and its own ports (API `:4100`, client `:5273`) |
| `npm run db:e2e-prepare` | Test DB only: `migrate deploy`, truncate all tables, seed with `SEED_SCALE=small`. Run by `e2e`    |
| `npm run db:migrate`     | Creates and applies a new dev migration (`prisma migrate dev`)                                     |
| `npm run db:seed`        | Seeds the dev DB (idempotent)                                                                      |
| `npm run db:reset`       | Drops the dev DB, re-applies migrations and seeds (destructive; run it yourself)                   |
| `npm run lint`           | ESLint                                                                                             |
| `npm run typecheck`      | TypeScript checks for the e2e files and every workspace                                            |
| `npm run format`         | Prettier                                                                                           |

`npm test` and `npm run e2e` share the test database (`hms_test`), so run them one after the other,
never in parallel. The e2e run does not touch your dev servers or dev database. One-time browser
install for e2e: `npx playwright install chromium`.

## Architecture

Layers follow the design spec (section 5.2):

| Layer          | Implementation                                                                                                                    |
| -------------- | --------------------------------------------------------------------------------------------------------------------------------- |
| Presentation   | `apps/client`: React pages and components                                                                                         |
| Application    | `apps/server/src/modules/*/*.routes.ts` and `*.controller.ts`: HTTP, auth guard, permission check, Zod validation, response shape |
| Business logic | `*.service.ts`: rules, state transitions, cross-module effects, transactions                                                      |
| Database       | Prisma schema plus `*.repository.ts` wrapping Prisma; PostgreSQL 16                                                               |

Each server module lives in `apps/server/src/modules/<module>/` with routes, controller, service,
repository and tests. Controllers never touch Prisma; services never touch `req`/`res`.
Shared Zod schemas, roles and permissions live in `packages/shared` (`@hms/shared`).

Full design: `docs/superpowers/specs/2026-11-17-hms-design.md`.

## Configuration

See `.env.example` for every variable: `DATABASE_URL`, `TEST_DATABASE_URL`, `PORT`,
`CLIENT_ORIGIN`, `NODE_ENV`, plus optional `LOGIN_RATE_LIMIT`, `BCRYPT_COST`, `SEED_SCALE` and
`SEED_ALLOW_PRODUCTION`.
