import { delay, http, HttpResponse } from 'msw';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterAll, afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import { server } from '../../test/server';
import { renderWithProviders } from '../../test/render';
import { App } from '../../App';

const admin = { id: 'a1', username: 'admin', fullName: 'Admin', role: 'ADMIN', mustChangePassword: false };
const row = (o: Record<string, unknown>) => ({
  id: 'u1',
  username: 'nurse',
  fullName: 'Nina Nurse',
  email: null,
  role: 'NURSE',
  isActive: true,
  mustChangePassword: false,
  lastLoginAt: null,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
  ...o,
});

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => {
  server.resetHandlers();
  vi.restoreAllMocks();
});
afterAll(() => server.close());

function setup(items = [row({})]) {
  server.use(
    http.get('*/api/v1/auth/me', () => HttpResponse.json({ user: admin, expiresAt: null })),
    http.get('*/api/v1/settings/public', () => HttpResponse.json({ hospitalName: 'H' })),
    http.get('*/api/v1/users', () => HttpResponse.json({ items, page: 1, pageSize: 25, total: items.length })),
  );
  renderWithProviders(<App />, '/admin/users');
}

async function fillCreate() {
  await userEvent.click(await screen.findByRole('button', { name: 'Add user' }));
  const dialog = await screen.findByRole('dialog', { name: 'Add user' });
  await userEvent.type(within(dialog).getByLabelText('Username'), 'newdoc');
  await userEvent.type(within(dialog).getByLabelText('Full name'), 'New Doc');
  await userEvent.selectOptions(within(dialog).getByLabelText('Role'), 'DOCTOR');
  return dialog;
}

