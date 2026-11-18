import { api } from './client';
import { cleanParams } from './admin';

const data = (r) => r.data;

export const inpatientApi = {
  wards: () => api.get('/wards').then(data),
  createWard: (body) => api.post('/wards', body).then(data),
  addRoom: (wardId, body) => api.post(`/wards/${wardId}/rooms`, body).then(data),
  setBedStatus: (bedId, status) => api.post(`/beds/${bedId}/status`, { status }),
  admissions: (params) => api.get('/admissions', { params: cleanParams(params) }).then(data),
  request: (body) => api.post('/admissions', body).then(data),
  admit: (id, bedId) => api.post(`/admissions/${id}/admit`, { bedId }).then(data),
  transfer: (id, bedId) => api.post(`/admissions/${id}/transfer`, { bedId }).then(data),
  discharge: (id, body) => api.post(`/admissions/${id}/discharge`, body).then(data),
  cancel: (id, reason) => api.post(`/admissions/${id}/cancel`, { reason }).then(data),
};

const opts = (codes) => codes.map((value) => ({ value, label: value.charAt(0) + value.slice(1).toLowerCase().replace(/_/g, ' ') }));
export const WARD_TYPES = opts(['GENERAL', 'ICU', 'PAEDIATRIC', 'MATERNITY', 'SURGICAL', 'PRIVATE']);
export const GENDER_POLICIES = opts(['MIXED', 'MALE', 'FEMALE']);
export const ROOM_TYPES = opts(['SHARED', 'PRIVATE', 'ISOLATION', 'ICU']);
export const ADMISSION_TYPES = opts(['EMERGENCY', 'ELECTIVE', 'TRANSFER_IN']);
export const DISCHARGE_TYPES = opts(['ROUTINE', 'AGAINST_ADVICE', 'TRANSFERRED', 'DECEASED']);
