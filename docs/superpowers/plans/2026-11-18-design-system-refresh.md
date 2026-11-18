# Design System Refresh + Public Hospital Website Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Re-skin the HMS client with a teal light/dark design system and component kit, a new
app shell with a command palette, a live dashboard, and a public hospital website at `/`.

**Architecture:** Tokens in `tokens.css` switch by `data-theme` on `<html>`. Tailwind maps to the
tokens, and a Radix-based kit in `components/ui` is the only place styling decisions live. Three
small server endpoints feed the dashboard and the website, following the existing
routes → controller → service → repository layering with zod schemas in `@hms/shared`. 21st.dev
components are fetched with the MCP `get_component` as references and rewritten onto our tokens,
never installed with `npx shadcn`.

**Tech Stack:** React 19, Vite 7, Tailwind 3.4, TanStack Query 5, react-router 7, Radix
primitives, lucide-react, motion, recharts, cmdk, clsx + tailwind-merge; Express + Prisma +
Postgres 16; Vitest + Testing Library + MSW + supertest; Playwright.

**Spec:** `docs/superpowers/specs/2026-11-18-design-system-refresh-design.md`. Read it before any
task. Its §5.2 token table and §6.6 content rules are referenced, not repeated, here.

## Global Constraints

- Local branch `design-system-refresh` only. Never push or open PRs.
- No raw hex/rgb outside `apps/client/src/styles/tokens.css`. Components use Tailwind token
  classes only.
- Existing token names keep working; new tokens per spec §5.2. `on-primary` maps to
  `var(--color-on-primary)`.
- WCAG 2.1 AA in both themes: 4.5:1 text, 3:1 UI components and large text. Visible 2 px focus
  ring, 2 px offset.
- Motion: animate only `opacity`/`transform`; durations from `--duration-*`; honour
  `prefers-reduced-motion`. No scroll reveals, glass, neumorphism, neon or purple/pink gradients.
- No fake data, no placeholder widgets, no nav or links to pages that don't exist.
- Interactive controls ≥ 44 px tall (`min-h-11`), icon-only buttons need `aria-label`.
- Pin exact dependency versions (no `^`) for every new package.
- Never write the 21st.dev API key anywhere in the repo. Each adapted file gets a header comment
  `// Adapted from 21st.dev: <component url>`. Budget is about 8 `get_component` calls in total.
- API base is `/api/v1`. Timezone Asia/Colombo via `apps/server/src/lib/dateRange.ts`.
- Gates before every commit: `npm run lint`, `npm run typecheck`, `npm test`. `npm test` and
  `npm run e2e` share `hms_test`, so never run them in parallel.

## Review Focus

1. **Storage blocked** (private window, `localStorage` throws): theme and sidebar still work and
   fall back to System and expanded. Tests are in Tasks 2 and 6.
2. **Fresh database with no website settings saved**: the public endpoint returns defaults (empty
   strings, empty services) and the landing page shows name + Sign in without empty sections or
   "undefined". Tests are in Tasks 9 and 10.
3. **About text containing HTML** (`<script>`, `<b>`): rendered as literal text with line breaks.
   Test is in Task 10.
4. **Audit summary on quiet days and at Colombo midnight**: always exactly `days` entries,
   zero-filled, oldest first. An event at 23:59 Colombo counts on that day, not the next UTC day.
   Test is in Task 8.
5. **Existing redirects after `/` moves**: forced password change still lands on
   `/change-password`; finishing it, or signing in, lands on `/dashboard`; session timeout still
   lands on `/login`. Tests are in Tasks 10 and 13.

---

### Task 1: Dependencies, `cn`, light/dark tokens, contrast test

**Files:**
- Modify: `apps/client/package.json` (add pinned deps)
- Create: `apps/client/src/components/ui/cn.ts`; Modify: `apps/client/src/components/ui/cx.ts`
  becomes `export { cn as cx } from './cn';`
- Modify: `apps/client/src/styles/tokens.css`, `apps/client/tailwind.config.ts`,
  `apps/client/src/styles/global.css`
- Create: `apps/client/src/styles/contrast.ts`
- Test: `apps/client/src/styles/tokens.contrast.test.ts`

**Interfaces:**
- Produces: `cn(...inputs: ClassValue[]): string` (clsx + twMerge configured with our custom
  colour, radius and shadow class names so merges resolve correctly).
- Produces: `parseThemeTokens(css: string): { light: Record<string,string>; dark: Record<string,string> }`
  reads `:root { … }` and `[data-theme="dark"] { … }`. Dark inherits any light token it doesn't
  redefine.
- Produces: `contrastRatio(fg: string, bg: string): number` (WCAG relative luminance, hex input).
- Produces Tailwind names: `on-primary`, `border-strong`, `surface-raised`, `surface-sunken`,
  `success-subtle`, `warning-subtle`, `danger-subtle`, `info-subtle`, `chart-1`…`chart-6`,
  `rounded-pill`, `shadow-raised`, `text-4xl` (36px display), `duration-fast|base|slow`,
  `ease-out-soft`; `darkMode: ['selector', '[data-theme="dark"]']`.

- [ ] **Step 1: Install dependencies** (exact versions):
      `npm i -w @hms/client --save-exact @radix-ui/react-dialog @radix-ui/react-dropdown-menu @radix-ui/react-popover @radix-ui/react-tooltip @radix-ui/react-tabs @radix-ui/react-switch @radix-ui/react-checkbox @radix-ui/react-visually-hidden lucide-react motion recharts cmdk clsx tailwind-merge`
