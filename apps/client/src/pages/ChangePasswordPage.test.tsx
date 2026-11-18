import { http, HttpResponse } from 'msw';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest';
import { server } from '../test/server';
import { renderWithProviders } from '../test/render';
import { App } from '../App';

const user = (mustChangePassword: boolean) => ({
  id: '1',
  username: 'nurse',
  fullName: 'Nurse N',
  role: 'NURSE',
  mustChangePassword,
});

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

const me = (mustChange: boolean) =>
  http.get('*/api/v1/auth/me', () => HttpResponse.json({ user: user(mustChange), expiresAt: null }));

describe('forced password change', () => {
  it('redirects a mustChangePassword user from / to /change-password', async () => {
    server.use(me(true));
    renderWithProviders(<App />, '/');
    expect(await screen.findByRole('heading', { name: 'Change password' })).toBeInTheDocument();
    expect(screen.queryByText(/Welcome/)).not.toBeInTheDocument();
  });

  it('redirects from any other route too', async () => {
    server.use(me(true));
    renderWithProviders(<App />, '/admin/users');
    expect(await screen.findByRole('heading', { name: 'Change password' })).toBeInTheDocument();
  });

  it('shows the policy hint and a mismatch error when confirm differs', async () => {
    server.use(me(true));
    renderWithProviders(<App />, '/change-password');
    expect(await screen.findByText(/at least 8 characters/i)).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText('Current password'), 'Temp1234');
    await userEvent.type(screen.getByLabelText('New password'), 'Password1!');
    await userEvent.type(screen.getByLabelText('Confirm new password'), 'Different1!');
    await userEvent.click(screen.getByRole('button', { name: 'Change password' }));
    expect(await screen.findByText('Passwords do not match')).toBeInTheDocument();
  });

  it('rejects a weak new password using the shared policy', async () => {
    server.use(me(true));
    renderWithProviders(<App />, '/change-password');
    await userEvent.type(await screen.findByLabelText('Current password'), 'Temp1234');
    await userEvent.type(screen.getByLabelText('New password'), 'short');
    await userEvent.type(screen.getByLabelText('Confirm new password'), 'short');
    await userEvent.click(screen.getByRole('button', { name: 'Change password' }));
    expect(await screen.findByText('Password must be at least 8 characters')).toBeInTheDocument();
  });

  it('on success refreshes the user and goes home', async () => {
    let changed = false;
    server.use(
      http.get('*/api/v1/auth/me', () => HttpResponse.json({ user: user(!changed), expiresAt: null })),
      http.post('*/api/v1/auth/change-password', async ({ request }) => {
        const body = (await request.json()) as Record<string, string>;
        expect(body).toEqual({ currentPassword: 'Temp1234', newPassword: 'Password1!' });
        changed = true;
        return new HttpResponse(null, { status: 204 });
      }),
      http.get('*/api/v1/settings/public', () => HttpResponse.json({ hospitalName: 'H' })),
    );
    renderWithProviders(<App />, '/');
    await userEvent.type(await screen.findByLabelText('Current password'), 'Temp1234');
    await userEvent.type(screen.getByLabelText('New password'), 'Password1!');
    await userEvent.type(screen.getByLabelText('Confirm new password'), 'Password1!');
    await userEvent.click(screen.getByRole('button', { name: 'Change password' }));
    expect(await screen.findByText(/Welcome, Nurse N/)).toBeInTheDocument();
  });

  it('shows the server error for a wrong current password', async () => {
    server.use(
      me(true),
      http.post('*/api/v1/auth/change-password', () =>
        HttpResponse.json(
          { error: { code: 'INVALID_CURRENT_PASSWORD', message: 'Current password is incorrect' } },
          { status: 400 },
        ),
      ),
    );
    renderWithProviders(<App />, '/change-password');
    await userEvent.type(await screen.findByLabelText('Current password'), 'nope');
    await userEvent.type(screen.getByLabelText('New password'), 'Password1!');
    await userEvent.type(screen.getByLabelText('Confirm new password'), 'Password1!');
    await userEvent.click(screen.getByRole('button', { name: 'Change password' }));
    await waitFor(() => expect(screen.getByText('Current password is incorrect')).toBeInTheDocument());
  });
});
