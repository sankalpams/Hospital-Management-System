import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ToastProvider } from '../components/ui/Toast';
import { AppointmentActions } from '../features/appointments/shared';

let permissions = [];
vi.mock('../auth/AuthProvider', () => ({
  useAuth: () => ({ hasPermission: (p) => !p || [].concat(p).some((x) => permissions.includes(x)) }),
}));

const appointment = (allowedActions) => ({
  id: 7, appointmentNo: 'APT-2026-000007', patientName: 'Test Patient', doctorName: 'Dr. Test', allowedActions,
});

function renderActions(a) {
  return render(
    <MemoryRouter>
      <QueryClientProvider client={new QueryClient()}>
        <ToastProvider>
          <AppointmentActions appointment={a} />
        </ToastProvider>
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

describe('AppointmentActions', () => {
  it('shows only actions that the server allows AND the user is permitted to perform', () => {
    permissions = ['appointment:update', 'appointment:checkin'];   // receptionist
    renderActions(appointment(['checkIn', 'cancel', 'reschedule']));
    expect(screen.getByRole('button', { name: 'Check in' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Cancel' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /start consultation/i })).not.toBeInTheDocument();
  });

  it('gives doctors consultation actions but not front-desk ones', () => {
    permissions = ['appointment:consult'];
    renderActions(appointment(['start', 'cancel']));
    expect(screen.getByRole('button', { name: /start consultation/i })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Cancel' })).not.toBeInTheDocument();
  });

  it('renders nothing when no action is both allowed and permitted', () => {
    permissions = ['appointment:read'];
    renderActions(appointment(['checkIn']));
    expect(screen.queryAllByRole('button')).toHaveLength(0);
  });

  it('requires a reason before cancelling', async () => {
    permissions = ['appointment:update'];
    renderActions(appointment(['cancel']));
    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.click(screen.getByRole('button', { name: 'Cancel appointment' }));
    expect(dialog).toHaveTextContent('Please give a reason');
  });
});
