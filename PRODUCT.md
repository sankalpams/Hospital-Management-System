# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users
- **Primary (landing page):** portfolio visitors (recruiters, prospective clients, reviewers) evaluating what the system does, then trying it by signing in.
- **Primary (application):** staff of a single hospital in nine roles: Super Admin, Hospital Admin, Doctor, Nurse, Receptionist, Lab Staff, Pharmacist, Accountant, HR Manager.

## Product Purpose
One system for a whole hospital: front desk, clinic, wards, laboratory, pharmacy, billing, staff and reports. Success for the showcase: a visitor understands the scope within seconds and signs in to try it.

## Positioning
Data integrity lives in the database (double booking and double bed occupancy are impossible by unique keys and row locks), every permission is enforced server-side, and every change is audited.

## Brand Commitments
- Hospital name shown to visitors: **Prabodha Hospital** (user-supplied, Nov 2026). The software itself is "HMS".
- Palette is pinned by the user: ivory #FDFDF9, sand #F3E6D7, tan #E1C7A5, sea teal #6AA6AA, deep teal #2C5E63 (tokens in design-system/hms/tokens.json).

## Evidence on Hand
- The working application and its synthetic demo data (1,000 patients online, labelled demo data).
- No testimonials, customer logos, certifications or usage figures exist. Do not invent them. Privacy follows Sri Lanka's PDPA principles but is **not** a compliance certification.

## Product Principles
1. Show the real system, not claims about it.
2. Safety and integrity are product features, not footnotes.
3. Plain language for hospital staff; no jargon about how it is built.

## Accessibility & Inclusion
WCAG 2.1 AA; contrast enforced by `npm run check:contrast`.
