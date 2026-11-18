import { api } from './client';
import { cleanParams } from './admin';

const data = (r) => r.data;

export const analyticsApi = {
  dashboard: () => api.get('/analytics/dashboard').then(data),
  report: (name, params) => api.get(`/analytics/${name}`, { params: cleanParams(params) }).then(data),
  /** Same-origin GET with the session cookie: a plain link downloads it. */
  exportUrl: (name, params) => {
    const q = new URLSearchParams(Object.entries(cleanParams(params)).filter(([, v]) => v !== '' && v != null)).toString();
    return `/api/v1/analytics/${name}/export.csv${q ? `?${q}` : ''}`;
  },
};

/** Reports, in menu order, with the permission each needs (the server enforces the same). */
export const REPORTS = [
  { id: 'appointments', label: 'Appointments', permission: 'report:operational' },
  { id: 'patients', label: 'Patients', permission: 'report:operational' },
  { id: 'doctors', label: 'Doctor workload', permission: 'report:operational' },
  { id: 'departments', label: 'Departments', permission: 'report:operational' },
  { id: 'inpatient', label: 'Inpatients', permission: 'report:operational' },
  { id: 'laboratory', label: 'Laboratory', permission: 'report:operational' },
  { id: 'pharmacy', label: 'Pharmacy', permission: 'report:operational' },
  { id: 'revenue', label: 'Revenue', permission: 'report:financial' },
  { id: 'staff', label: 'Staff', permission: 'report:staff' },
];
