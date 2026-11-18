# HMS Design System — MASTER

> Global source of truth for every screen. A page-specific file in `pages/<page>.md`, when one exists, overrides this file for that page only.
> Token values live in [`tokens.json`](tokens.json). [`frontend/src/styles/tokens.css`](../../frontend/src/styles/tokens.css) is generated from it, so never edit the CSS by hand.
> Built with the ui-ux-pro-max design plugin (design-system → ui-styling workflow), using the brand palette from the project brief.

## 1. Direction

| Decision | Choice | Why |
|---|---|---|
| Product type | Healthcare operations / admin panel | The plugin's product search matched **Medical Clinic**: "Accessible & Ethical + Minimalism & Swiss Style", dashboard style "Healthcare Analytics" |
| Style | **Minimalism & Swiss** for structure, **Data-Dense Dashboard** for analytics pages | Clean grid, white space, one accent colour, clear type hierarchy. Dense KPI/table layout only where the data needs it. |
| Rejected | Glassmorphism, dark OLED (the plugin's first, generic "dashboard" match) | The brief asks for soft blue backgrounds with white cards and no heavy effects. Backdrop blur also hurts legibility on clinical data. |
| Mode | Light only, for now | The brief specifies the light palette. The tokens are layered, so a dark theme can be added later without changing components. |
| Icons | **Lucide** (`lucide-react`), stroke 1.75, sizes 16/20/24 | One consistent outline set. No emoji as icons. |

## 2. Colour

### Brand palette (exactly as supplied)

| Token | Hex | Role |
|---|---|---|
| `brand-100` | `#B7D3E3` | Soft fills, sidebar text, `primary-soft` badges |
| `brand-200` | `#A4C8E1` | Strong borders, selected rows, chart 3 |
| `brand-300` | `#8FB8D8` | Decorative, illustrations |
| `brand-400` | `#6FA3D8` | Chart 2, icon accents on white |
| `brand-500` | `#4A8BBE` | Accent, focus ring (3.67 : 1 against white, which passes the 3 : 1 rule for UI components) |

### Added shades (same hue, needed for accessibility)

| Token | Hex | Role | White text contrast |
|---|---|---|---|
| `brand-50` | `#EEF5FA` | App page background | — |
| `brand-600` | `#346F9E` | **Primary buttons, links, active nav** | 5.37 : 1 ✅ |
| `brand-700` | `#24537A` | Primary hover, info text | 8.10 : 1 ✅ |
| `brand-800` | `#1A3C59` | Sidebar background, headings on tints | 11.5 : 1 ✅ |

Why the extra shades: none of the five supplied blues reaches 4.5 : 1 with white text, so a "deep blue primary button" in `#4A8BBE` would fail WCAG AA for normal-size text. The 600–800 shades keep the same hue and fix that.

### Semantic tokens (components use only these, never raw hex)

| Purpose | Token | Text / soft background |
|---|---|---|
| Page | `background` / `surface` / `surface-muted` | `#EEF5FA` / `#FFFFFF` / `#F8FAFC` |
| Text | `foreground` / `foreground-muted` / `foreground-subtle` | slate-900 / slate-600 / `#5B6B82` |
| Lines | `border` / `border-strong` | slate-200 / brand-200 |
| Action | `primary` / `primary-hover` / `primary-foreground` | brand-600 / brand-700 / white |
| Status: success | `success` / `success-soft` | green-700 on green-50 (4.76) |
| Status: warning | `warning` / `warning-soft` | amber-700 on amber-50 (4.75) |
| Status: danger | `danger` / `danger-soft` | red-700 on red-50 (5.91) |
| Status: info | `info` / `info-soft` | brand-700 on brand-50 (7.36) |
| Status: neutral | `neutral` / `neutral-soft` | slate-700 on slate-100 (9.45) |
| Status: special | `special` / `special-soft` | violet-700 on violet-50 (6.48) |
| Charts | `chart-1` … `chart-6` | brand-600, brand-400, brand-200, amber-500, green-600, slate-400 |

All 17 text/background pairs are checked by `npm run check:contrast` (frontend), and the check fails the build if any pair drops below 4.5 : 1, or 3 : 1 for UI parts.

### Status → badge colour mapping (used everywhere)

| Tone | Statuses |
|---|---|
| neutral | PENDING, DRAFT, REQUESTED, INACTIVE |
| info | CONFIRMED, SAMPLE_COLLECTED, ISSUED, ON_LEAVE |
| special | CHECKED_IN, PROCESSING, IN_PROGRESS |
| success | COMPLETED, REVIEWED, PAID, ACTIVE, AVAILABLE, APPROVED |
| warning | PARTIALLY_PAID, NO_SHOW, CLEANING, LOW_STOCK, EXPIRING |
| danger | CANCELLED, VOID, OCCUPIED, EXPIRED, REJECTED, LOCKED |

**Every badge always shows its text label** as well as its colour; meaning is never carried by colour alone.

## 3. Typography

| Role | Font | Size / weight | Line-height |
|---|---|---|---|
| Page title (h1) | Poppins | 24px / 600 | 1.3 |
| Section title (h2) | Poppins | 18px / 600 | 1.4 |
| Card title (h3) | Poppins | 16px / 600 | 1.4 |
| KPI value | Poppins | 28px / 600, tabular numbers | 1.2 |
| Body | Noto Sans | 14px / 400 (dense tables), 16px for forms and reading | 1.5 |
| Label / caption | Noto Sans | 13px / 500 and 12px / 400 | 1.4 |
| IDs, MRN, codes | JetBrains Mono, falling back to Consolas | 13px | — |

- **Fonts:** Poppins for headings keeps the font choice you made for the previous HMS. Noto Sans for body text comes from the plugin's "Medical Clean" pairing (wide glyph coverage, readable in dense tables; the plugin's pick for healthcare).
- **Numbers:** money and counts use `font-variant-numeric: tabular-nums` and are right-aligned in tables.
- **Minimum size:** body text is never smaller than 12px.

