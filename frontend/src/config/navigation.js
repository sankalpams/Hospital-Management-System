import { BarChart3, BedDouble, Building2, CalendarCheck, IdCard, Receipt, CalendarDays, FlaskConical, Pill, LayoutDashboard, ScrollText, Stethoscope, Users, UsersRound } from 'lucide-react';

/**
 * Single source for sidebar items AND route permission checks, so a hidden menu item is also
 * a blocked route. Only modules that are actually implemented are listed (no placeholder links);
 * each phase adds its entries here (docs/06-implementation-plan.md).
 *
 * permission: string | string[] (any-of) | undefined (every signed-in user)
 */
export const NAV_GROUPS = [
  {
    label: 'Overview',
    items: [
      { to: '/', label: 'Dashboard', icon: LayoutDashboard, end: true },
      { to: '/reports', label: 'Reports', icon: BarChart3, permission: ['report:operational', 'report:financial', 'report:staff'] },
      { to: '/my-leave', label: 'My leave', icon: CalendarCheck },
    ],
  },
  {
    label: 'Patient care',
    items: [
      { to: '/patients', label: 'Patients', icon: Users, permission: 'patient:read' },
      { to: '/appointments', label: 'Appointments', icon: CalendarDays, permission: 'appointment:read' },
    ],
  },
  {
    label: 'Clinical services',
    items: [
      { to: '/inpatients', label: 'Inpatients', icon: BedDouble, permission: ['admission:read', 'ward:manage'] },
      { to: '/lab', label: 'Laboratory', icon: FlaskConical, permission: 'lab:request:read' },
      { to: '/pharmacy', label: 'Pharmacy', icon: Pill, permission: ['prescription:read', 'medicine:read'] },
    ],
  },
  {
    label: 'Finance',
    items: [{ to: '/billing', label: 'Billing', icon: Receipt, permission: 'billing:read' }],
  },
  {
    label: 'Organisation',
    items: [
      { to: '/doctors', label: 'Doctors', icon: Stethoscope, permission: 'doctor:read' },
      { to: '/staff', label: 'Staff', icon: IdCard, permission: ['employee:read', 'attendance:manage', 'leave:approve'] },
      { to: '/departments', label: 'Departments', icon: Building2, permission: 'department:manage' },
    ],
  },
  {
    label: 'Administration',
    items: [
      { to: '/users', label: 'Users', icon: UsersRound, permission: 'user:read' },
      { to: '/audit', label: 'Audit log', icon: ScrollText, permission: 'audit:read' },
    ],
  },
];

/** Permission required for a top-level route path, looked up from NAV_GROUPS. */
export function permissionFor(path) {
  return NAV_GROUPS.flatMap((g) => g.items).find((i) => i.to === path)?.permission;
}

export function visibleGroups(hasPermission) {
  return NAV_GROUPS.map((g) => ({ ...g, items: g.items.filter((i) => hasPermission(i.permission)) }))
    .filter((g) => g.items.length > 0);
}
