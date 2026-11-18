import { PERMISSIONS, type Permission } from '@hms/shared';
import { AuditIcon, HomeIcon, SettingsIcon, UsersIcon, type IconComponent } from '../components/ui/icons';

export interface NavItem {
  label: string;
  path: string;
  icon: IconComponent;
  permission?: Permission;
}

// Later slices append their items here.
export const NAV_ITEMS: NavItem[] = [
  { label: 'Home', path: '/', icon: HomeIcon },
  { label: 'Users', path: '/admin/users', icon: UsersIcon, permission: PERMISSIONS.USER_MANAGE },
  { label: 'Settings', path: '/admin/settings', icon: SettingsIcon, permission: PERMISSIONS.SETTINGS_MANAGE },
  { label: 'Audit Log', path: '/admin/audit', icon: AuditIcon, permission: PERMISSIONS.AUDIT_VIEW },
];