## 4. Spacing, radius, elevation

- 4px base grid. Card padding is 20–24px, grid gap is 16–24px, and data-dense pages use 12px.
- **Radius:** inputs and buttons `md` (8px), cards `lg` (12px), modals `xl` (16px), badges `full`.
- **Shadows** are tinted toward brand-800, not grey:
  - cards: `shadow-sm`
  - dropdowns and popovers: `shadow-md`
  - modals: `shadow-lg`
- Gradients are never used (per the brief).

## 5. Layout

```
┌──────────┬──────────────────────────────────────────────┐
│          │ Topbar 64px: search · notifications · user   │
│ Sidebar  ├──────────────────────────────────────────────┤
│ 256px    │ Page header: title, breadcrumb, primary act. │
│ brand-800│ ┌──────────── content (max 1440px) ───────┐ │
│          │ │ white cards on brand-50 background       │ │
│          │ └──────────────────────────────────────────┘ │
└──────────┴──────────────────────────────────────────────┘
```

- **Sidebar:**
  - Navigation is grouped by area: Overview, Patients & Care, Clinical Services, Finance, Administration.
  - Only items the user has permission for are rendered.
  - Below 1024px the sidebar collapses to a drawer.
- **Breakpoints:** 375 / 768 / 1024 / 1440. No horizontal page scroll; wide tables scroll inside their card.
- **One primary action per page**, top-right of the page header.

## 6. Components (states)

| Component | Default | Hover | Focus-visible | Disabled | Loading |
|---|---|---|---|---|---|
| Button / primary | `primary` bg, white | `primary-hover` | 2px `ring` + 2px offset | 50% opacity, `not-allowed` | spinner + label kept, `aria-busy` |
| Button / secondary | white, `border-strong`, `primary` text | `primary-soft` bg | same ring | same | same |
| Button / ghost | transparent, muted text | `surface-muted` | same ring | same | same |
| Button / danger | `danger` bg, white | darker | same ring | same | same |
| Input / select | white, `border`, 40px tall | `border-strong` | `ring` 2px | `surface-muted` bg | — |
| Input / error | `danger` border; message below the field with an icon | — | ring in `danger` | — | — |
| Table row | white | `surface-muted` | row focus ring (keyboard) | — | skeleton rows |
| Card | `surface`, `border`, `shadow-sm` | (clickable cards only) `shadow-md` | ring | — | skeleton |

**Transitions:** 150–200ms on colour and shadow only. Width and height are never animated. `prefers-reduced-motion` turns transitions off.

### Required interaction patterns (from the plugin's UX guidelines and the brief)

- **Forms:**
  - Every field has a visible label (never placeholder-only).
  - Errors show next to the field. An error summary on submit links to each field.
  - The first invalid field gets focus.
- **Tables:**
  - Search with a 300ms debounce, filters, sortable headers (`aria-sort`), server-side pagination.
  - An empty state with an explanation and the next action.
- **Feedback:**
  - A toast for success.
  - Inline alerts for errors, showing the request ID from the API error body.
- **Confirmations:** destructive and irreversible actions (cancel appointment, void invoice, discharge, deactivate user) open a confirm dialog that states the consequence; some also require a reason field.
- **Loading:** skeletons for page content, spinners only inside buttons. Space is reserved so the layout doesn't shift.
- **Accessibility:**
  - Icon-only buttons have an `aria-label`.
  - Focus rings are never removed.
  - Dialogs trap focus and close on Esc.
  - Clickable elements are at least 44×44px on touch.

## 7. Charts (Recharts)

| Data | Chart | Notes |
|---|---|---|
| Trend over time (registrations, revenue) | Line, or area with a 20% fill | Fewer than 4 points → use a stat card instead |
| Comparison across categories (departments, doctors) | Horizontal bar | Sorted by value; direct value labels |
| Share of a whole (appointment status, payment method) | Doughnut | At most 6 slices, the rest grouped as "Other"; legend with values |
| Detail | Table under the chart | Doubles as the accessible fallback |

- **Colours:** series use `chart-1…6` in order.
- **Multiple series:** distinguished by line style (solid, dashed, dotted) as well as colour.
- **Tooltips:** show exact values with units (LKR, count).
- **Data states:** every chart has empty, loading and error states. Charts only ever show API data.

## 8. Anti-patterns (do not ship)

- Raw hex values or Tailwind default colours (`bg-blue-500`, etc.) in components; use the semantic classes. Checked with `validate-tokens.cjs`.
- Gradients, glassmorphism, decorative animation, emoji icons.
- Hard-coded numbers on dashboards.
- Placeholder buttons or menu items that do nothing.
- Colour-only status indicators.
- Modals for long forms; use a full page or a side sheet.

## 9. Pre-delivery checklist (per page)

- [ ] Only semantic tokens; `validate-tokens` passes
- [ ] Text contrast at least 4.5 : 1; focus visible on every interactive element
- [ ] Keyboard path works (Tab order, Esc closes dialogs, Enter submits)
- [ ] Loading, empty and error states present
- [ ] Responsive at 375, 768, 1024 and 1440 px; no horizontal page scroll
- [ ] `prefers-reduced-motion` respected
- [ ] Every action is wired to a real API and checked against permissions
