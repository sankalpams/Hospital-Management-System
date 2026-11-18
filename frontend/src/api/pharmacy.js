import { api } from './client';
import { cleanParams } from './admin';

const data = (r) => r.data;

export const pharmacyApi = {
  medicines: (params) => api.get('/medicines', { params: cleanParams(params) }).then(data),
  createMedicine: (body) => api.post('/medicines', body).then(data),
  updateMedicine: (id, body) => api.put(`/medicines/${id}`, body).then(data),
  batches: (id) => api.get(`/medicines/${id}/batches`).then(data),
  categories: () => api.get('/medicine-categories').then(data),
  addCategory: (body) => api.post('/medicine-categories', body).then(data),
  suppliers: () => api.get('/suppliers').then(data),
  saveSupplier: (id, body) => (id ? api.put(`/suppliers/${id}`, body) : api.post('/suppliers', body)).then(data),
  receive: (body) => api.post('/stock/receipts', body).then(data),
  adjust: (body) => api.post('/stock/adjustments', body).then(data),
  writeOffExpired: () => api.post('/stock/write-off-expired').then(data),
  transactions: (params) => api.get('/stock/transactions', { params: cleanParams(params) }).then(data),
  alerts: () => api.get('/pharmacy/alerts').then(data),
  queue: (params) => api.get('/pharmacy/queue', { params: cleanParams(params) }).then(data),
  dispense: (id, body) => api.post(`/prescriptions/${id}/dispense`, body).then(data),
};

export const DOSAGE_FORMS = ['TABLET', 'CAPSULE', 'SYRUP', 'SUSPENSION', 'INJECTION', 'INFUSION', 'CREAM', 'OINTMENT', 'DROPS', 'INHALER', 'SUPPOSITORY', 'POWDER', 'OTHER'];