- [ ] **Step 2: Write the failing contrast test.** It reads `tokens.css` with `fs` and asserts,
      for **both** themes:
      ```ts
      const TEXT_PAIRS = [['text','bg'],['text','surface'],['text','surface-raised'],['text','surface-sunken'],
        ['muted','bg'],['muted','surface'],['muted','surface-sunken'],['on-primary','primary'],['on-primary','primary-hover'],
        ['primary','surface'],['primary','bg'],['primary-hover','primary-subtle'],
        ['success','success-subtle'],['warning','warning-subtle'],['danger','danger-subtle'],['info','info-subtle'],
        ['success','surface'],['warning','surface'],['danger','surface'],['info','surface']];
      // each: contrastRatio(t[`--color-${fg}`], t[`--color-${bg}`]) >= 4.5
      const UI_PAIRS = [['border-strong','surface'],['primary','surface'],['chart-1','surface'], …chart-2..6];
      // each >= 3
      ```
      Also: `parseThemeTokens` returns `dark['--color-surface'] !== light['--color-surface']`, and
      `contrastRatio('#000000','#ffffff')` ≈ 21.
- [ ] **Step 3: Run** `npm test -w @hms/client -- tokens.contrast` → FAIL (module missing).
- [ ] **Step 4: Implement** `contrast.ts`. Write `tokens.css` per spec §5.2 light (`:root`) and
      dark (`:root[data-theme="dark"]`) blocks, plus motion, radius-pill and shadow-raised tokens.
      Update the Tailwind config. If a pair fails, adjust the shade (stay in the teal/slate
      families) and update the spec §5.2 table in the same commit.
- [ ] **Step 5: Run** the contrast test plus the full client suite → PASS. Existing tests stay
      green, since the class names they rely on are unchanged.
- [ ] **Step 6: Commit** `feat(client): light/dark design tokens, cn helper, contrast test`.

### Task 2: Theme preference (System/Light/Dark) with no flash

**Files:**
- Create: `apps/client/public/theme-init.js`; Modify: `apps/client/index.html` (add
  `<script src="/theme-init.js"></script>` in `<head>`, **not** a module and not inline)
- Create: `apps/client/src/theme/ThemeProvider.tsx`, `apps/client/src/theme/useTheme.ts`,
  `apps/client/src/components/ui/ThemeToggle.tsx`
- Modify: `apps/client/src/main.tsx`, `apps/client/src/test/render.tsx` (wrap in ThemeProvider)
- Test: `apps/client/src/theme/ThemeProvider.test.tsx`

**Interfaces:**
- Produces: `type ThemePreference = 'system' | 'light' | 'dark'`; storage key `'hms-theme'`.
- Produces: `useTheme(): { preference: ThemePreference; resolved: 'light' | 'dark'; setPreference(p: ThemePreference): void }`.
- Produces: `<ThemeToggle />`: a DropdownMenu (Task 3) trigger, icon-only with
  `aria-label="Theme"`, items "System", "Light", "Dark" as radio items.
- `theme-init.js` sets `document.documentElement.dataset.theme` from storage, or from
  `matchMedia('(prefers-color-scheme: dark)')` when System. Wrap everything in try/catch.

