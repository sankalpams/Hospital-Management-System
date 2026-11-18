import { api } from './client';
import { cleanParams } from './admin';

const data = (r) => r.data;

export const patientsApi = {
  search: (params) => api.get('/patients', { params: cleanParams(params) }).then(data),
  get: (id) => api.get(`/patients/${id}`).then(data),
  duplicateCheck: (body) => api.post('/patients/duplicate-check', body).then(data),
  register: (body) => api.post('/patients', body).then(data),
  update: (id, body) => api.put(`/patients/${id}`, body).then(data),
  changeStatus: (id, body) => api.post(`/patients/${id}/status`, body).then(data),
  documents: (id) => api.get(`/patients/${id}/documents`).then(data),
  upload: (id, { file, category, title }) => {
    const form = new FormData();
    form.append('file', file);
    form.append('category', category);
    form.append('title', title);
    return api.post(`/patients/${id}/documents`, form).then(data);
  },
  downloadUrl: (documentId) => `/api/v1/documents/${documentId}/download`,
};

export const GENDERS = [
  { value: 'FEMALE', label: 'Female' },
  { value: 'MALE', label: 'Male' },
  { value: 'OTHER', label: 'Other' },
];

export const BLOOD_GROUPS = ['A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-'].map((v) => ({ value: v, label: v }));

export const MARITAL = [
  { value: 'SINGLE', label: 'Single' },
  { value: 'MARRIED', label: 'Married' },
  { value: 'DIVORCED', label: 'Divorced' },
  { value: 'WIDOWED', label: 'Widowed' },
];

export const DISTRICTS = [
  'Ampara', 'Anuradhapura', 'Badulla', 'Batticaloa', 'Colombo', 'Galle', 'Gampaha', 'Hambantota', 'Jaffna',
  'Kalutara', 'Kandy', 'Kegalle', 'Kilinochchi', 'Kurunegala', 'Mannar', 'Matale', 'Matara', 'Monaragala',
  'Mullaitivu', 'Nuwara Eliya', 'Polonnaruwa', 'Puttalam', 'Ratnapura', 'Trincomalee', 'Vavuniya',
].map((d) => ({ value: d, label: d }));

export const DOCUMENT_CATEGORIES = [
  { value: 'REFERRAL', label: 'Referral letter' },
  { value: 'LAB_EXTERNAL', label: 'External lab report' },
  { value: 'IMAGING', label: 'Imaging' },
  { value: 'DISCHARGE', label: 'Discharge summary' },
  { value: 'CONSENT', label: 'Consent form' },
  { value: 'ID_PROOF', label: 'Identity document' },
  { value: 'OTHER', label: 'Other' },
];

/** Same patterns the server validates with (PatientDtos); used for instant feedback only. */
export const NIC_PATTERN = /^([0-9]{9}[VvXx]|[0-9]{12})$/;
export const PHONE_PATTERN = /^(0[0-9]{9}|\+[1-9][0-9]{7,14})$/;
