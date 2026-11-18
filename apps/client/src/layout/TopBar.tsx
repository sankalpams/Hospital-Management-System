import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/useAuth';
import { ChevronDownIcon, MenuIcon } from '../components/ui/icons';
import { usePublicSettings } from '../lib/usePublicSettings';

const ROLE_LABELS: Record<string, string> = {
  ADMIN: 'Administrator',
  DOCTOR: 'Doctor',
  NURSE: 'Nurse',
  RECEPTIONIST: 'Receptionist',
  LAB_STAFF: 'Lab staff',
  PHARMACIST: 'Pharmacist',
  ACCOUNTANT: 'Accountant',
};
export const roleLabel = (role: string) => ROLE_LABELS[role] ?? role;

export function TopBar({ onOpenMenu, menuOpen }: { onOpenMenu: () => void; menuOpen: boolean }) {
  const { user, logout, expiresAt } = useAuth();
  const { data: settings } = usePublicSettings();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const wrapRef = useRef<HTMLDivElement>(null);
  const buttonRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (!open) return;
    const onDoc = (e: MouseEvent) => {
      if (!wrapRef.current?.contains(e.target as Node)) setOpen(false);
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        setOpen(false);
        buttonRef.current?.focus();
      }
    };
    document.addEventListener('mousedown', onDoc);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onDoc);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  const onLogout = async () => {
    setOpen(false);
    await logout();
    navigate('/login', { replace: true });
  };

  return (
    <header className="flex h-14 items-center gap-3 border-b border-border bg-surface px-4">
      <button
        type="button"
        onClick={onOpenMenu}
        aria-label="Open navigation menu"
        aria-expanded={menuOpen}
        className="inline-flex h-11 w-11 items-center justify-center rounded-control text-text hover:bg-bg lg:hidden"
      >
        <MenuIcon />
      </button>
      <p className="truncate text-base font-semibold text-text">{settings?.hospitalName ?? 'HMS'}</p>
      <div className="ml-auto flex items-center gap-4">
        {expiresAt && (
          <p className="hidden text-xs text-muted md:block">
            Session ends{' '}
            {expiresAt.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
          </p>
        )}
        <div ref={wrapRef} className="relative">
          <button
            ref={buttonRef}
            type="button"
            aria-haspopup="menu"
            aria-expanded={open}
            onClick={() => setOpen((o) => !o)}
            className="flex min-h-11 items-center gap-2 rounded-control px-2 text-left hover:bg-bg"
          >
            <span className="leading-tight">
              <span className="block text-sm font-medium text-text">{user?.fullName}</span>
              <span className="block text-xs text-muted">{user ? roleLabel(user.role) : ''}</span>
            </span>
            <ChevronDownIcon className="h-4 w-4 text-muted" />
          </button>
          {open && (
            <div
              role="menu"
              aria-label="User menu"
              className="absolute right-0 z-40 mt-1 w-48 rounded-card border border-border bg-surface p-1 shadow-overlay"
            >
              <button
                type="button"
                role="menuitem"
                autoFocus
                // TODO(Task 9): route exists once the change-password screen is built.
                onClick={() => {
                  setOpen(false);
                  navigate('/change-password');
                }}
                className="flex min-h-11 w-full items-center rounded-control px-3 text-sm text-text hover:bg-bg"
              >
                Change password
              </button>
              <button
                type="button"
                role="menuitem"
                onClick={onLogout}
                className="flex min-h-11 w-full items-center rounded-control px-3 text-sm text-text hover:bg-bg"
              >
                Log out
              </button>
            </div>
          )}
        </div>
      </div>
    </header>
  );
}
