import { http, HttpResponse } from 'msw';
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