- [ ] **Step 1: Failing tests:**
  - `defaults to system and follows the media query`: mock `matchMedia` dark → `<html data-theme="dark">`.
    Fire the change event to light → `data-theme="light"`.
  - `persists the choice`: `setPreference('dark')` → `localStorage['hms-theme'] === 'dark'`.
  - `survives storage that throws`: stub `localStorage.getItem`/`setItem` to throw → renders,
    `preference === 'system'`, `setPreference('light')` still sets `data-theme="light"`.
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement.** Listen to `matchMedia` change events only
  while preference is `system`. **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat(client): theme preference with system/light/dark and no flash`.

*(ThemeToggle depends on Task 3's DropdownMenu. Implement ThemeToggle at the end of Task 3 and
test it there: picking "Dark" sets `data-theme="dark"`.)*

### Task 3: Core kit restyle + new primitives

**Files (all in `apps/client/src/components/ui/`):**
- Modify: `Button.tsx`, `Input.tsx`, `Select.tsx`, `FormField.tsx`, `Label.tsx`, `Card.tsx`,
  `Dialog.tsx`, `Toast.tsx`, `Spinner.tsx`, `EmptyState.tsx`, `Badge.tsx`
- Create: `Textarea.tsx`, `Checkbox.tsx`, `Switch.tsx`, `StatusBadge.tsx`, `Skeleton.tsx`,
  `Avatar.tsx`, `Tooltip.tsx`, `DropdownMenu.tsx`, `Tabs.tsx`, `Sheet.tsx`, `PageHeader.tsx`,
  `ThemeToggle.tsx` (from Task 2)
- Delete: `icons.tsx` (call sites move to `lucide-react`; decorative icons get `aria-hidden`)
- Test: `apps/client/src/components/ui/kit.test.tsx` (existing `Dialog.test.tsx` must keep passing)

**Interfaces:**
- `Button`: `variant: 'primary'|'secondary'|'ghost'|'danger'|'link'`, `size: 'sm'|'md'`
  (both keep `min-h-11`; sm only tightens padding/text), `loading`, plus `IconButton`
  (`aria-label: string` **required** in its props type, `icon: LucideIcon`).
- `StatusBadge`: `{ tone: 'neutral'|'primary'|'success'|'warning'|'danger'|'info'; children; dot?: boolean }`.
  `Badge` re-exports it under its existing name and props.
- `Dialog`: keep its current props/API, rebuilt on `@radix-ui/react-dialog` with a `motion`
  enter (fade + 4 px rise, `--duration-base`) and faster exit.
- `Sheet`: `{ open, onOpenChange, side: 'left'|'right', title, children }` (Radix dialog).
- `PageHeader`: `{ title: string; description?: ReactNode; actions?: ReactNode; breadcrumbs?: { label: string; to?: string }[] }`.
  Renders one `h1`.
- `Avatar`: `{ name: string; size?: 'sm'|'md' }` → initials (first letters of the first and last
  words), `aria-hidden` when next to a visible name.
- `Tooltip`: `{ content: string; children: ReactElement; side? }`.
- `Tabs`, `DropdownMenu`, `Checkbox`, `Switch`: thin token-styled wrappers re-exporting the Radix
  parts (`Tabs.Root/List/Trigger/Content`, `DropdownMenu.Root/Trigger/Content/Item/RadioGroup/RadioItem/Separator/Label`).

- [ ] **Step 1: Failing tests:** `Avatar` initials (`'Dr. Kumari Fernando'` → `'DF'`, `'Nimal'` →
  `'N'`). Tabs arrow-key moves selection. DropdownMenu opens on Enter and closes on Escape,
  returning focus to the trigger. ThemeToggle "Dark" sets `data-theme="dark"`. A Button with
  `loading` is disabled and `aria-busy`. Sheet traps focus and closes on Escape.
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement.** Swap all `icons.tsx` imports to lucide.
  **Step 4: Run** the full client suite → PASS. Fix selectors in existing tests only where markup
  changed, without weakening assertions.
- [ ] **Step 5:** Run ui-ux-pro-max `--domain ux` checks for "focus states" and "touch target",
  and fix what fails.
- [ ] **Step 6: Commit** `feat(client): radix-based component kit on design tokens`.

### Task 3A: Poppins as the system font *(added 2026-11-18)*

The user asked for Poppins throughout. It replaces Inter (spec §5.2 is updated).

**Files:** Modify `apps/client/package.json` (add `@fontsource/poppins` pinned exactly; remove
`@fontsource-variable/inter`), `apps/client/src/main.tsx` (import
`@fontsource/poppins/latin-400.css`, `latin-500.css`, `latin-600.css` and `latin-700.css`, and
delete the Inter import), `apps/client/src/styles/tokens.css` (`--font-sans: 'Poppins', system-ui,
-apple-system, 'Segoe UI', sans-serif;`). Add `--font-numeric` only if Step 3 requires it.
Test: `apps/client/src/styles/font.test.ts`.

- [ ] **Step 1: Failing test.** Read `tokens.css` and `main.tsx` from disk:
  - `--font-sans` starts with `'Poppins'`;
  - `main.tsx` imports the four Poppins weight files;
  - nothing under `apps/client/src` matches `/\binter(-variable)?\b|Inter Variable|'Inter'/i` (so words like "Interface" don't count), except this test file.
- [ ] **Step 2–4:** FAIL → implement → PASS.
- [ ] **Step 4b (numerals):** check whether Poppins supports `font-variant-numeric: tabular-nums`
  (the `tnum` feature in the shipped woff2: inspect the font's GSUB features with a short Node
  script using the `fontkit` package in the scratch area, without adding it to the project).
  - If it has `tnum`: done.
  - If not: add `--font-numeric: ui-monospace, 'Segoe UI', system-ui, sans-serif`, plus a Tailwind
    `font-numeric` family. Record in the spec §5.2 that tables and stat figures (Tasks 4, 5 and 11)
    use `font-numeric tabular-nums`, so digits align.
  - Report which case applied.
- [ ] **Step 5:** Gates: lint, typecheck, `npm test` green.
- [ ] **Step 6: Commit** `feat(client): Poppins as the system font`.

### Task 4: DataTable upgrade (sort, density, sticky header)

**Files:** Modify `apps/client/src/components/DataTable.tsx`, `apps/client/src/components/ui/Table.tsx`,
`apps/client/src/components/Pagination.tsx`; Test `apps/client/src/components/DataTable.test.tsx`.
21st.dev: search "data table sortable", fetch one, adapt the visual treatment only.

**Interfaces (additive; existing props unchanged):**
- `Column<T>` gains `sortable?: boolean`, `sortValue?: (row: T) => string | number`,
  `align?: 'left' | 'right'` (right uses `tabular-nums`).
- `DataTableProps<T>` gains `density?: 'comfortable' | 'compact'` (default comfortable; compact
  uses 36 px rows, comfortable 48 px), `defaultSort?: { key: string; dir: 'asc' | 'desc' }`,
  `onSortChange?: (s: { key: string; dir: 'asc' | 'desc' }) => void`. When `onSortChange` is set,
  sorting is server-driven and rows aren't re-sorted locally.
- Header is sticky inside a scroll container (`overflow-auto`, `max-h` set by caller); table
  scrolls horizontally inside its own container on narrow screens.

- [ ] **Step 1: Failing tests:** clicking a sortable header sorts the rows ascending and sets
  `aria-sort="ascending"`. Clicking again → descending. Unsortable headers have no button and no
  `aria-sort`. With `onSortChange`, rows keep input order and the callback receives
  `{key, dir}`. `density="compact"` sets a `data-density="compact"` attribute on the table.
- [ ] **Step 2–4:** Run → FAIL, implement (sort button inside `<th>`, lucide arrow icon
  `aria-hidden`), run → PASS.
- [ ] **Step 5: Commit** `feat(client): sortable, dense, sticky-header DataTable`.

### Task 5: StatCard and chart wrappers

**Files:** Create in `apps/client/src/components/data/`: `StatCard.tsx`, `Sparkline.tsx`,
`ChartCard.tsx`, `AreaTrend.tsx`, `BarBreakdown.tsx`, `Donut.tsx`, `chartColors.ts`.
Modify `apps/client/src/test/setup.ts` (stub `ResizeObserver` for recharts).
Test: `apps/client/src/components/data/data.test.tsx`.
21st.dev: fetch one KPI/stat card with sparkline (e.g. id 45564 "Stat Card" or 40086 "KPI Tile").

**Interfaces:**
- `StatCard`: `{ label: string; value: ReactNode; delta?: { value: number; goodWhen: 'up' | 'down'; label?: string }; sparkline?: number[]; icon?: LucideIcon; loading?: boolean }`.
  Delta tone: `(value > 0) === (goodWhen === 'up')` → success, `value === 0` → neutral, else danger.
  The delta text includes a sign and an arrow icon, so colour is never the only signal.
- `chartColor(i: number): string` returns `var(--chart-${(i % 6) + 1})`.
- `ChartCard`: `{ title: string; description?: string; table: { columns: string[]; rows: (string | number)[][] }; children: ReactNode }`.
  Renders the chart `aria-hidden` plus a visually hidden `<table>` of the same data, and a
  "Show data" toggle that reveals the table visually.
- `AreaTrend`: `{ data: { label: string; value: number }[]; seriesName: string }`.
  `BarBreakdown`: `{ data: { label: string; value: number }[] }`.
  `Donut`: `{ data: { label: string; value: number }[]; centerLabel?: string }`.
  All use `chartColor`, axis/grid colours from `var(--color-muted)` / `var(--color-border)`, and
  tooltips styled as `surface-raised`.

- [ ] **Step 1: Failing tests:** StatCard delta tones for `{value: 5, goodWhen:'down'}` → danger
  and `{value:-3, goodWhen:'down'}` → success. `loading` renders a Skeleton and no value. ChartCard
  exposes a table with the given rows to `getByRole('table')`. "Show data" toggles its
  visibility. Donut with all-zero data renders an EmptyState "No data yet" instead of a chart.
- [ ] **Step 2–4:** FAIL → implement → PASS.
- [ ] **Step 5: Commit** `feat(client): stat cards and accessible chart wrappers`.

### Task 6: Grouped navigation, collapsible sidebar, top bar

**Files:** Modify `apps/client/src/layout/nav.ts`, `Sidebar.tsx`, `TopBar.tsx`, `AppShell.tsx`;
Create `apps/client/src/layout/Breadcrumbs.tsx`, `apps/client/src/layout/useSidebarCollapsed.ts`.
Tests: `apps/client/src/layout/Sidebar.test.tsx` (extend), `apps/client/src/layout/TopBar.test.tsx`.
21st.dev: fetch one collapsible sidebar shell (e.g. id 31454 "Sidebar" icon-rail variant).

**Interfaces:**
- `type NavGroup = 'Overview' | 'Clinical' | 'Operations' | 'Finance' | 'Administration'`
  (`NAV_GROUP_ORDER` in that order).
- `NavItem { label: string; path: string; icon: LucideIcon; permission?: Permission; group: NavGroup }`.
  Slice 0 items: Dashboard `/dashboard` (Overview); Users, Settings, Audit Log (Administration).
- `visibleNavGroups(can: (p: Permission) => boolean): { group: NavGroup; items: NavItem[] }[]`
  omits groups with no visible items.
- `useSidebarCollapsed(): [boolean, (v: boolean) => void]` uses localStorage key
  `'hms-sidebar-collapsed'`, wrapped in try/catch.
- Desktop (≥1024 px): sidebar 240 px, or a 64 px icon rail with a Tooltip on each item. Below
  1024 px: Sheet drawer (replaces the hand-rolled drawer).
- TopBar: menu button (<1024 px), Breadcrumbs derived from the matched nav item, search button
  ("Search… Ctrl K", opens the Task 7 palette), ThemeToggle, session timer (existing), user
  DropdownMenu (Avatar + name + role; items: Change password, Sign out). Existing TopBar
  behaviours and labels used by tests/e2e stay: sign-out flow, change-password navigation.

- [ ] **Step 1: Failing tests:** a NURSE sees no "Administration" heading and no empty group
  headings. An ADMIN sees "Overview" then "Administration" in order. Collapsing hides the labels
  but keeps each link's accessible name (via Tooltip/`aria-label`). With localStorage throwing,
  the sidebar renders expanded. The user menu has "Sign out" and "Change password".
- [ ] **Step 2–4:** FAIL → implement → PASS (existing Sidebar tests updated for `/dashboard`).
- [ ] **Step 5: Commit** `feat(client): grouped collapsible sidebar and new top bar`.

### Task 7: Command palette (Ctrl/⌘+K)

**Files:** Create `apps/client/src/commands/registry.ts`, `apps/client/src/commands/CommandPalette.tsx`;
Modify `TopBar.tsx` (opens it) and `AppShell.tsx` (global key handler);
Modify `apps/client/src/pages/admin/UsersPage.tsx` (open the create dialog when `?new=1`, then
strip the param). Test `apps/client/src/commands/CommandPalette.test.tsx`.
21st.dev: fetch one cmdk-style palette (e.g. id 46285, combobox/listbox semantics).

**Interfaces:**
- `CommandItem { id: string; label: string; group: 'Pages' | 'Actions'; to: string; permission?: Permission; keywords?: string[]; icon: LucideIcon }`.
- `COMMANDS`: every `NAV_ITEMS` entry as a Page, plus the Action `{ id: 'user.create', label: 'Create user', to: '/admin/users?new=1', permission: USER_MANAGE }`.
  Later slices append here.
- `visibleCommands(can): CommandItem[]`.
- `<CommandPalette open onOpenChange />`: Radix Dialog + cmdk; Enter navigates and closes.

- [ ] **Step 1: Failing tests:** Ctrl+K opens the palette (`role="dialog"` named "Command
  palette"), and Meta+K does too. A NURSE typing "users" sees "No results". An ADMIN typing
  "create" sees "Create user", and Enter navigates to `/admin/users?new=1`. UsersPage with `?new=1`
  shows the create-user dialog. Escape closes the palette and returns focus.
- [ ] **Step 2–4:** FAIL → implement → PASS.
- [ ] **Step 5: Commit** `feat(client): RBAC-filtered command palette`.

### Task 8: Summary endpoints for the dashboard (server)

**Files:**
- Create: `packages/shared/src/schemas/summary.ts` (export from `packages/shared/src/index.ts`)
- Modify: `apps/server/src/modules/users/users.{routes,controller,service,repository}.ts`
- Modify: `apps/server/src/modules/audit/audit.{routes,controller,service,repository}.ts`
- Test: `apps/server/src/modules/users/users.test.ts`, `apps/server/src/modules/audit/audit.test.ts` (extend)

**Interfaces:**
- `usersSummarySchema = z.object({ total: z.number().int(), active: z.number().int(), byRole: z.record(z.enum(ROLES), z.number().int()) })`.
  Type `UsersSummary`. `byRole` has every role key, with 0 when empty. Uses `ROLES` from
  `packages/shared/src/roles.ts`.
- `auditSummaryQuerySchema = z.object({ days: z.coerce.number().int().min(1).max(90).default(14) })`.
- `auditSummarySchema = z.object({ days: z.array(z.object({ date: z.string(), count: z.number().int() })), today: z.object({ logins: z.number().int(), failedLogins: z.number().int() }) })`.
  Type `AuditSummary`.
- `GET /api/v1/users/summary` (`user:manage`, register **before** any `/:id` GET).
  `GET /api/v1/audit-logs/summary?days=` (`audit:view`).
- Service computes "today" from `clock.now()` (`apps/server/src/lib/clock.ts`; tests use
  `setNow` / `resetClock`) in Colombo. The day
  window runs from `startOfDayColombo(today - (days-1))` to `startOfNextDayColombo(today)`.
  Bucket in SQL with `date_trunc('day', "createdAt" AT TIME ZONE 'UTC' AT TIME ZONE 'Asia/Colombo')`
  via `prisma.$queryRaw` in the repository, then zero-fill in the service.

- [ ] **Step 1: Failing tests:**
  - users summary: 401 anonymous, 403 for NURSE; for ADMIN with 3 active + 1 inactive users,
    `total: 4, active: 3`; `byRole.DOCTOR` counts correctly, `byRole.PHARMACIST === 0`.
  - audit summary: 403 for NURSE; `days=0` and `days=91` → 400; default returns 14 entries
    oldest-first ending today.
  - Colombo boundary: freeze clock at `2026-11-18T10:00:00+05:30`. An audit row at
    `2026-11-17T23:59:00+05:30` counts on `2026-11-17`, and one at `2026-11-18T00:01:00+05:30`
    on `2026-11-18`. A day with no rows has `count: 0`.
  - today logins: 2 `LOGIN` + 1 `LOGIN_FAILED` today and 1 `LOGIN` yesterday →
    `{logins: 2, failedLogins: 1}` (the test's own login adds to `LOGIN`; assert relative to a
    baseline taken first, or insert rows directly).
- [ ] **Step 2–4:** FAIL → implement → PASS (`npm test -w @hms/server`).
- [ ] **Step 5: Commit** `feat(users,audit): summary endpoints for the dashboard`.

### Task 9: Website settings + public hospital endpoint (server)

**Files:**
- Modify: `packages/shared/src/schemas/settings.ts` (new fields; add `publicHospitalSchema`)
- Modify: `apps/server/src/modules/settings/settings.service.ts` (`SettingsValue`, `SETTING_DEFAULTS`)
- Create: `apps/server/src/modules/public/public.{routes,controller}.ts`; Modify
  `apps/server/src/app.ts` (`api.use('/public', publicRouter(publicRateLimit))`, plus
  `AppOptions.publicRateLimit?: number | false`, default 120 per minute)
- Modify: `apps/server/prisma/seed/modules/core.ts` (`DEMO_SETTINGS`)
- Test: `apps/server/src/modules/public/public.test.ts`, extend `settings.test.ts`, `prisma/seed/seed.test.ts`

**Interfaces:**
- New settings (defaults in brackets):
  - `hospitalEmail: z.union([z.literal(''), z.string().trim().email().max(120)])` ['']
  - `hospitalEmergencyPhone: z.string().trim().max(30)` ['']
  - `hospitalTagline: z.string().trim().max(140)` ['']
  - `hospitalAbout: z.string().trim().max(1200)` ['']
  - `hospitalOpeningHours: z.string().trim().max(300)` ['']
  - `hospitalServices: z.array(z.object({ name: z.string().trim().min(1).max(60), description: z.string().trim().max(160) })).max(12)` [[]]
- `PUBLIC_HOSPITAL_KEYS = ['hospitalName','hospitalAddress','hospitalPhone','hospitalEmail','hospitalEmergencyPhone','hospitalTagline','hospitalAbout','hospitalOpeningHours','hospitalServices'] as const`.
  `publicHospitalSchema` picks exactly these. Type `PublicHospital`.
- `settingsService.getPublicHospital(): Promise<PublicHospital>`.
- `GET /api/v1/public/hospital`: no auth, `Cache-Control: public, max-age=300`, 429
  `RATE_LIMITED` past the limit.
- Seed values: realistic, matching the existing demo hospital ("Lanka General Hospital (Demo)",
  Colombo 08). Emergency number, an email on a `.lk`-style demo domain such as
  `info@lgh-demo.lk`, tagline, a 2–3 sentence about, weekday/weekend hours, 8 services
  (e.g. Emergency & Trauma, Outpatient Clinics, Cardiology, Paediatrics, Obstetrics &
  Gynaecology, Laboratory, Pharmacy, Radiology), each with a one-line description.

- [ ] **Step 1: Failing tests:**
  - public: anonymous `GET /api/v1/public/hospital` → 200 with exactly the 9 keys (sorted
    equality). `currencyCode`, `taxRatePercent`, `sessionTimeoutMinutes` are absent. It has the
    `Cache-Control` header. On an empty settings table the response is defaults (`hospitalName:
    'Hospital'`, `hospitalServices: []`). With `createApp({ publicRateLimit: 2 })` the third call
    → 429.
  - settings: PUT with the new fields persists them. `hospitalEmail: 'not-an-email'` → 400. 13
    services → 400. `hospitalEmail: ''` → 200. Update the existing `valid` fixture and the
    `/settings/public` key list (unchanged keys there).
  - seed: after seeding, `hospitalServices` has 8 entries and `hospitalEmergencyPhone` is non-empty.
- [ ] **Step 2–4:** FAIL → implement → PASS.
- [ ] **Step 5: Commit** `feat(settings,public): website settings and public hospital endpoint`.

### Task 9A: 1,000 staff accounts with 90 days of audit history (seed) *(added 2026-11-18)*

The user asked for 1,000 dummy records. The only data the system holds today is staff accounts,
settings and the audit log, so this fills those. The dashboard (Task 11), Users and Audit pages
then show a busy hospital.

**Files:**
- Create: `apps/server/prisma/seed/data/names.ts` (Sinhala, Tamil and Muslim given names and
  surnames, at least 60 each family. Spec §11 says these lists live in `data/`.)
- Create: `apps/server/prisma/seed/modules/staff.ts`; Modify: `apps/server/prisma/seed/index.ts`
  (register after `core`)
- Test: `apps/server/prisma/seed/staff.test.ts`

**Interfaces:**
- Consumes `SeedModule`, `ctx.prisma`, `ctx.faker` (seeded 20261117), `ctx.scale`, `DEMO_USERS`,
  `DEMO_PASSWORD` and `hashPassword` from `core.ts` / the auth module.
- Produces `staffModule: SeedModule` (name `'staff'`) and
  `STAFF_TOTALS: Record<SeedScale, Record<RoleName, number>>`. Both include the 7 demo users:
  - `large`: ADMIN 15, DOCTOR 200, NURSE 450, RECEPTIONIST 100, LAB_STAFF 100, PHARMACIST 80,
    ACCOUNTANT 55 (= 1,000)
  - `small`: ADMIN 2, DOCTOR 4, NURSE 6, RECEPTIONIST 2, LAB_STAFF 2, PHARMACIST 2,
    ACCOUNTANT 2 (= 20)

**Rules:**
- Names: mix the three name families roughly 70 / 20 / 10. Doctors are "Dr. " + name.
- Usernames are deterministic, `firstname.lastname` lower-case ASCII. On a clash, add a numeric
  suffix (`.2`, `.3`). They must never clash with the 7 demo usernames.
- Password `Password1!`, hashed once and reused.
- About 5% are `isActive: false` and about 2% are `mustChangePassword: true`. Neither applies to
  demo users.
- Upsert by username, so a re-run creates no duplicates. A re-run doesn't touch existing
  non-demo users' passwords.
- Audit history covers the 90 Colombo days before today, generated from `ctx.faker`:
  - Active users get a `LOGIN` on most weekdays (doctors and nurses on weekends too), each
    followed by a `LOGOUT` 4–10 h later when the time is still in the past.
  - About 3% of login attempts are `LOGIN_FAILED` with `userId` null where the user is unknown
    (match how `auth.service.ts` records it).
  - Admins add a few `CREATE` and `UPDATE` entries on entity `User`.
  - Times fall in plausible Colombo shift hours. `ip` values come from private ranges.
  - Rows go in through `createMany` batches of 5,000 with deterministic ids
    `seed-audit-<7-digit n>` and `skipDuplicates: true`, so a re-run adds nothing.
  - Large scale yields about 50k–80k rows; small yields about 300.
- Large-scale seeding of this module finishes in under 60 s locally. Log the counts.
- The seed's production guard is unchanged.

- [ ] **Step 1: Failing tests** (run the module with `scale: 'small'` against the test DB after
  `resetDb`):
  - The user count per role equals `STAFF_TOTALS.small`, and the 7 demo usernames are present.
  - All usernames are unique and match `/^[a-z]+(\.[a-z]+)+(\.\d+)?$/`, or are a demo username.
  - At least one inactive non-demo user exists at small scale (force at least one).
  - The audit rows' `createdAt` all fall within the last 90 Colombo days and none are in the
    future. `LOGIN` > 0 and `LOGIN_FAILED` > 0.
  - Running the module twice leaves user and audit counts unchanged.
  - Two runs on fresh DBs produce identical usernames (determinism).
  - `STAFF_TOTALS.large` sums to 1000, as a pure assertion that doesn't seed large in tests.
- [ ] **Step 2–4:** FAIL → implement → PASS (`npm test -w @hms/server -- staff seed`).
- [ ] **Step 5:** Run `npm run db:seed` once against the **dev** DB, which is idempotent and not a
  reset. Record the time taken and the counts in the report. Do not run `db:reset`.
- [ ] **Step 6:** README: the demo section gains one line: "The seed also creates 1,000 staff
  accounts (all roles) with 90 days of activity; they share the demo password."
- [ ] **Step 7: Commit** `feat(seed): 1,000 staff accounts with 90 days of audit history`.

### Task 10: Public hospital website at `/` and route move to `/dashboard`

**Files:**
- Create: `apps/client/src/pages/public/LandingPage.tsx`, `LandingHeader.tsx`, `LandingHero.tsx`,
  `LandingSections.tsx` (About, Services, Visit us), `LandingFooter.tsx`, `HeroArt.tsx` (inline SVG)
- Create: `apps/client/src/lib/usePublicHospital.ts`
- Modify: `apps/client/src/App.tsx` (`/` → LandingPage outside RequireAuth; `/dashboard` → the
  dashboard inside the shell), `apps/client/src/pages/LoginPage.tsx` (default `from` →
  `'/dashboard'`), `apps/client/src/pages/ChangePasswordPage.tsx` (both `navigate('/')` →
  `'/dashboard'`), `apps/client/src/pages/admin/SettingsPage.tsx` (form fields for the 6 new
  settings; services as a repeatable name + description list with Add/Remove, max 12)
- Test: `apps/client/src/pages/public/LandingPage.test.tsx`; update `LoginPage.test.tsx`,
  `ChangePasswordPage.test.tsx`, `SettingsAuditPages.test.tsx`
- 21st.dev: fetch at most one hero-section reference and one footer reference (adapt layout only;
  no stock images).

**Interfaces:**
- Consumes `PublicHospital` (Task 9).
- `usePublicHospital(): UseQueryResult<PublicHospital, ApiError>` fetches `/public/hospital`
  with `staleTime: 5 min`.
- LandingPage: if `useAuth()` has a user (after loading), `<Navigate to="/dashboard" replace />`.
  While auth is loading, render the landing page as normal (no spinner flash).
- Sets `document.title` to `${hospitalName} | ${hospitalTagline}`, or just the name when there's
  no tagline. Updates `<meta name="description">` to the tagline.

- [ ] **Step 1: Failing tests** (MSW-mocked `/public/hospital` and `/auth/me` 401):
  - renders the hospital name as the only `h1`, the emergency number as a link with
    `href="tel:+94…"` (spaces stripped), services as a list, and a "Sign in" link to `/login`.
  - the empty-settings fixture (all `''`, `[]`) shows the name and Sign in, with no "About",
    "Services", "Visit us" or emergency strip, and no text "undefined" anywhere.
  - endpoint 500 → the page still renders "Sign in" and a calm fallback heading (no crash).
  - `hospitalAbout: '<script>alert(1)</script>\nLine two'` → the literal text is visible, no
    `script` element is created, and the two lines render separately.
  - signed-in `/auth/me` 200 → redirected to `/dashboard`.
  - LoginPage: successful sign-in with no `from` lands on `/dashboard`. ChangePasswordPage
    success → `/dashboard`.
  - SettingsPage: editing a service and saving PUTs `hospitalServices` with the new value. "Add
    service" is disabled at 12.
- [ ] **Step 2–4:** FAIL → implement → PASS. Header anchors scroll to `#about`, `#services`,
  `#visit` and `#contact`, and the header collapses to a menu Sheet below 768 px.
