export type Permission = string;
export type Role = 'ADMIN' | 'DOCTOR' | 'NURSE' | 'RECEPTIONIST' | 'LAB_STAFF' | 'PHARMACIST' | 'ACCOUNTANT';

export interface User {
  id: string;
  username: string;
  email: string;
  firstName: string;
  lastName: string;
  phone?: string;
  role: Role;
  active: boolean;
  forcePasswordChange: boolean;
  lastLoginAt?: string;
  createdAt: string;
  version: number;
}

export interface Session {
  accessToken: string;
  refreshToken: string;
  forcePasswordChange: boolean;
  user: User;
}

export interface ApiError {
  timestamp: string;
  status: number;
  code: string;
  message: string;
  fieldErrors?: { field: string; message: string }[];
  path: string;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface Department {
  id: number;
  code: string;
  name: string;
  description?: string;
  phone?: string;
  location?: string;
  active: boolean;
}

export interface Doctor {
  id: string;
  userId: string;
  doctorName: string;
  email: string;
  phone?: string;
  departmentId: number;
  departmentName: string;
  specialization: string;
  qualification: string;
  slmcRegNumber: string;
  consultationFee: number;
  active: boolean;
}

export interface Employee {
  id: string;
  userId?: string;
  employeeName?: string;
  username?: string;
  email?: string;
  departmentId?: number;
  departmentName?: string;
  employeeNumber: string;
  designation: string;
  contactPhone?: string;
  active: boolean;
}

export type AllergySeverity = 'MILD' | 'MODERATE' | 'SEVERE';
export type DocumentCategory = 'LAB_REPORT' | 'SCAN' | 'REFERRAL' | 'OTHER';

export interface PatientAllergy {
  id: string;
  allergen: string;
  reaction: string;
  severity: AllergySeverity;
  notes?: string;
}

export interface PatientDocument {
  id: string;
  fileName: string;
  contentType: string;
  fileSize: number;
  category: DocumentCategory;
  notes?: string;
  createdAt: string;
}

export interface Consent {
  id: string;
  purpose: string;
  givenDate: string;
  withdrawnDate?: string;
  witnessName?: string;
  notes?: string;
}

export interface PatientSummary {
  id: string;
  mrn: string;
  nic?: string;
  firstName: string;
  lastName: string;
  fullName: string;
  dateOfBirth: string;
  age: number;
  sex: string;
  phone?: string;
  district: string;
  bloodGroup?: string;
  active: boolean;
}

export interface Patient extends PatientSummary {
  ageSex: string;
  email?: string;
  addressLine1?: string;
  addressLine2?: string;
  city?: string;
  guardianName?: string;
  guardianRelationship?: string;
  guardianPhone?: string;
  chronicConditions?: string;
  insuranceProvider?: string;
  insurancePolicyNumber?: string;
  mergedIntoId?: string;
  createdAt: string;
  version: number;
  allergies: PatientAllergy[];
  documents: PatientDocument[];
  consents: Consent[];
}

export interface DuplicateMatch {
  id: string;
  mrn: string;
  fullName: string;
  nic?: string;
  dateOfBirth: string;
  phone?: string;
  reason: string;
}
