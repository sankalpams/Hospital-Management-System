import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { ChevronDown, KeyRound, LogOut, Menu } from 'lucide-react';
import { useAuth } from '../../auth/AuthProvider';
import { initials, roleLabel } from '../../lib/roles';
import { NotificationBell } from './NotificationBell';

export function Topbar({ onOpenSidebar }) {
  return (
    <header className="app-topbar sticky top-0 z-20 flex h-topbar items-center gap-3 border-b border-border bg-surface px-4 sm:px-6">
      <button
        type="button"
        onClick={onOpenSidebar}
        className="rounded-md p-2 text-foreground-muted hover:bg-surface-muted hover:text-foreground lg:hidden"
        aria-label="Open navigation"
        aria-controls="app-sidebar"
      >
        <Menu className="h-5 w-5" aria-hidden="true" />
      </button>
      <div className="flex-1" />
      <NotificationBell />
      <UserMenu />
    </header>
  );
}

function UserMenu() {
  const { user, logout } = useAuth();
  const [open, setOpen] = useState(false);
  const [signingOut, setSigningOut] = useState(false);
  const ref = useRef(null);

  useEffect(() => {
    if (!open) return undefined;
    const onDown = (e) => { if (ref.current && !ref.current.contains(e.target)) setOpen(false); };
    const onKey = (e) => { if (e.key === 'Escape') setOpen(false); };
    document.addEventListener('mousedown', onDown);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onDown);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  const primaryRole = user?.roles?.[0];

  return (
    <div className="relative" ref={ref}>
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        className="flex items-center gap-3 rounded-md py-1.5 pl-1.5 pr-2 hover:bg-surface-muted"
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label={`Account menu for ${user?.fullName ?? 'current user'}`}
      >
        <span className="flex h-9 w-9 items-center justify-center rounded-full bg-primary-soft text-sm font-semibold text-primary-soft-foreground">
          {initials(user?.fullName)}
        </span>
        <span className="hidden text-left sm:block">
          <span className="block text-sm font-medium leading-tight text-foreground">{user?.fullName}</span>
          <span className="block text-xs leading-tight text-foreground-subtle">{roleLabel(primaryRole)}</span>
        </span>
        <ChevronDown className="h-4 w-4 text-foreground-subtle" aria-hidden="true" />
      </button>

      {open && (
        <div role="menu" className="absolute right-0 mt-2 w-60 overflow-hidden rounded-lg border border-border bg-surface py-1 shadow-md">
          <div className="border-b border-border px-4 py-3">
            <p className="truncate text-sm font-medium">{user?.fullName}</p>
            <p className="truncate font-mono text-xs text-foreground-subtle">{user?.username}</p>
          </div>
          <Link
            role="menuitem"
            to="/change-password"
            onClick={() => setOpen(false)}
            className="flex items-center gap-2.5 px-4 py-2.5 text-sm text-foreground hover:bg-surface-muted"
          >
            <KeyRound className="h-4 w-4 text-foreground-subtle" aria-hidden="true" />
            Change password
          </Link>
          <button
            role="menuitem"
            type="button"
            disabled={signingOut}
            onClick={async () => { setSigningOut(true); await logout(); }}
            className="flex w-full items-center gap-2.5 px-4 py-2.5 text-left text-sm text-danger hover:bg-danger-soft disabled:opacity-50"
          >
            <LogOut className="h-4 w-4" aria-hidden="true" />
            {signingOut ? 'Signing out…' : 'Sign out'}
          </button>
        </div>
      )}
    </div>
  );
}
