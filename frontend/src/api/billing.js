import { api } from './client';
import { cleanParams } from './admin';

const data = (r) => r.data;

export const billingApi = {
  unbilled: (patientId) => api.get(`/patients/${patientId}/unbilled-charges`).then(data),
  services: () => api.get('/services').then(data),
  addCharge: (body) => api.post('/charges', body).then(data),
  invoices: (params) => api.get('/invoices', { params: cleanParams(params) }).then(data),
  invoice: (id) => api.get(`/invoices/${id}`).then(data),
  create: (body) => api.post('/invoices', body).then(data),
  issue: (id) => api.post(`/invoices/${id}/issue`).then(data),
  void: (id, reason) => api.post(`/invoices/${id}/void`, { reason }).then(data),
  pay: (id, body) => api.post(`/invoices/${id}/payments`, body).then(data),
  refund: (paymentId, body) => api.post(`/payments/${paymentId}/refund`, body).then(data),
};

export const PAYMENT_METHODS = [
  { value: 'CASH', label: 'Cash' },
  { value: 'CARD', label: 'Card' },
  { value: 'BANK_TRANSFER', label: 'Bank transfer' },
  { value: 'CHEQUE', label: 'Cheque' },
];
