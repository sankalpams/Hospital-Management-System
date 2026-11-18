/** Display names for role codes (codes come from the backend; never used for authorisation). */
export const ROLE_LABELS = {
  SUPER_ADMIN: 'Super Admin',
  HOSPITAL_ADMIN: 'Hospital Administrator',
  DOCTOR: 'Doctor',
  NURSE: 'Nurse',
  RECEPTIONIST: 'Receptionist',
  LAB_STAFF: 'Laboratory Staff',
  PHARMACIST: 'Pharmacist',
  ACCOUNTANT: 'Accountant',
  HR_MANAGER: 'HR / Staff Manager',
};

export function roleLabel(code) {
  return ROLE_LABELS[code] ?? code;
}

export function initials(fullName) {
  return String(fullName ?? '')
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((w) => w[0].toUpperCase())
    .join('');
}
