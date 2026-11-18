import { api } from './client';
import { cleanParams } from './admin';

const data = (r) => r.data;

export const doctorsApi = {
  list: (params) => api.get('/doctors', { params: cleanParams(params) }).then(data),
  options: (departmentId) => api.get('/doctors/options', { params: cleanParams({ departmentId }) }).then(data),
  get: (id) => api.get(`/doctors/${id}`).then(data),
  create: (body) => api.post('/doctors', body).then(data),
  update: (id, body) => api.put(`/doctors/${id}`, body).then(data),
  schedules: (id) => api.get(`/doctors/${id}/schedules`).then(data),
  addSchedule: (id, body) => api.post(`/doctors/${id}/schedules`, body).then(data),
  updateSchedule: (id, scheduleId, body) => api.put(`/doctors/${id}/schedules/${scheduleId}`, body).then(data),
  exceptions: (id) => api.get(`/doctors/${id}/exceptions`).then(data),
  addException: (id, body) => api.post(`/doctors/${id}/exceptions`, body).then(data),
  removeException: (id, exceptionId) => api.delete(`/doctors/${id}/exceptions/${exceptionId}`),
  availability: (id, date) => api.get(`/doctors/${id}/availability`, { params: { date } }).then(data),
};

export const appointmentsApi = {
  search: (params) => api.get('/appointments', { params: cleanParams(params) }).then(data),
  calendar: (params) => api.get('/appointments/calendar', { params: cleanParams(params) }).then(data),
  get: (id) => api.get(`/appointments/${id}`).then(data),
  book: (body) => api.post('/appointments', body).then(data),
  walkIn: (body) => api.post('/appointments/walk-in', body).then(data),
  confirm: (id) => api.post(`/appointments/${id}/confirm`).then(data),
  checkIn: (id) => api.post(`/appointments/${id}/check-in`).then(data),
  start: (id) => api.post(`/appointments/${id}/start`).then(data),
  complete: (id) => api.post(`/appointments/${id}/complete`).then(data),
  noShow: (id) => api.post(`/appointments/${id}/no-show`).then(data),
  cancel: (id, reason) => api.post(`/appointments/${id}/cancel`, { reason }).then(data),
  reschedule: (id, body) => api.post(`/appointments/${id}/reschedule`, body).then(data),
};

export const WEEKDAYS = [
  { value: 1, label: 'Monday', short: 'Mon' },
  { value: 2, label: 'Tuesday', short: 'Tue' },
  { value: 3, label: 'Wednesday', short: 'Wed' },
  { value: 4, label: 'Thursday', short: 'Thu' },
  { value: 5, label: 'Friday', short: 'Fri' },
  { value: 6, label: 'Saturday', short: 'Sat' },
  { value: 7, label: 'Sunday', short: 'Sun' },
];

export const EMPLOYMENT_TYPES = [
  { value: 'PERMANENT', label: 'Permanent' },
  { value: 'CONTRACT', label: 'Contract' },
  { value: 'VISITING', label: 'Visiting consultant' },
  { value: 'TRAINEE', label: 'Trainee' },
];

export const EMPLOYEE_STATUSES = [
  { value: 'ACTIVE', label: 'Active' },
  { value: 'ON_LEAVE', label: 'On leave' },
  { value: 'SUSPENDED', label: 'Suspended' },
  { value: 'RESIGNED', label: 'Resigned' },
  { value: 'TERMINATED', label: 'Terminated' },
];

/** Which permission each appointment action needs (the server enforces the same). */
export const ACTION_PERMISSION = {
  confirm: 'appointment:update',
  reschedule: 'appointment:update',
  cancel: 'appointment:update',
  noShow: 'appointment:update',
  checkIn: 'appointment:checkin',
  start: 'appointment:consult',
  complete: 'appointment:consult',
};

export const ACTION_LABEL = {
  confirm: 'Confirm', checkIn: 'Check in', start: 'Start consultation', complete: 'Complete',
  noShow: 'Mark no-show', cancel: 'Cancel', reschedule: 'Reschedule',
};

export const emrApi = {
  record: (patientId, reason) => api.get(`/patients/${patientId}/emr`, { params: cleanParams({ reason }) }).then(data),
  addDiagnosis: (encounterId, body) => api.post(`/encounters/${encounterId}/diagnoses`, body).then(data),
  addNote: (encounterId, body) => api.post(`/encounters/${encounterId}/notes`, body).then(data),
  addNursingNote: (encounterId, content) => api.post(`/encounters/${encounterId}/nursing-notes`, { content }).then(data),
  editNote: (id, content) => api.put(`/notes/${id}`, { content }).then(data),
  signNote: (id) => api.post(`/notes/${id}/sign`).then(data),
  addAddendum: (id, content) => api.post(`/notes/${id}/addenda`, { content }).then(data),
  recordVitals: (patientId, body) => api.post(`/patients/${patientId}/vitals`, body).then(data),
  addAllergy: (patientId, body) => api.post(`/patients/${patientId}/allergies`, body).then(data),
  resolveAllergy: (id) => api.post(`/allergies/${id}/resolve`).then(data),
  prescribe: (encounterId, body) => api.post(`/encounters/${encounterId}/prescriptions`, body).then(data),
  cancelPrescription: (id) => api.post(`/prescriptions/${id}/cancel`).then(data),
  medicineOptions: (q) => api.get('/medicines/options', { params: { q } }).then(data),
};