- [ ] **Step 5:** ui-ux-pro-max: run `--domain landing "hero contact"` and `--domain ux "skip link landmarks"`.
  Apply what fits the spec. Run Lighthouse (Chrome DevTools, against `npm run dev`) on `/` in
  both themes: accessibility ≥ 95 and CLS < 0.1. Record the scores in the task report.
- [ ] **Step 6: Commit** `feat(client): public hospital website at / and dashboard at /dashboard`.

### Task 11: Dashboard page with live data

**Files:** Rename `apps/client/src/pages/HomePage.tsx` → `apps/client/src/pages/DashboardPage.tsx`
(update imports); Create `apps/client/src/lib/useDashboardData.ts`;
Test `apps/client/src/pages/DashboardPage.test.tsx`.

**Interfaces:**
- Consumes `UsersSummary`, `AuditSummary` (Task 8), `StatCard`, `ChartCard`, `AreaTrend`,
  `BarBreakdown` (Task 5), `visibleCommands` (Task 7).
- `useDashboardData()` enables the users query only when `can(USER_MANAGE)`, and the audit
  summary + latest-8 audit list (`/audit-logs?pageSize=8`) only when `can(AUDIT_VIEW)`.

**Layout:** PageHeader "Welcome, {fullName}" (keep this exact heading text; e2e relies on it)
with role StatusBadge and today's date in Colombo (`Intl.DateTimeFormat('en-LK', { timeZone: 'Asia/Colombo', dateStyle: 'full' })`).
- With USER_MANAGE: StatCards Active users (`active` of `total`) and Roles in use, plus a
  BarBreakdown of users by role.
