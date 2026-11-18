import path from 'node:path';
import { expect, test } from '@playwright/test';
import { AUTH_DIR } from './global-setup.js';

/**
 * Responsive check: the busiest screens at phone, tablet, laptop and desktop widths. The page itself
 * must never scroll sideways (wide tables scroll inside their own container), and the navigation must be
 * reachable: a menu button below the desktop breakpoint, the sidebar from it upwards.
 */
const WIDTHS = [375, 768, 1024, 1440];
const SCREENS = ['/', '/patients', '/appointments', '/inpatients', '/pharmacy', '/billing', '/staff?tab=attendance', '/reports', '/users', '/audit'];

test.use({ storageState: path.join(AUTH_DIR, 'SUPER_ADMIN.json') });

for (const width of WIDTHS) {
  test(`no sideways scrolling at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    const overflowing = [];
    for (const url of SCREENS) {
      await page.goto(url);
      await expect(page.getByRole('heading', { level: 1 }).first()).toBeVisible();
      await page.waitForLoadState('networkidle');
      const { scroll, inner, culprit } = await page.evaluate(() => {
        const inner = document.documentElement.clientWidth;
        const wide = [...document.querySelectorAll('body *')].find((el) => {
          const r = el.getBoundingClientRect();
          return r.right > inner + 1 && getComputedStyle(el).position !== 'fixed' && !el.closest('[class*="overflow-x-auto"],[class*="overflow-auto"]');
        });
        return { scroll: document.documentElement.scrollWidth, inner, culprit: wide ? `${wide.tagName.toLowerCase()}.${wide.className}`.slice(0, 120) : null };
      });
      if (scroll > inner) overflowing.push(`${url}: ${scroll}px > ${inner}px (${culprit})`);
    }
    expect(overflowing).toEqual([]);
  });
}

test('navigation is reachable on a phone and on a laptop', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 800 });
  await page.goto('/');
  const sidebarLink = page.locator('#app-sidebar').getByRole('link', { name: 'Patients', exact: true });
  await expect(sidebarLink).toBeHidden();
  await page.getByRole('button', { name: 'Open navigation' }).click();
  await page.getByRole('dialog').getByRole('link', { name: 'Patients', exact: true }).or(page.locator('#app-sidebar').getByRole('link', { name: 'Patients', exact: true })).first().click();
  await expect(page).toHaveURL(/\/patients/);

  await page.setViewportSize({ width: 1024, height: 800 });
  await page.goto('/');
  await expect(page.locator('#app-sidebar').getByRole('link', { name: 'Patients', exact: true })).toBeVisible();
});
