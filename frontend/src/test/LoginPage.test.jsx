import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import LoginPage from '../features/auth/LoginPage';

// A plain function (not a vi.fn spy) performs the call: Vitest's spy wrapper attaches its own
// handler to returned promises, which reports a rejected promise as unhandled even though the
// component catches it. loginMock only records arguments and chooses the outcome.
const loginMock = vi.fn();
let loginOutcome = () => Promise.resolve({});
vi.mock('../auth/AuthProvider', () => ({
  useAuth: () => ({
    login: (...args) => { loginMock(...args); return loginOutcome(...args); },
    notice: null,
  }),
}));

function renderLogin() {
  return render(
    <MemoryRouter initialEntries={['/login']}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/" element={<p>Dashboard reached</p>} />
        <Route path="/change-password" element={<p>Change password reached</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('LoginPage', () => {
  beforeEach(() => {
    loginMock.mockReset();
    loginOutcome = () => Promise.resolve({});
  });

  it('labels fields so assistive technology can find them', () => {
    renderLogin();
    expect(screen.getByLabelText(/username/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/^password/i)).toBeInTheDocument();
  });

  it('shows inline errors and does not call the API when fields are empty', async () => {
    renderLogin();
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }));
    expect(screen.getByText('Enter your username.')).toBeInTheDocument();
    expect(screen.getByText('Enter your password.')).toBeInTheDocument();
    expect(screen.getByLabelText(/username/i)).toHaveAttribute('aria-invalid', 'true');
    expect(loginMock).not.toHaveBeenCalled();
  });

  it('shows the server message on bad credentials and clears the password', async () => {
    loginOutcome = () => Promise.reject(Object.assign(new Error('Request failed with status code 401'), {
      response: { status: 401, data: { status: 401, code: 'BAD_CREDENTIALS', message: 'Incorrect username or password.' } },
    }));
    renderLogin();
    await userEvent.type(screen.getByLabelText(/username/i), 'nimal');
    await userEvent.type(screen.getByLabelText(/^password/i), 'wrong-pass-1');
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Incorrect username or password.');
    expect(screen.getByLabelText(/^password/i)).toHaveValue('');
  });

  it('sends users with a temporary password to the change-password page', async () => {
    loginOutcome = () => Promise.resolve({ username: 'nimal', mustChangePassword: true, permissions: [] });
    renderLogin();
    await userEvent.type(screen.getByLabelText(/username/i), ' nimal ');
    await userEvent.type(screen.getByLabelText(/^password/i), 'Temp-Pass-123');
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }));
    expect(await screen.findByText('Change password reached')).toBeInTheDocument();
    expect(loginMock).toHaveBeenCalledWith('nimal', 'Temp-Pass-123');
  });

  it('lets the user reveal the password with an accessible toggle', async () => {
    renderLogin();
    const input = screen.getByLabelText(/^password/i);
    expect(input).toHaveAttribute('type', 'password');
    await userEvent.click(screen.getByRole('button', { name: /show password/i }));
    expect(input).toHaveAttribute('type', 'text');
  });
});