- With AUDIT_VIEW: StatCards Logins today and Failed logins today. Failed uses
  `goodWhen: 'down'` with no delta unless yesterday is available: show no delta rather than
  invent one. AreaTrend "Activity, last 14 days". "Recent activity" list of 8 entries (actor,
  action StatusBadge, entity, relative time) with a "View audit log" link.
- Neither permission: a welcome card plus quick links built from `visibleCommands(can)`
  (Pages only).

- [ ] **Step 1: Failing tests:** a NURSE makes no request to `/users/summary` or
  `/audit-logs/summary` (MSW `onUnhandledRequest: 'error'` or request spy) and sees quick links.
  An ADMIN sees "Active users" showing "3 of 4" from the fixture and a 14-row data table in the
  activity ChartCard. Loading shows skeletons. A summary 500 shows an inline error with "Try
  again" in that card only, while the rest renders.
- [ ] **Step 2–4:** FAIL → implement → PASS.
- [ ] **Step 5: Commit** `feat(client): live role-aware dashboard`.

### Task 12: Re-skin remaining screens

**Files:** Modify `apps/client/src/pages/LoginPage.tsx`, `ChangePasswordPage.tsx`,
`ForbiddenPage.tsx`, `NotFoundPage.tsx`, `pages/admin/UsersPage.tsx`, `UserFormDialog.tsx`,
`SettingsPage.tsx`, `AuditLogPage.tsx`, `apps/client/src/auth/SessionTimeoutWarning.tsx`;
Tests: existing page tests (update selectors only) + new assertions below.

