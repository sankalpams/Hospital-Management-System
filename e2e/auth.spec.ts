import { expect, test, type Page } from '@playwright/test';

const PASSWORD = 'Password1!';

const DEMO_USERS = [
  { username: 'admin', fullName: 'Nimal Perera' },
  { username: 'doctor', fullName: 'Dr. Kumari Fernando' },
  { username: 'nurse', fullName: 'Tharushi Jayasinghe' },
  { username: 'reception', fullName: 'Mohamed Rizwan' },
  { username: 'lab', fullName: 'Suresh Kumar' },
  { username: 'pharmacist', fullName: 'Dilani Wickramasinghe' },
  { username: 'accountant', fullName: 'Ruwan Bandara' },
];

async function login(page: Page, username: string, password: string) {
  await page.goto('/login');
  await page.getByLabel('Username').fill(username);
  await page.getByLabel('Password', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Sign in' }).click();
}

async function logout(page: Page) {
  await page.locator('button[aria-haspopup="menu"]').click();
  await page.getByRole('menuitem', { name: 'Log out' }).click();
  await expect(page).toHaveURL(/\/login$/);
}

test.describe('authentication smoke', () => {
  for (const { username, fullName } of DEMO_USERS) {
    test(`${username} can sign in and out`, async ({ page }) => {
      await login(page, username, PASSWORD);
      await expect(page.getByRole('heading', { name: `Welcome, ${fullName}` })).toBeVisible();
      await logout(page);
      await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible();
    });
  }

  test('a wrong password shows an alert and stays on the login page', async ({ page }) => {
    await login(page, 'admin', 'WrongPassword9');
    await expect(page.getByRole('alert')).toBeVisible();
    await expect(page).toHaveURL(/\/login$/);
  });

  test('admin creates a user who must change the temporary password at first sign-in', async ({
    page,
  }) => {
    const username = `e2e.${Date.now().toString(36)}`;
    const fullName = 'E2E Newcomer';
    const newPassword = 'Brand-new1pass';

    await login(page, 'admin', PASSWORD);
    await page.getByRole('link', { name: 'Users' }).click();
    await page.getByRole('button', { name: 'Add user' }).click();
    await page.getByLabel('Username').fill(username);
    await page.getByLabel('Full name').fill(fullName);
    await page.getByRole('button', { name: 'Create user' }).click();

    const tempDialog = page.getByRole('dialog', { name: 'Temporary password' });
    await expect(tempDialog).toBeVisible();
    const temporaryPassword = (await tempDialog.locator('code').innerText()).trim();
    expect(temporaryPassword.length).toBeGreaterThanOrEqual(8);
    await tempDialog.getByRole('button', { name: 'Done' }).click();
    await logout(page);

    await login(page, username, temporaryPassword);
    await expect(page).toHaveURL(/\/change-password$/);
    await page.getByLabel('Current password').fill(temporaryPassword);
    await page.getByLabel('New password', { exact: true }).fill(newPassword);
    await page.getByLabel('Confirm new password').fill(newPassword);
    await page.getByRole('button', { name: 'Change password' }).click();

    await expect(page).toHaveURL(/\/$/);
    await expect(page.getByRole('heading', { name: `Welcome, ${fullName}` })).toBeVisible();
  });
});
