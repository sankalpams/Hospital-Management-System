export const ROLES = [
  'ADMIN',
  'DOCTOR',
  'NURSE',
  'RECEPTIONIST',
  'LAB_STAFF',
  'PHARMACIST',
  'ACCOUNTANT',
] as const;

export type Role = (typeof ROLES)[number];
