# HMS Design System Refresh ("Evolve teal") — Design

Date: 2026-11-18
Amends: `2026-11-17-hms-design.md` §8 (UI). Comes before Slice 1.
Status: draft for approval.

## 1. Intent and why

The user wants the HMS to have a "stunning" frontend, built with the ui-ux-pro-max skill and
21st.dev components, while the system grows from the Slice 0 foundation into all 13 module slices.

They chose **Evolve teal**: keep teal as the brand and the calm, clinical feel. Clinical staff use
this all day, so it must stay readable. Add what makes it feel premium: dark mode, rich dashboards
and charts, subtle motion, and polished components (command palette, data tables, stat cards).

They chose **design system first** (approach A below). The kit is built and the existing Slice 0
screens are re-skinned before any new module, so slices 1–13 inherit one consistent look instead
of being restyled later.

## 2. Goals, non-goals, anti-goals

**Goals**
- A token-driven design system with **light and dark themes**: teal brand, slate neutrals,
  status colours, chart palette, elevation, motion.
- A shared component kit in `apps/client/src/components/ui` that later slices compose, with no
  one-off styling in pages.
- A new app shell: collapsible sidebar (icon rail on desktop, drawer below 1024 px) with grouped
  navigation, a top bar with breadcrumbs, a Ctrl/⌘+K command palette, a theme switch and the user
  menu.
- A **public hospital website** at `/` in front of sign-in (§6.6), for patients and visitors.
- Re-skin every existing screen: Login, Home, Users (+ user dialog), Settings, Audit log, Change
  password, Forbidden, Not found, session-timeout warning.
- Home becomes a real role-aware overview with live data from small new summary endpoints (§6.5).
- WCAG 2.1 AA in **both** themes, verified by an automated contrast test.

