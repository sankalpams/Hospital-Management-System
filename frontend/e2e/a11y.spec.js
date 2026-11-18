import path from 'node:path';
import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { AUTH_DIR } from './global-setup.js';

/**
 * Automated accessibility scan (axe-core, WCAG 2.1 A/AA rules) of every main screen and the main forms.
 * Serious and critical findings fail the test; automated checks catch roughly a third of issues,
 * so this complements, not replaces, the keyboard and screen-reader checks in docs/07.
 */
const SCREENS = ['/', '/reports', '/my-leave', '/patients', '/patients/new', '/appointments', '/appointments/new', '/inpatients',
  '/inpatients?tab=admissions', '/lab', '/pharmacy', '/pharmacy?tab=stock', '/billing', '/doctors', '/staff', '/staff?tab=attendance',
  '/departments', '/users', '/audit'];

async function scan(page) {
  const { violations } = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
  return violations.filter((v) => v.impact === 'serious' || v.impact === 'critical')
    .map((v) => `${v.id} (${v.impact}): ${v.nodes.slice(0, 3).map((n) => n.target.join(' ')).join(' | ')}`);
}

test.describe('screens', () => {
  test.use({ storageState: path.join(AUTH_DIR, 'SUPER_ADMIN.json') });

  for (const url of SCREENS) {
    test(`no serious accessibility violations on ${url}`, async ({ page }) => {
      await page.goto(url);
      await expect(page.getByRole('heading', { level: 1 }).first()).toBeVisible();
      await page.waitForLoadState('networkidle');
      expect(await scan(page)).toEqual([]);
    });
  }
});

test('the login page', async ({ page }) => {
  await page.goto('/login');
  await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible();
  expect(await scan(page)).toEqual([]);
});
