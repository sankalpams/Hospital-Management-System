import { NavLink } from 'react-router-dom';
import { X } from 'lucide-react';
import clsx from 'clsx';
import { Logo } from '../ui/Logo';
import { useAuth } from '../../auth/AuthProvider';
import { visibleGroups } from '../../config/navigation';

/** Permission-filtered navigation. Fixed on desktop, slide-in drawer below 1024px. */
export function Sidebar({ open, onClose }) {
  const { hasPermission } = useAuth();
  const groups = visibleGroups(hasPermission);

  return (
    <>
      {open && (
        <div className="fixed inset-0 z-30 bg-sidebar opacity-50 lg:hidden" onClick={onClose} aria-hidden="true" />
      )}
      <aside
        id="app-sidebar"
        className={clsx(
          'fixed inset-y-0 left-0 z-40 flex w-sidebar flex-col bg-sidebar text-sidebar-foreground',
          'transition-transform duration-normal lg:translate-x-0',
          open ? 'translate-x-0' : '-translate-x-full',
        )}
      >
        <div className="flex h-topbar items-center justify-between px-5">
          <Logo inverted />
          <button
            type="button"
            onClick={onClose}
            className="rounded-md p-2 text-sidebar-foreground hover:text-white lg:hidden"
            aria-label="Close navigation"
          >
            <X className="h-5 w-5" aria-hidden="true" />
          </button>
        </div>

        <nav aria-label="Main navigation" className="sidebar-scroll flex-1 overflow-y-auto px-3 pb-6 pt-2">
          {groups.map((group) => (
            <div key={group.label} className="mb-5">
              <p className="px-3 pb-2 text-[11px] font-semibold uppercase tracking-wider text-sidebar-foreground">
                {group.label}
              </p>
              <ul className="flex flex-col gap-0.5">
                {group.items.map(({ to, label, icon: Icon, end }) => (
                  <li key={to}>
                    <NavLink
                      to={to}
                      end={end}
                      onClick={onClose}
                      className={({ isActive }) =>
                        clsx(
                          'flex h-10 items-center gap-3 rounded-md px-3 text-sm font-medium transition-colors duration-fast',
                          isActive ? 'bg-sidebar-active text-white' : 'text-sidebar-foreground hover:bg-white/10 hover:text-white',
                        )
                      }
                    >
                      <Icon className="h-[18px] w-[18px] shrink-0" aria-hidden="true" />
                      {label}
                    </NavLink>
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </nav>
      </aside>
    </>
  );
}
