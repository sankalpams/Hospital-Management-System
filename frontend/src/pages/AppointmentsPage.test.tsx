import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { HomePage } from './HomePage';
import { StatusBadge } from '../components/StatusBadge';
import * as useAuthModule from '../hooks/use-auth';
import * as clientModule from '../api/client';

vi.mock('../hooks/use-auth');
vi.mock('../api/client');

describe('Phase 3 Appointments & Queue UI', () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    vi.clearAllMocks();
    queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
  });

  const mockDoctorUser = {
    id: 'user-doc-1',
    username: 'dr.ananda',
    email: 'ananda@hms.local',
    firstName: 'Ananda',
    lastName: 'Silva',
    role: 'DOCTOR' as const,
    active: true,
    forcePasswordChange: false,
    createdAt: '2026-10-31T00:00:00Z',
    version: 0,
  };

  const mockReceptionistUser = {
    id: 'user-rec-1',
    username: 'reception',
    email: 'reception@hms.local',
    firstName: 'Kumari',
    lastName: 'Fernando',
    role: 'RECEPTIONIST' as const,
    active: true,
    forcePasswordChange: false,
    createdAt: '2026-10-31T00:00:00Z',
    version: 0,
  };

  it('renders receptionist home with Today Check-in and Walk-in options', async () => {
    vi.spyOn(useAuthModule, 'useAuth').mockReturnValue({
      session: {
        accessToken: 'token',
        refreshToken: 'refresh',
        forcePasswordChange: false,
        user: mockReceptionistUser,
      },
      user: mockReceptionistUser,
      has: (p: string) => ['APPOINTMENT_READ', 'APPOINTMENT_WRITE', 'PATIENT_READ'].includes(p),
      login: vi.fn(),
      logout: vi.fn(),
      refreshProfile: vi.fn(),
    });

    vi.spyOn(clientModule.api, 'get').mockResolvedValue({
      data: {
        content: [
          {
            id: 'apt-1',
            appointmentNumber: 'APT-2026-000001',
            tokenNumber: 1,
            patientName: 'Kusal Mendis',
            patientMrn: 'MRN-2026-000010',
            doctorName: 'Dr. Ananda Silva',
            departmentName: 'General Medicine',
            appointmentDate: '2026-10-31',
            startTime: '08:30:00',
            endTime: '08:50:00',
            status: 'SCHEDULED',
          },
        ],
        totalElements: 1,
        page: 0,
        size: 50,
      },
    });

    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter>
          <HomePage />
        </MemoryRouter>
      </QueryClientProvider>
    );

    expect(await screen.findByText(/Today's Clinic Check-In/i)).toBeInTheDocument();
    expect(screen.getByText(/Walk-In Registration/i)).toBeInTheDocument();
    expect(screen.getByText(/Book Appointment/i)).toBeInTheDocument();
  });

  it('renders doctor home with consultation queue and Start Consultation button', async () => {
    vi.spyOn(useAuthModule, 'useAuth').mockReturnValue({
      session: {
        accessToken: 'token',
        refreshToken: 'refresh',
        forcePasswordChange: false,
        user: mockDoctorUser,
      },
      user: mockDoctorUser,
      has: (p: string) => ['APPOINTMENT_READ', 'EMR_READ', 'EMR_WRITE'].includes(p),
      login: vi.fn(),
      logout: vi.fn(),
      refreshProfile: vi.fn(),
    });

    vi.spyOn(clientModule.api, 'get').mockImplementation((url: string) => {
      if (url === '/doctors/me') {
        return Promise.resolve({
          data: {
            id: 'doc-1',
            doctorName: 'Dr. Ananda Silva',
            departmentName: 'Cardiology',
          },
        });
      }
      if (url.includes('/appointments/queue/today')) {
        return Promise.resolve({
          data: [
            {
              id: 'apt-2',
              appointmentNumber: 'APT-2026-000002',
              tokenNumber: 1,
              patientName: 'Pathum Nissanka',
              patientMrn: 'MRN-2026-000020',
              doctorName: 'Dr. Ananda Silva',
              appointmentDate: '2026-10-31',
              startTime: '09:00:00',
              endTime: '09:20:00',
              status: 'CHECKED_IN',
              reason: 'Chest pain review',
            },
          ],
        });
      }
      return Promise.resolve({ data: {} });
    });

    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter>
          <HomePage />
        </MemoryRouter>
      </QueryClientProvider>
    );

    expect(await screen.findByText(/Clinical Queue Workspace/i)).toBeInTheDocument();
    expect(await screen.findByText('Pathum Nissanka')).toBeInTheDocument();
    expect(screen.getByText('Start Consultation')).toBeInTheDocument();
  });

  it('renders correct status badges for appointment lifecycle', () => {
    const { rerender } = render(<StatusBadge status="CONFIRMED" />);
    expect(screen.getByText('CONFIRMED')).toBeInTheDocument();

    rerender(<StatusBadge status="IN_CONSULTATION" />);
    expect(screen.getByText('IN CONSULTATION')).toBeInTheDocument();

    rerender(<StatusBadge status="CANCELLED" />);
    expect(screen.getByText('CANCELLED')).toBeInTheDocument();
  });
});