- Login: two panels at ≥1024 px. Left is a brand panel with `HeroArt` (Task 10), the hospital
  name (from `usePublicHospital`, falling back to "Hospital Management System") and the tagline.
  Right is the form. A "← Back to website" link to `/`. All labels, error messages and button
  names stay exactly as they are (tests and e2e depend on them).
- Users: PageHeader with a "Create user" action, DataTable with sortable Name/Username/Role,
  StatusBadge for active/inactive, Avatar beside the name, row actions in a DropdownMenu.
- Audit: filter bar with Action (Select of AuditAction values), Actor (user select from
  `/users` when USER_MANAGE, otherwise hidden), From/To dates. These map to the existing query
  params `action`, `userId`, `from`, `to`. Filters sync to the URL search params.
- Settings: Tabs "Hospital", "Website", "Security & billing". Fields grouped accordingly, one
  form and one Save.
- Forbidden / Not found: EmptyState with an illustration-free icon, plus a "Go to dashboard"
  button (or "Go to website" when signed out).

- [ ] **Step 1: Failing tests:** Audit: choosing Action "LOGIN" requests
  `/audit-logs?…action=LOGIN…` and puts `action=LOGIN` in the URL. Loading
  `/admin/audit?action=LOGIN` pre-selects it. Login: the "Back to website" link points to `/`.
  NotFound signed in: "Go to dashboard" → `/dashboard`.
