import { readFileSync } from 'node:fs';
import path from 'node:path';
import { expect, test } from '@playwright/test';
import { AUTH_DIR, ROLES } from './global-setup.js';

/**
 * Per-role smoke: the sidebar shows exactly the screens the role's permissions allow (same rule as
 * src/config/navigation.js), every one of them loads without a crash, console error, server error or
 * a 403 from an API call the screen should not have made, and a forbidden URL shows the access page.
 */
const credentials = () => JSON.parse(readFileSync(path.join(AUTH_DIR, 'credentials.json'), 'utf8'));

// Mirrors NAV_GROUPS (kept here as data so the test does not import React components into Node).
const NAV = [
  ['/', 'Dashboard', null],
  ['/reports', 'Reports', ['report:operational', 'report:financial', 'report:staff']],
  ['/my-leave', 'My leave', null],
  ['/patients', 'Patients', ['patient:read']],
  ['/appointments', 'Appointments', ['appointment:read']],
  ['/inpatients', 'Inpatients', ['admission:read', 'ward:manage']],
  ['/lab', 'Laboratory', ['lab:request:read']],
  ['/pharmacy', 'Pharmacy', ['prescription:read', 'medicine:read']],
  ['/billing', 'Billing', ['billing:read']],
  ['/doctors', 'Doctors', ['doctor:read']],
  ['/staff', 'Staff', ['employee:read', 'attendance:manage', 'leave:approve']],
  ['/departments', 'Departments', ['department:manage']],
  ['/users', 'Users', ['user:read']],
  ['/audit', 'Audit log', ['audit:read']],
];

function watch(page) {
  const problems = [];
  page.on('console', (m) => { if (m.type() === 'error' && !/Failed to load resource/.test(m.text())) problems.push(`console: ${m.text()}`); });
  page.on('pageerror', (e) => problems.push(`page error: ${e.message}`));
  page.on('response', (r) => {
    if (r.url().includes('/api/') && (r.status() === 403 || r.status() >= 500)) problems.push(`${r.status()} ${r.request().method()} ${new URL(r.url()).pathname}`);
  });
  return problems;
}

for (const role of ROLES) {
  test.describe(role, () => {
    test.use({ storageState: path.join(AUTH_DIR, `${role}.json`) });

    test('sees exactly its screens and each one loads cleanly', async ({ page }) => {
      const { permissions } = credentials()[role];
      const allowed = NAV.filter(([, , need]) => !need || need.some((p) => permissions.includes(p)));
      const problems = watch(page);

      await page.goto('/');
      const sidebar = page.locator('#app-sidebar');
      await expect(sidebar.getByRole('link', { name: 'Dashboard' })).toBeVisible();
      const shown = await sidebar.getByRole('link').evaluateAll((links) => links.map((a) => a.getAttribute('href')).filter((h) => h !== '/' || true));
      expect(shown.filter((h) => NAV.some(([to]) => to === h)).sort()).toEqual(allowed.map(([to]) => to).sort());

      for (const [to, label] of allowed) {
        await page.goto(to);
        await expect(page.getByRole('heading', { level: 1 }).first(), `${label} heading`).toBeVisible();
        await expect(page.getByText('Something went wrong on this screen')).toHaveCount(0);
        await expect(page.getByText('You don’t have access to this page')).toHaveCount(0);
        await page.waitForLoadState('networkidle');
      }
      expect(problems, `${role}: problems while visiting ${allowed.map(([, l]) => l).join(', ')}`).toEqual([]);
    });

    test('is turned away from a screen it may not use', async ({ page }) => {
      const { permissions } = credentials()[role];
      const forbidden = NAV.find(([, , need]) => need && !need.some((p) => permissions.includes(p)));
      test.skip(!forbidden, 'this role can open every screen');
      await page.goto(forbidden[0]);
      await expect(page.getByText('You don’t have access to this page')).toBeVisible();
    });
  });
}

test('signing in through the form lands on the dashboard; signing out ends the session', async ({ page }) => {
  const { username, password } = credentials().RECEPTIONIST;
  await page.goto('/login');
  await page.getByLabel('Username').fill(username);
  await page.locator('input[autocomplete="current-password"]').fill(password);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page).toHaveURL(/\/$/);
  await expect(page.getByRole('heading', { level: 1 })).toContainText('Good');
  await page.getByRole('button', { name: /account menu/i }).click();
  await page.getByRole('menuitem', { name: /sign out/i }).click();
  await expect(page).toHaveURL(/\/login/);
  await page.goto('/patients');
  await expect(page).toHaveURL(/\/login/);
});
