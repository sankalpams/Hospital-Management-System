import { api } from './client';
import { cleanParams } from './admin';

const data = (r) => r.data;

export const staffApi = {
  employees: (params) => api.get('/employees', { params: cleanParams(params) }).then(data),
  employee: (id) => api.get(`/employees/${id}`).then(data),
  save: (id, body) => (id ? api.put(`/employees/${id}`, body) : api.post('/employees', body)).then(data),
  history: (id, from, to) => api.get(`/employees/${id}/attendance`, { params: { from, to } }).then(data),
  balances: (id, year) => api.get(`/employees/${id}/leave-balances`, { params: { year } }).then(data),
  register: (date) => api.get('/attendance', { params: { date } }).then(data),
  saveRegister: (date, entries) => api.put('/attendance', { entries }, { params: { date } }).then(data),
  leaveTypes: () => api.get('/leave-types').then(data),
  leave: (params) => api.get('/leave-requests', { params: cleanParams(params) }).then(data),
  apply: (body) => api.post('/leave-requests', body).then(data),
  approve: (id, note) => api.post(`/leave-requests/${id}/approve`, { note }).then(data),
  reject: (id, note) => api.post(`/leave-requests/${id}/reject`, { note }).then(data),
  cancel: (id) => api.post(`/leave-requests/${id}/cancel`).then(data),
  myLeave: () => api.get('/me/leave').then(data),
};

export const STAFF_CATEGORIES = [
  { value: 'NURSE', label: 'Nursing' },
  { value: 'LAB', label: 'Laboratory' },
  { value: 'PHARMACY', label: 'Pharmacy' },
  { value: 'ADMIN', label: 'Administration' },
  { value: 'FINANCE', label: 'Finance' },
  { value: 'SUPPORT', label: 'Support services' },
  { value: 'DOCTOR', label: 'Medical (doctors)' },
];

export const ATTENDANCE_STATUSES = [
  { value: 'PRESENT', label: 'Present' },
  { value: 'HALF_DAY', label: 'Half day' },
  { value: 'ABSENT', label: 'Absent' },
  { value: 'ON_LEAVE', label: 'On leave' },
];