- [ ] **Step 2–4:** FAIL → implement → PASS. Full client suite green.
- [ ] **Step 5:** ui-ux-pro-max pre-delivery checklist on each screen at 375/768/1024/1440 px in
  both themes (manual, via `npm run dev`). Fix any findings.
- [ ] **Step 6: Commit** `feat(client): re-skin login, admin, password and error screens`.

### Task 13: E2E, screenshots, spec update, final verification

**Files:** Modify `e2e/auth.spec.ts`; Create `e2e/design.spec.ts`;
Modify `docs/superpowers/specs/2026-11-17-hms-design.md` §8.1, §8.2, §7.3, §7.12 (per new spec §7);
Modify `README.md` (routes: `/` website, `/dashboard`; theme; Ctrl K).

- [ ] **Step 1:** Update `auth.spec.ts`: the `toHaveURL(/\/$/)` after password change becomes
  `/\/dashboard$/`. The `login()` helper may keep going to `/login` directly.
- [ ] **Step 2:** Write `design.spec.ts`:
  - `/` shows the hospital name `h1` and the "Sign in" link, which leads to `/login`. Signing in
    as `admin` lands on `/dashboard` with "Welcome, Nimal Perera".
  - Theme: choose Dark from the Theme menu → `html[data-theme="dark"]`. Reload → still dark.
  - Ctrl+K → type "audit" → Enter → `/admin/audit`.
  - Screenshots (artifacts, no pixel assertions) of `/`, `/login` and `/dashboard` in light and
    dark, at 1440×900 and 390×844, saved to `test-results/screens/`.
- [ ] **Step 3: Run** `npm run e2e` → PASS (needs `npx playwright install chromium` once).
- [ ] **Step 4:** Apply the spec edits listed in the design spec §7. Update the README.
- [ ] **Step 5: Verify** in sequence: `npm run lint`, `npm run typecheck`, `npm test`, then
  `npm run e2e`. All green. Paste the summaries into the task report.
- [ ] **Step 6: Commit** `test(e2e): website, theme and command palette; docs: spec §8 and README`.
- [ ] **Step 7:** Whole-branch review, then merge to local `main` per
  superpowers:finishing-a-development-branch (local only, never push). Then run `/graphify . --update`.
