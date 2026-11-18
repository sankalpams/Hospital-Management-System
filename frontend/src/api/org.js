import { api } from './client';
import { cleanParams } from './admin';

const data = (r) => r.data;

export const departmentsApi = {
  list: (params) => api.get('/departments', { params: cleanParams(params) }).then(data),
  options: () => api.get('/departments/options').then(data),
  get: (id) => api.get(`/departments/${id}`).then(data),
  create: (body) => api.post('/departments', body).then(data),
  update: (id, body) => api.put(`/departments/${id}`, body).then(data),
  activate: (id) => api.post(`/departments/${id}/activate`).then(data),
  deactivate: (id) => api.post(`/departments/${id}/deactivate`).then(data),
};

export const DEPARTMENT_TYPES = [
  { value: 'CLINICAL', label: 'Clinical' },
  { value: 'DIAGNOSTIC', label: 'Diagnostic' },
  { value: 'SUPPORT', label: 'Support' },
  { value: 'ADMINISTRATIVE', label: 'Administrative' },
];