**Non-goals**
- New hospital modules (slices 1–13 come after this, each with its own plan).
- Tailwind 3 → 4 migration, a router change, or a state-library change.
- A software-marketing page about the HMS itself. *(The public hospital website in §6.6 is in
  scope; added 2026-11-18 at the user's request.)*

**Anti-goals** (failure even if it "works")
- Fake numbers or decorative charts. Every figure on screen comes from the API.
- Stub screens or links to pages that don't exist yet: no "coming soon" nav items, and no bell
  until Slice 13.
- Glassmorphism, neumorphism, neon, purple/pink AI gradients, scroll-reveal animations or motion
  that slows down repeated work.
- A theme that passes contrast in light but fails in dark.
- Breaking the existing auth, session timeout, RBAC or the test suites.

## 3. Constraints

- Stack stays: React 19, Vite 7, Tailwind 3.4, TanStack Query, react-hook-form + zod, Vitest +
  Testing Library + MSW, Playwright smoke.
- All colours stay in `apps/client/src/styles/tokens.css`. Tailwind keeps mapping to CSS variables,
  and components never contain raw hex.
- Local only: work on a local branch, never pushed (memory: no GitHub push). No hosting.
- 21st.dev `get_component` is paid and quota-limited per day. Fetch only what the kit needs, and
  never write the 21st.dev API key into the repo, commands in docs, or code comments.
- Windows dev machine; the scripts already in the repo must keep working.

## 4. Approaches

| Approach | Trade-off |
|---|---|
| **A. Design system first, then modules** — **chosen by the user** | One upfront phase before new features. Every later screen inherits the look, and something impressive is demo-able right after this phase. |
| B. Modules first, re-skin at the end | Features sooner, but every screen gets built twice and the UI stays plain for weeks. |
| C. Redesign inside each slice | No upfront phase, but the look drifts slice to slice and the kit never settles. |

Visual direction options were: Polish only (keep tokens as is), **Evolve teal (chosen)**, and Fresh
identity (new palette, dark-first, bold motion). Fresh identity was rejected as risky for
all-day clinical readability.

How 21st.dev is used (builder's call, recorded): **as source references, not via
`npx shadcn add`**. The registry install assumes a shadcn project (components.json, `@/` alias,
shadcn token names, usually Tailwind 4), and this client has none of those. Each chosen component
is fetched with the 21st.dev MCP `get_component`, then rewritten into our kit using our tokens and
Radix primitives. Its source URL goes in a header comment.

## 5. Design system

ui-ux-pro-max recommendation used: style **Data-Dense Dashboard** (low accessibility risk, light
and dark supported). Dials: variance 4, motion 3 (subtle), density 8 (dense). Its generated palette
(cyan `#0891B2`), Neumorphism style and Atkinson font were **not** adopted. Teal is kept (and the font is Poppins, the user's choice)
because they're the brand already in place, and Neumorphism carries a high accessibility risk.

### 5.1 Theming mechanics
- Themes are set by `data-theme="light" | "dark"` on `<html>`. The user picks **System / Light /
  Dark**, and System follows `prefers-color-scheme` live.
- The choice is stored in `localStorage` (`hms-theme`), wrapped in try/catch. It's a per-device
  preference, not stored on the server.
- To avoid a flash of the wrong theme, a tiny **external** script `public/theme-init.js` is loaded
  synchronously in `index.html` before the app bundle. It's external, not inline, so a strict CSP
  needs no `unsafe-inline`.
- Tailwind `darkMode: ['selector', '[data-theme="dark"]']` is available, but components should
  rarely need `dark:`. Tokens switch value, so the same class works in both themes.

### 5.2 Tokens (`tokens.css`)
Existing token names are kept so current code keeps working. New tokens are added. Values are
starting points: the contrast test (§8) is the authority, and the builder may nudge a shade to
pass it, recording the change here.

| Token | Light | Dark |
|---|---|---|
| `--color-primary` | `#0F766E` | `#2DD4BF` |
| `--color-primary-hover` | `#115E59` | `#5EEAD4` |
| `--color-primary-subtle` | `#CCFBF1` | `#134E4A` |
| `--color-on-primary` *(new)* | `#FFFFFF` | `#042F2E` |
| `--color-text` | `#0F172A` | `#E2E8F0` |
| `--color-muted` | `#475569` | `#94A3B8` |
| `--color-border` | `#E2E8F0` | `#1E293B` |
| `--color-border-strong` *(new)* | `#64748B` | `#64748B` |
| `--color-bg` | `#F8FAFC` | `#020617` |
| `--color-surface` | `#FFFFFF` | `#0F172A` |
| `--color-surface-raised` *(new; menus, dialogs)* | `#FFFFFF` | `#1E293B` |
| `--color-surface-sunken` *(new; table header, inputs)* | `#F1F5F9` | `#0B1222` |
| `--color-success` / `-subtle` | `#15803D` / `#DCFCE7` | `#4ADE80` / `#052E16` |
| `--color-warning` / `-subtle` | `#B45309` / `#FEF3C7` | `#FBBF24` / `#422006` |
| `--color-danger` / `-subtle` | `#B91C1C` / `#FEE2E2` | `#F87171` / `#450A0A` |
| `--color-info` / `-subtle` | `#1D4ED8` / `#DBEAFE` | `#60A5FA` / `#172554` |
| `--color-overlay` | `rgb(15 23 42 / .5)` | `rgb(2 6 23 / .7)` |
| `--chart-1…6` *(new)* | teal-700, sky-700, amber-600, violet-600, rose-600, slate-500 | teal-300, sky-300, amber-300, violet-300, rose-300, slate-400 |

The current `on-primary → var(--color-surface)` mapping in the Tailwind config changes to
`var(--color-on-primary)`, since surface turns dark in dark mode.

- **Type:** **Poppins** for the whole system *(changed 2026-11-18 at the user's request; replaces Inter)*, self-hosted via `@fontsource/poppins` (weights 400, 500, 600, 700; latin subset), fallback `system-ui, -apple-system, 'Segoe UI', sans-serif`. Sizes 12/14/16/18/20/24/30 stay, body 14 / 1.5. Add a 36 px
  display size for dashboard figures. Numbers in tables and stat cards use `tabular-nums`.
- **Spacing:** the 4 px scale stays. Dense layout: 12 px card padding inside dashboards, 16–24 px
  for page gutters.
- **Radius:** controls 6, cards 10 (unchanged). Add `--radius-pill: 9999px` for badges.
- **Elevation:** `--shadow-card`, `--shadow-overlay`, and a new `--shadow-raised` for menus. In
  dark mode, shadows are minimal and elevation comes from the lighter `surface-raised`.
- **Motion** *(new)*: `--duration-fast 120ms`, `--duration-base 180ms`, `--duration-slow 260ms`,
  `--ease-out cubic-bezier(.2,.8,.2,1)`. Only `opacity` and `transform` animate, and exits are
  faster than enters. The existing `prefers-reduced-motion` rule stays and also covers the
  motion library.
- **Focus:** 2 px primary ring with 2 px offset in both themes (unchanged rule).

## 6. Components, shell and screens

### 6.1 New dependencies (builder's calls)
`@radix-ui/react-*` (dialog, dropdown-menu, popover, tooltip, tabs, switch, checkbox,
visually-hidden) for accessible behaviour, `lucide-react` icons (replacing hand-written
`icons.tsx`), `motion` for enter/exit transitions, `recharts` for charts, `cmdk` for the command
palette, and `clsx` + `tailwind-merge` (the `cx` helper becomes a merging `cn`). Pin exact versions.

### 6.2 Kit (`components/ui`)
Restyle or replace: Button (primary / secondary / ghost / danger / link; sizes sm/md; icon-only
needs `aria-label`), Input, Select, Textarea, Checkbox, Switch, Label, FormField, Badge →
**StatusBadge** (tone + dot), Card, Dialog, Toast, Spinner, EmptyState, Table/DataTable,
Pagination.
New: **Skeleton**, **Tabs**, **DropdownMenu**, **Tooltip**, **Sheet** (side drawer), **Avatar**
(initials), **PageHeader** (title, description, actions, breadcrumbs), **StatCard** (label, value,
delta with direction semantics, optional sparkline), **ChartCard** + chart wrappers
(**AreaTrend**, **BarBreakdown**, **Donut**) that read `--chart-*` tokens and show a
table/summary fallback for screen readers, **CommandPalette**, **ThemeToggle**.

DataTable gains: sticky header, sortable columns (`aria-sort`), row hover, comfortable/compact
density toggle, skeleton loading rows, empty state, and horizontal scroll inside its own container
on narrow screens. Its current public API stays compatible, or call sites get updated in the same
change.

21st.dev references to fetch (one each, adapted): a collapsible sidebar shell, a stat/KPI card
with sparkline, a command palette, and a data table. The builder may swap in a better match from a
search. Keep it to about 6 retrievals.

### 6.3 App shell
- Sidebar: brand mark + hospital name, nav **grouped into sections**. Only sections with pages
  that exist are shown; Slice 0 has "Overview" and "Administration". Groups for "Clinical",
  "Operations" and "Finance" are defined in `nav.ts` but render only once they have items. Collapses
  to a 64 px icon rail with tooltips, and the collapsed state persists in localStorage. Below 1024 px
  it's a drawer, as today, with focus trap and Esc to close.
- Top bar: breadcrumbs, search button that opens the command palette (shows the "Ctrl K" hint),
  theme toggle, session timer, user menu (profile info, change password, sign out).
- Command palette: navigate to any page the user may access (RBAC-filtered from `nav.ts`), plus
  actions they may perform (e.g. "Create user" for USER_MANAGE). It's registry-driven so later
  slices add entries; no fake entries.

### 6.4 Screens
- **Login:** two-panel on ≥1024 px. The brand panel shows teal gradient art (CSS/SVG, no stock
  photos), the hospital name from public settings and a short tagline. The form panel stays
  single-column below 1024 px. It keeps every behaviour and test of today's form (errors, lockout
  messages, forced password change).
- **Home:** greeting + role badge + today's date (Asia/Colombo). For users with the right
  permissions: StatCards (active users, users by role, logins today, failed logins today), an
  AreaTrend of audit events per day over 14 days, and a "Recent activity" list (latest 8 audit
  entries). Users without those permissions see a clean welcome plus quick links from the command
  registry. No placeholder widgets.
- **Users / Settings / Audit / Change password / Forbidden / Not found / timeout dialog:**
  rebuilt on PageHeader + kit. Behaviour unchanged, plus better filters (Audit: date range,
  action, actor) where the existing API already supports them.

### 6.5 Server additions (small, TDD, same layering)
- `GET /api/users/summary` (USER_MANAGE): counts total, active, and by role.
- `GET /api/audit/summary?days=14` (AUDIT_VIEW, days 1–90): per-day counts in Asia/Colombo, plus
  today's counts of the existing `LOGIN` and `LOGIN_FAILED` audit actions. Reuse
  `lib/dateRange.ts` for Colombo day boundaries. `AuditLog.createdAt` is already indexed.
- The Audit page filters use the query params the list API already accepts (`userId`, `action`,
  `entityType`, `from`, `to`), so the Audit filters need no server change.
- Response shapes go in `@hms/shared` as zod schemas. They read existing tables only, with no new
  migrations unless an index is needed for the per-day query.

### 6.6 Public hospital website (landing page) *(added 2026-11-18)*
**Who it's for:** patients and visitors arriving at the hospital's address. Staff use it as the
way in to sign-in. The user chose this over a page showcasing the HMS software.

**Routes**
- `/` is the public landing page, with no auth required.
- The signed-in overview moves from `/` to `/dashboard`.
- A signed-in user who opens `/` is redirected to `/dashboard`, and so is a user whose sign-in
  succeeds.
- `/login` stays. Sidebar "Home" becomes "Dashboard" → `/dashboard`.
- RequireAuth's redirect target for logged-out users stays `/login`.
- Update the e2e smoke and client tests that assume `/` is the app home.

**Content.** All of it comes from settings, editable in Admin → Settings, with no hard-coded
hospital facts:
- **Header:** hospital name and logo mark, anchor links (About, Services, Visit us, Contact),
  theme toggle, **Sign in** button.
- **Hero:** hospital name, tagline, the emergency number as a prominent `tel:` link, and CTAs
  "Contact us" and "Sign in". The art is teal gradient CSS/SVG, with no stock photos and no
  external images.
- **Emergency strip:** "24/7 Emergency" + number, high-contrast danger tone. It shows only if an
  emergency number is set.
- **About:** the hospital's about text.
- **Services:** in this phase, a short list of the care areas the hospital offers, held as a
  settings list (`hospitalServices`, up to 12 items of name + one-line description). Slice 1
  replaces this with live departments from a public read-only departments endpoint and adds an
  "Our doctors" section. Slice 12 adds **Sign up** (patient portal) and a "Book an appointment"
  CTA. Until then neither appears.
- **Visit us:** address, opening hours (multi-line text), phone, email (`mailto:`). No map embed:
  it would load a third-party script and cookies.
- **Footer:** name, address, phone, © year, Sign in link.

**Settings additions**, in `settingsSchema`, the seed and the Settings page form:
- `hospitalEmail` (optional email)
- `hospitalEmergencyPhone` (≤30)
- `hospitalTagline` (≤140)
- `hospitalAbout` (≤1200, plain text, rendered with line breaks, never as HTML)
- `hospitalOpeningHours` (≤300, multi-line)
- `hospitalServices` (array, ≤12, each `{ name ≤60, description ≤160 }`)

The seed fills realistic Sri Lankan values, matching the existing seed's style.

**Server**
- `GET /api/public/hospital` has **no auth** and returns only the fields above plus
  `hospitalName`, `hospitalAddress`, `hospitalPhone`. It's an explicit allow-list, so it never
  leaks tax, timeout or currency settings.
- Responses are `Cache-Control: public, max-age=300` and rate-limited like other unauthenticated
  routes. The response schema is in `@hms/shared`.
- The existing authenticated `/api/settings/public` stays as is.

**Behaviour and quality**
- If the public endpoint fails or a field is empty, the section hides or the page shows a calm
  fallback (name + Sign in). The page never crashes or shows "undefined".
- Same tokens, light/dark and reduced-motion rules as the app. Semantic landmarks (`header`,
  `nav`, `main`, `footer`), a skip link, one `h1`.
- `<title>` and meta description come from the hospital name and tagline.
- Responsive at 375 / 768 / 1024 / 1440 px. The header collapses to a menu button below 768 px.
- Lighthouse accessibility ≥ 95 and no layout shift from fonts (Poppins is self-hosted, `font-display: swap`).
- 21st.dev: may fetch one hero / landing-section reference and one footer reference, adapted to
  tokens, within the ~6-retrieval budget (raise it to ~8).

## 7. Spec changes that ship with this
- Rewrite `2026-11-17-hms-design.md` §8.2 to point here. Replace "Light theme only" and
  "ui-ux-pro-max must not override" with: *tokens.css is authoritative; ui-ux-pro-max and 21st.dev
  inform design within it; token changes are made in this spec first.*
- §8.1 gains: command palette, theme switch, grouped nav, public hospital website at `/`
  (dashboard moves to `/dashboard`). §7.3 (Slice 1) gains a public read-only departments endpoint
  for the website. §7.12 (Slice 12) gains the website's Sign up and Book CTAs.
- Separate from this doc: the production-hardening amendment noted on 2026-11-18 (CSRF/Origin
  checks, trust proxy, DB-wipe guard, secrets, huge-seed indexes) must land before Slice 1. It's
  tracked separately so this phase stays visual.

## 8. Testing and done criteria
- All existing Vitest suites (shared, server, client) and the Playwright auth smoke pass. Tests
  may change selectors, but must not weaken assertions.
- New: a **contrast test** that parses `tokens.css` for both themes and asserts AA (4.5:1 text,
  3:1 for UI and large text) for every foreground/background pair the kit uses. Theme tests cover
  System following the media query, persistence, and no crash when storage throws. Tests also cover
  command palette RBAC filtering, sidebar group hiding, StatCard delta semantics, DataTable sort +
  `aria-sort`, and the two summary endpoints (permissions, Colombo day boundaries, `days` bounds).
  Landing page: the public endpoint returns only allow-listed fields, without a session, and new
  settings validate. Client-side, sections hide when empty, the page survives an endpoint error,
  `/` redirects signed-in users to `/dashboard`, and the emergency link is a `tel:` link.
- Playwright: extend the smoke to toggle dark mode and open the command palette for one role.
  Capture light + dark screenshots of the landing page, Login and Dashboard as review artifacts (not
  pixel assertions). The smoke starts at `/` and reaches sign-in via the landing page's Sign in
  button.
- ui-ux-pro-max pre-delivery checklist run on every re-skinned screen at 375 / 768 / 1024 / 1440 px.
- `npm run lint`, `npm run typecheck`, `npm test`, `npm run e2e` green.

## 9. Delivery
One local branch `design-system-refresh`. Order: tokens + theming + contrast test → kit → shell
+ command palette → summary endpoints → public endpoint + settings additions → landing page +
route move → screens → spec §8 rewrite → review → merge to local main.
Never pushed. `/graphify . --update` after merge, per the main spec's workflow.

## 10. Left to the builder
Exact shades that pass the contrast test, which 21st.dev components to fetch, chart details,
icon choices, animation details within the motion tokens, file split inside `components/ui`, and
whether DataTable keeps its old API or call sites move.
