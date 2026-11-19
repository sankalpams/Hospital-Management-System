import { api } from './client';

const data = (r) => r.data;

/** Strips empty values so they are not sent as filters. */
export function cleanParams(params) {
  return Object.fromEntries(Object.entries(params).filter(([, v]) => v !== '' && v !== undefined && v !== null));
}

export const usersApi = {
  list: (params) => api.get('/users', { params: cleanParams(params) }).then(data),
  demoStaff: () => api.get('/users/demo-staff').then(data),
  get: (id) => api.get(`/users/${id}`).then(data),
  create: (body) => api.post('/users', body).then(data),
  update: (id, body) => api.put(`/users/${id}`, body).then(data),
  activate: (id) => api.post(`/users/${id}/activate`).then(data),
  deactivate: (id) => api.post(`/users/${id}/deactivate`).then(data),
  resetPassword: (id) => api.post(`/users/${id}/reset-password`).then(data),
  roles: () => api.get('/roles').then(data),
};

export const auditApi = {
  logs: (params) => api.get('/audit-logs', { params: cleanParams(params) }).then(data),
  loginActivity: (params) => api.get('/login-activity', { params: cleanParams(params) }).then(data),
};
