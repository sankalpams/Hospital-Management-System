import { api } from './client';

/** Thin wrappers over /api/v1/auth. No business logic: the server owns every rule. */
export const authApi = {
  csrf: () => api.get('/auth/csrf'),
  me: () => api.get('/auth/me').then((r) => r.data),
  login: (username, password) => api.post('/auth/login', { username, password }).then((r) => r.data),
  logout: () => api.post('/auth/logout'),
  changePassword: (currentPassword, newPassword) =>
    api.post('/auth/change-password', { currentPassword, newPassword }).then((r) => r.data),
};
