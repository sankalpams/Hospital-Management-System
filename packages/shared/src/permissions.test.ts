import { describe, expect, it } from 'vitest';
import { ROLE_PERMISSIONS, can } from './permissions.js';
import { ROLES, type Role } from './roles.js';
import type { Permission } from './permissions.js';

describe('permissions', () => {
  it('matches the mandated assertions', () => {
    expect(can('ADMIN', 'user:manage')).toBe(true);
    expect(can('DOCTOR', 'user:manage')).toBe(false);
    expect(can('PHARMACIST', 'pharmacy:dispense')).toBe(true);
    expect(can('RECEPTIONIST', 'emr:view')).toBe(false);
    for (const r of ROLES) expect(can(r, 'staff:self')).toBe(true);
  });

  it('gives ADMIN management and view access but not clinical or dispensing actions (spec 6.2)', () => {
    for (const p of ['user:manage', 'emr:view', 'lab:view', 'pharmacy:view', 'billing:void', 'ipd:admit'] as const)
      expect(can('ADMIN', p)).toBe(true);
    for (const p of [
      'emr:write',
      'emr:vitals',
      'lab:order',
      'lab:collect',
      'lab:result',
      'lab:verify',
      'pharmacy:manage',
      'pharmacy:dispense',
      'schedule:manage_own',
    ] as const)
      expect(can('ADMIN', p)).toBe(false);
    for (const r of ROLES) expect(ROLE_PERMISSIONS[r]).toBeDefined();
  });

  // [permission, allowed role, denied role] per area of the spec 6.2 table
  const cases: [Permission, Role, Role][] = [
    ['settings:manage', 'ADMIN', 'ACCOUNTANT'],
    ['audit:view', 'ADMIN', 'DOCTOR'],
    ['patient:create', 'RECEPTIONIST', 'DOCTOR'],
    ['patient:update', 'RECEPTIONIST', 'NURSE'],
    ['patient_doc:upload', 'NURSE', 'PHARMACIST'],
    ['patient_doc:view', 'PHARMACIST', 'ACCOUNTANT'],
    ['doctor:manage', 'ADMIN', 'RECEPTIONIST'],
    ['schedule:manage_own', 'DOCTOR', 'NURSE'],
    ['appointment:manage', 'RECEPTIONIST', 'DOCTOR'],
    ['appointment:update_status', 'DOCTOR', 'ACCOUNTANT'],
    ['appointment:check_in', 'NURSE', 'LAB_STAFF'],
    ['emr:write', 'DOCTOR', 'NURSE'],
    ['emr:vitals', 'NURSE', 'DOCTOR'],
    ['emr:view', 'PHARMACIST', 'LAB_STAFF'],
    ['lab:order', 'DOCTOR', 'NURSE'],
    ['lab:collect', 'NURSE', 'DOCTOR'],
    ['lab:verify', 'LAB_STAFF', 'DOCTOR'],
    ['pharmacy:view', 'DOCTOR', 'NURSE'],
    ['pharmacy:manage', 'PHARMACIST', 'DOCTOR'],
    ['ipd:admit', 'RECEPTIONIST', 'NURSE'],
    ['ipd:discharge', 'DOCTOR', 'RECEPTIONIST'],
    ['ipd:transfer', 'NURSE', 'DOCTOR'],
    ['billing:invoice', 'RECEPTIONIST', 'DOCTOR'],
    ['billing:payment', 'ACCOUNTANT', 'LAB_STAFF'],
    ['billing:void', 'ACCOUNTANT', 'RECEPTIONIST'],
    ['staff:manage', 'ADMIN', 'ACCOUNTANT'],
    ['report:own', 'DOCTOR', 'NURSE'],
    ['report:appointment', 'RECEPTIONIST', 'LAB_STAFF'],
    ['report:patient', 'RECEPTIONIST', 'DOCTOR'],
    ['report:lab', 'LAB_STAFF', 'PHARMACIST'],
    ['report:pharmacy', 'PHARMACIST', 'LAB_STAFF'],
    ['report:revenue', 'ACCOUNTANT', 'RECEPTIONIST'],
    ['report:all', 'ADMIN', 'ACCOUNTANT'],
  ];

  it.each(cases)('%s allowed for %s, denied for %s', (perm, allowed, denied) => {
    expect(can(allowed, perm)).toBe(true);
    expect(can(denied, perm)).toBe(false);
  });
});
