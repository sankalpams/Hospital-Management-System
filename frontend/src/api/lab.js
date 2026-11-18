import { api } from './client';
import { cleanParams } from './admin';

const data = (r) => r.data;

export const labApi = {
  tests: () => api.get('/lab-tests').then(data),
  order: (encounterId, body) => api.post(`/encounters/${encounterId}/lab-requests`, body).then(data),
  search: (params) => api.get('/lab-requests', { params: cleanParams(params) }).then(data),
  get: (id) => api.get(`/lab-requests/${id}`).then(data),
  collect: (itemId) => api.post(`/lab-items/${itemId}/collect-sample`).then(data),
  start: (itemId) => api.post(`/lab-items/${itemId}/start-processing`).then(data),
  result: (itemId, body) => api.post(`/lab-items/${itemId}/result`, body).then(data),
  review: (itemId) => api.post(`/lab-items/${itemId}/review`).then(data),
  cancel: (itemId, reason) => api.post(`/lab-items/${itemId}/cancel`, { reason }).then(data),
};
