import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { PatientProfilePage } from './PatientProfilePage';
import * as useAuthModule from '../hooks/use-auth';
import * as clientModule from '../api/client';

vi.mock('../hooks/use-auth');
vi.mock('../api/client');

describe('Phase 2 Patients UI & Permissions', () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    vi.clearAllMocks();
    queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
  });

  const mockAdminUser = {
    id: 'user-1',
    username: 'admin',
    email: 'admin@hms.local',
    firstName: 'Amara',
    lastName: 'Perera',
    role: 'ADMIN' as const,
    active: true,
    forcePasswordChange: false,
    createdAt: '2026-10-31T00:00:00Z',
    version: 0,
  };

  const mockReceptionUser = {
    ...mockAdminUser,
    id: 'user-2',
    username: 'reception',
    role: 'RECEPTIONIST' as const,
  };

  const mockPatient = {
    id: 'pat-123',
    mrn: 'MRN-2026-000001',
    nic: '199012345678',
    firstName: 'Sunil',
    lastName: 'Perera',
    fullName: 'Sunil Perera',
    dateOfBirth: '1990-05-12',
    age: 36,
    ageSex: '36y / MALE',
    sex: 'MALE',
    phone: '+94771234567',
    district: 'Colombo',
    bloodGroup: 'O+',
    active: true,
    allergies: [
      { id: 'all-1', allergen: 'Penicillin', reaction: 'Anaphylaxis', severity: 'SEVERE' },
    ],
    documents: [],
    consents: [],
    createdAt: '2026-10-31T00:00:00Z',
    version: 0,
  };

  it('Doctor/Admin sees clinical tabs on patient profile', async () => {
    vi.spyOn(useAuthModule, 'useAuth').mockReturnValue({
      session: { accessToken: 'token', refreshToken: 'refresh', forcePasswordChange: false, user: mockAdminUser },
      user: mockAdminUser,
      login: vi.fn(),
      logout: vi.fn(),
      refreshProfile: vi.fn(),
      has: () => true,
    });

    vi.spyOn(clientModule.api, 'get').mockResolvedValue({
      data: mockPatient,
    });

    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/patients/pat-123']}>
          <Routes>
            <Route path="/patients/:id" element={<PatientProfilePage />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    );

    expect((await screen.findAllByText('Sunil Perera')).length).toBeGreaterThan(0);
    expect(screen.getByText('MRN MRN-2026-000001')).toBeInTheDocument();
    expect(screen.getByText(/Medical History/)).toBeInTheDocument();
    expect(screen.getByText('Lab & Diagnostics')).toBeInTheDocument();
    expect(screen.getByText('Prescriptions')).toBeInTheDocument();
  });

  it('Receptionist sees demographics but clinical tabs (Medical, Lab, Prescriptions) are strictly hidden', async () => {
    vi.spyOn(useAuthModule, 'useAuth').mockReturnValue({
      session: { accessToken: 'token', refreshToken: 'refresh', forcePasswordChange: false, user: mockReceptionUser },
      user: mockReceptionUser,
      login: vi.fn(),
      logout: vi.fn(),
      refreshProfile: vi.fn(),
      has: (perm: string) => perm === 'PATIENT_READ',
    });

    vi.spyOn(clientModule.api, 'get').mockResolvedValue({
      data: mockPatient,
    });

    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/patients/pat-123']}>
          <Routes>
            <Route path="/patients/:id" element={<PatientProfilePage />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    );

    expect((await screen.findAllByText('Sunil Perera')).length).toBeGreaterThan(0);
    expect(screen.getByText('Overview')).toBeInTheDocument();
    expect(screen.getByText('Documents')).toBeInTheDocument();
    expect(screen.getByText('Consents')).toBeInTheDocument();

    // Clinical tabs MUST be hidden for RECEPTIONIST
    expect(screen.queryByText(/Medical History & Allergies/)).not.toBeInTheDocument();
    expect(screen.queryByText('Lab & Diagnostics')).not.toBeInTheDocument();
    expect(screen.queryByText('Prescriptions')).not.toBeInTheDocument();
  });
});
