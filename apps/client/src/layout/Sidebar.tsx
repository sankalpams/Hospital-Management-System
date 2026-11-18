import { NavLink } from 'react-router-dom';
import { useAuth } from '../auth/useAuth';
import { cx } from '../components/ui/cx';
import { NAV_ITEMS } from './nav';

export function Sidebar({ onNavigate }: { onNavigate?: () => void }) {
  const { can } = useAuth();
  const items = NAV_ITEMS.filter((i) => !i.permission || can(i.permission));
  return (
    <nav aria-label="Main" className="flex flex-col gap-1 p-3">
      {items.map(({ label, path, icon: Icon }) => (
        <NavLink
          key={path}
          to={path}
          end={path === '/'}
          onClick={onNavigate}
          className={({ isActive }) =>
            cx(
              'flex min-h-11 items-center gap-3 rounded-control px-3 py-2 text-sm font-medium',
              isActive ? 'bg-primary-subtle text-primary-hover' : 'text-text hover:bg-bg',
            )
          }
        >
          <Icon />
          <span>{label}</span>
        </NavLink>
      ))}
    </nav>
  );
}
