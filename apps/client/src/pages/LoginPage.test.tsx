import { http, HttpResponse } from 'msw';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router-dom';
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it } from 'vitest';
import { server } from '../test/server';
import { renderWithProviders } from '../test/render';
import { LoginPage } from './LoginPage';

const adminUser = {
  id: '1',
  username: 'admin',
  fullName: 'System Admin',
  role: 'ADMIN',
  mustChangePassword: false,
};

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
beforeEach(() => {
  server.use(
    http.get('*/api/v1/auth/me', () =>
      HttpResponse.json({ error: { code: 'UNAUTHENTICATED', message: 'no' } }, { status: 401 }),
    ),
  );
});
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

function setup() {
  renderWithProviders(
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/" element={<div>HOME PAGE</div>} />
    </Routes>,
    '/login',
  );
}

async function submit(username: string, password: string) {
  await userEvent.type(await screen.findByLabelText('Username'), username);
  await userEvent.type(screen.getByLabelText('Password'), password);
  await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));
}

describe('LoginPage', () => {
  it('has labelled username and password fields', async () => {
    setup();
    expect(await screen.findByLabelText('Username')).toBeInTheDocument();
    expect(screen.getByLabelText('Password')).toHaveAttribute('type', 'password');
  });

  it('shows inline errors for empty fields', async () => {
    setup();
    await userEvent.click(await screen.findByRole('button', { name: 'Sign in' }));
    expect(await screen.findByText('Enter your username')).toBeInTheDocument();
    expect(screen.getByText('Enter your password')).toBeInTheDocument();
    expect(screen.getByLabelText('Username')).toHaveAttribute('aria-invalid', 'true');
  });

  it('shows invalid credentials in an alert on 401', async () => {
    server.use(
      http.post('*/api/v1/auth/login', () =>
        HttpResponse.json(
          { error: { code: 'INVALID_CREDENTIALS', message: 'Invalid username or password' } },
          { status: 401 },
        ),
      ),
    );
    setup();
    await submit('admin', 'wrong');
    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid username or password');
  });

  it('shows the lockout message on 423', async () => {
    server.use(
      http.post('*/api/v1/auth/login', () =>
        HttpResponse.json(
          { error: { code: 'ACCOUNT_LOCKED', message: 'Account locked. Try again in 15 minutes.' } },
          { status: 423 },
        ),
      ),
    );
    setup();
    await submit('doctor', 'x');
    expect(await screen.findByRole('alert')).toHaveTextContent('Account locked. Try again in 15 minutes.');
  });

  it('navigates to / on successful login', async () => {
    server.use(http.post('*/api/v1/auth/login', () => HttpResponse.json({ user: adminUser })));
    setup();
    await submit('admin', 'Password1!');
    await waitFor(() => expect(screen.getByText('HOME PAGE')).toBeInTheDocument());
  });

  it('shows the server message for 4xx such as an inactive account', async () => {
    server.use(
      http.post('*/api/v1/auth/login', () =>
        HttpResponse.json(
          { error: { code: 'ACCOUNT_INACTIVE', message: 'This account is inactive' } },
          { status: 403 },
        ),
      ),
    );
    setup();
    await submit('nurse', 'x');
    expect(await screen.findByRole('alert')).toHaveTextContent('This account is inactive');
  });

  it('shows a generic message on a network error', async () => {
    server.use(http.post('*/api/v1/auth/login', () => HttpResponse.error()));
    setup();
    await submit('nurse', 'x');
    expect(await screen.findByRole('alert')).toHaveTextContent('Something went wrong. Please try again.');
  });
});
