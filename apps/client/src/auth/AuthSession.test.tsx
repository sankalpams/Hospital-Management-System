import { http, HttpResponse } from 'msw';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest';
import { server } from '../test/server';
import { renderWithProviders } from '../test/render';
import { App } from '../App';
import { useAuth } from './useAuth';

const nurse = { id: '1', username: 'n', fullName: 'Nina', role: 'NURSE', mustChangePassword: false };

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

function Probe() {
  const { expiresAt, login } = useAuth();
  return (
    <div>
      <span data-testid="exp">{expiresAt ? expiresAt.toISOString() : 'none'}</span>
      <button onClick={() => void login('n', 'p')}>go</button>
    </div>
  );
}

describe('session expiry bookkeeping', () => {
  it('login() stores expiresAt from the response header with no other request', async () => {
    const when = new Date(Date.now() + 15 * 60_000);
    server.use(
      http.get('*/api/v1/auth/me', () => HttpResponse.json({ error: { code: 'UNAUTHENTICATED', message: 'x' } }, { status: 401 })),
      http.post('*/api/v1/auth/login', () =>
        HttpResponse.json({ user: nurse }, { headers: { 'X-Session-Expires-At': when.toISOString() } }),
      ),
    );
    renderWithProviders(<Probe />);
    await waitFor(() => expect(screen.getByTestId('exp')).toHaveTextContent('none'));
    await userEvent.click(screen.getByRole('button', { name: 'go' }));
    await waitFor(() => expect(screen.getByTestId('exp')).toHaveTextContent(when.toISOString()));
  });

  it('expiry at 0 with /auth/me 401 ends on /login?reason=timeout showing the expired message', async () => {
    let calls = 0;
    server.use(
      http.get('*/api/v1/auth/me', () => {
        calls++;
        if (calls === 1) {
          return HttpResponse.json({ user: nurse, expiresAt: new Date(Date.now() + 1500).toISOString() });
        }
        return HttpResponse.json({ error: { code: 'UNAUTHENTICATED', message: 'x' } }, { status: 401 });
      }),
      http.post('*/api/v1/auth/logout', () =>
        HttpResponse.json({ error: { code: 'UNAUTHENTICATED', message: 'x' } }, { status: 401 }),
      ),
      http.get('*/api/v1/settings/public', () => HttpResponse.json({ hospitalName: 'H' })),
    );
    renderWithProviders(<App />, '/');
    expect(await screen.findByText(/Welcome, Nina/)).toBeInTheDocument();
    expect(
      await screen.findByText('Your session expired. Please sign in again.', {}, { timeout: 6000 }),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Sign in' })).toBeInTheDocument();
  }, 10_000);
});