describe('UsersPage', () => {
  it('lists users in an accessible table', async () => {
    setup();
    expect(await screen.findByRole('table', { name: 'Users' })).toBeInTheDocument();
    expect(await screen.findByText('Nina Nurse')).toBeInTheDocument();
  });

  it('shows the temporary password once after create, with a copy button', async () => {
    setup();
    server.use(
      http.post('*/api/v1/users', () =>
        HttpResponse.json(
          {
            user: row({ id: 'u2', username: 'newdoc', fullName: 'New Doc', role: 'DOCTOR' }),
            temporaryPassword: 'Tmp9xyzabc12',
          },
          { status: 201 },
        ),
      ),
    );
    const dialog = await fillCreate();
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create user' }));
    const shown = await screen.findByRole('dialog', { name: 'Temporary password' });
    expect(within(shown).getByText('Tmp9xyzabc12')).toBeInTheDocument();
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true });
    await userEvent.click(within(shown).getByRole('button', { name: 'Copy' }));
    expect(writeText).toHaveBeenCalledWith('Tmp9xyzabc12');
    expect(await within(shown).findByText('Copied')).toBeInTheDocument();
    await userEvent.click(within(shown).getByRole('button', { name: 'Done' }));
    expect(screen.queryByText('Tmp9xyzabc12')).not.toBeInTheDocument();
  });

  it('handles clipboard failure', async () => {
    setup();
    server.use(
      http.post('*/api/v1/users', () =>
        HttpResponse.json({ user: row({ id: 'u2' }), temporaryPassword: 'Tmp9xyzabc12' }, { status: 201 }),
      ),
    );
    Object.defineProperty(navigator, 'clipboard', {
      value: { writeText: vi.fn().mockRejectedValue(new Error('denied')) },
      configurable: true,
    });
    const dialog = await fillCreate();
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create user' }));
    const shown = await screen.findByRole('dialog', { name: 'Temporary password' });
    await userEvent.click(within(shown).getByRole('button', { name: 'Copy' }));
    expect(await within(shown).findByText(/copy manually/i)).toBeInTheDocument();
  });

  it('shows USERNAME_TAKEN inline on the username field', async () => {
    setup();
    server.use(
      http.post('*/api/v1/users', () =>
        HttpResponse.json(
          { error: { code: 'USERNAME_TAKEN', message: 'Username is already taken' } },
          { status: 409 },
        ),
      ),
    );
    const dialog = await fillCreate();
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create user' }));
    expect(await within(dialog).findByText('Username is already taken')).toBeInTheDocument();
  });

  it('validates with the shared schema', async () => {
    setup();
    await userEvent.click(await screen.findByRole('button', { name: 'Add user' }));
    const dialog = await screen.findByRole('dialog', { name: 'Add user' });
    await userEvent.type(within(dialog).getByLabelText('Username'), 'ab');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create user' }));
    expect(await within(dialog).findByText(/Username must be 3-32/)).toBeInTheDocument();
  });

  it('deactivation needs confirmation and LAST_ADMIN shows a toast', async () => {
    setup([row({ id: 'a2', username: 'boss', fullName: 'Boss', role: 'ADMIN' })]);
    let patched = 0;
    server.use(
      http.patch('*/api/v1/users/a2', async ({ request }) => {
        patched++;
        expect(await request.json()).toEqual({ isActive: false });
        return HttpResponse.json(
          { error: { code: 'LAST_ADMIN', message: 'At least one active administrator must remain' } },
          { status: 409 },
        );
      }),
    );
    await userEvent.click(await screen.findByRole('button', { name: 'Deactivate Boss' }));
    expect(patched).toBe(0);
    const confirm = await screen.findByRole('dialog', { name: 'Deactivate user' });
    await userEvent.click(within(confirm).getByRole('button', { name: 'Deactivate' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('At least one active administrator must remain');
    expect(patched).toBe(1);
  });

  it('reset password asks for confirmation then reveals the temporary password', async () => {
    setup();
    server.use(
      http.post('*/api/v1/users/u1/reset-password', () => HttpResponse.json({ temporaryPassword: 'Rst77abcdefg' })),
    );
    await userEvent.click(await screen.findByRole('button', { name: 'Reset password for Nina Nurse' }));
    const confirm = await screen.findByRole('dialog', { name: 'Reset password' });
    await userEvent.click(within(confirm).getByRole('button', { name: 'Reset password' }));
    expect(await screen.findByText('Rst77abcdefg')).toBeInTheDocument();
  });

  it('shows an empty message', async () => {
    setup([]);
    await waitFor(() => expect(screen.getByText('No users match your filters.')).toBeInTheDocument());
  });
});

describe('UsersPage fix round 1', () => {
  it('refreshes the list after a reset', async () => {
    let gets = 0;
    setup();
    server.use(
      http.get('*/api/v1/users', () => {
        gets++;
        return HttpResponse.json({ items: [row({})], page: 1, pageSize: 25, total: 1 });
      }),
      http.post('*/api/v1/users/u1/reset-password', () => HttpResponse.json({ temporaryPassword: 'Rst77abcdefg' })),
    );
    await userEvent.click(await screen.findByRole('button', { name: 'Reset password for Nina Nurse' }));
    const before = gets;
    const confirm = await screen.findByRole('dialog', { name: 'Reset password' });
    await userEvent.click(within(confirm).getByRole('button', { name: 'Reset password' }));
    await screen.findByText('Rst77abcdefg');
    await waitFor(() => expect(gets).toBeGreaterThan(before));
  });

  it('disables reset and deactivate on your own row', async () => {
    setup([row({ id: 'a1', username: 'admin', fullName: 'Admin', role: 'ADMIN' })]);
    expect(await screen.findByRole('button', { name: 'Reset password for Admin' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Deactivate Admin' })).toBeDisabled();
  });

  it('Escape does not dismiss the one-time temporary password dialog; Done does', async () => {
    setup();
    server.use(
      http.post('*/api/v1/users/u1/reset-password', () => HttpResponse.json({ temporaryPassword: 'Rst77abcdefg' })),
    );
    await userEvent.click(await screen.findByRole('button', { name: 'Reset password for Nina Nurse' }));
    const confirm = await screen.findByRole('dialog', { name: 'Reset password' });
    await userEvent.click(within(confirm).getByRole('button', { name: 'Reset password' }));
    await screen.findByText('Rst77abcdefg');
    await userEvent.keyboard('{Escape}');
    expect(screen.getByText('Rst77abcdefg')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Done' }));
    expect(screen.queryByText('Rst77abcdefg')).not.toBeInTheDocument();
  });

  it('editing with no changes sends no request and just closes', async () => {
    let patched = 0;
    setup();
    server.use(
      http.patch('*/api/v1/users/u1', () => {
        patched++;
        return HttpResponse.json(row({}));
      }),
    );
    await userEvent.click(await screen.findByRole('button', { name: 'Edit Nina Nurse' }));
    const dialog = await screen.findByRole('dialog', { name: 'Edit user' });
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save changes' }));
    await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Edit user' })).not.toBeInTheDocument());
    expect(patched).toBe(0);
  });

  it('locks the role field when editing yourself', async () => {
    setup([row({ id: 'a1', username: 'admin', fullName: 'Admin', role: 'ADMIN' })]);
    await userEvent.click(await screen.findByRole('button', { name: 'Edit Admin' }));
    const dialog = await screen.findByRole('dialog', { name: 'Edit user' });
    expect(within(dialog).getByLabelText('Role')).toBeDisabled();
  });

  it('keeps the previous page data (and page count) while the next page loads', async () => {
    setup();
    server.use(
      http.get('*/api/v1/users', async ({ request }) => {
        const page = Number(new URL(request.url).searchParams.get('page'));
        if (page === 2) await delay(300);
        return HttpResponse.json({ items: [row({ id: `p${page}`, fullName: `Person ${page}` })], page, pageSize: 25, total: 60 });
      }),
    );
    expect(await screen.findByText('Page 1 of 3')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Next page' }));
    expect(screen.getByText('Page 2 of 3')).toBeInTheDocument();
    expect(screen.getByText('Person 1')).toBeInTheDocument();
    expect(await screen.findByText('Person 2')).toBeInTheDocument();
  });
});
