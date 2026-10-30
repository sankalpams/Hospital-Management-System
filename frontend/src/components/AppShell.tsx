import {
  Bell,
  Building2,
  Briefcase,
  ChevronLeft,
  ChevronRight,
  LogOut,
  Menu,
  Moon,
  Search,
  ShieldCheck,
  Stethoscope,
  Sun,
  UserCheck,
  Users,
} from 'lucide-react';
import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useEffect, useRef, useState } from 'react';
import { useAuth } from '../hooks/use-auth';
import { cn } from '../lib/utils';
import { Button } from './ui/button';
import { IdleTimeout } from './IdleTimeout';
import { api } from '../api/client';
import type { PatientSummary } from '../types/api';

const items = [
  { to: '/', label: 'Home', icon: Menu },
  { to: '/patients', label: 'Patients', icon: UserCheck, permission: 'PATIENT_READ' },
  { to: '/departments', label: 'Departments', icon: Building2, permission: 'USER_READ' },
  { to: '/doctors', label: 'Doctors', icon: Stethoscope, permission: 'USER_READ' },
  { to: '/employees', label: 'Employees', icon: Briefcase, permission: 'USER_READ' },
  { to: '/users', label: 'Users', icon: Users, permission: 'USER_READ' },
  { to: '/audit-logs', label: 'Audit log', icon: ShieldCheck, permission: 'AUDIT_READ' },
  { to: '/roles', label: 'Roles & permissions', icon: ShieldCheck, permission: 'ROLE_READ' },
];

export function AppShell() {
  const { user, has, logout } = useAuth();
  const [collapsed, setCollapsed] = useState(false);
  const [dark, setDark] = useState(() => localStorage.getItem('hms.theme') === 'dark');
  const [searchQuery, setSearchQuery] = useState('');
  const [searchResults, setSearchResults] = useState<PatientSummary[]>([]);
  const [searchOpen, setSearchOpen] = useState(false);

  const searchInput = useRef<HTMLInputElement>(null);
  const searchDropdown = useRef<HTMLDivElement>(null);
  const navigate = useNavigate();

  useEffect(() => {
    document.documentElement.classList.toggle('dark', dark);
    localStorage.setItem('hms.theme', dark ? 'dark' : 'light');
  }, [dark]);

  // Global keyboard shortcuts: / for search, N for new record
  useEffect(() => {
    const listener = (event: KeyboardEvent) => {
      if (event.key === '/' && document.activeElement?.tagName !== 'INPUT' && document.activeElement?.tagName !== 'TEXTAREA') {
        event.preventDefault();
        searchInput.current?.focus();
      }
      if (event.key.toLowerCase() === 'n' && document.activeElement?.tagName !== 'INPUT' && document.activeElement?.tagName !== 'TEXTAREA') {
        if (location.pathname === '/users' && has('USER_MANAGE')) {
          navigate('/users?new=1');
        }
      }
    };
    window.addEventListener('keydown', listener);
    return () => window.removeEventListener('keydown', listener);
  }, [has, navigate]);

  // Quick search autocomplete
  useEffect(() => {
    if (searchQuery.trim().length >= 2) {
      const timer = setTimeout(async () => {
        try {
          const { data } = await api.get<PatientSummary[]>('/patients/quick-search', {
            params: { q: searchQuery.trim(), limit: 6 },
          });
          setSearchResults(data);
          setSearchOpen(true);
        } catch {
          setSearchResults([]);
        }
      }, 250);
      return () => clearTimeout(timer);
    } else {
      setSearchResults([]);
      setSearchOpen(false);
    }
  }, [searchQuery]);

  // Dismiss dropdown on outside click
  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (
        searchDropdown.current &&
        !searchDropdown.current.contains(e.target as Node) &&
        searchInput.current &&
        !searchInput.current.contains(e.target as Node)
      ) {
        setSearchOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  return (
    <div className="min-h-screen bg-background">
      <aside
        className={cn(
          'fixed inset-y-0 left-0 z-30 flex flex-col border-r bg-surface transition-[width]',
          collapsed ? 'w-16' : 'w-64'
        )}
      >
        <div className="flex h-16 items-center gap-2 border-b px-4">
          <div className="grid h-8 w-8 place-items-center rounded bg-primary text-xs font-bold text-primary-foreground">
            H
          </div>
          {!collapsed && <span className="font-semibold">HMS</span>}
        </div>

        <nav className="flex-1 space-y-1 p-2 overflow-y-auto">
          {items
            .filter((item) => !item.permission || has(item.permission))
            .map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.to === '/'}
                className={({ isActive }) =>
                  cn(
                    'flex items-center gap-3 rounded px-3 py-2 text-sm text-muted-foreground hover:bg-muted hover:text-foreground',
                    isActive && 'bg-muted font-medium text-foreground'
                  )
                }
                title={collapsed ? item.label : undefined}
              >
                <item.icon className="h-4 w-4 shrink-0" />
                {!collapsed && item.label}
              </NavLink>
            ))}
        </nav>

        <button
          onClick={() => setCollapsed(!collapsed)}
          className="flex h-11 items-center justify-center border-t text-muted-foreground hover:bg-muted"
          aria-label="Toggle sidebar"
        >
          {collapsed ? <ChevronRight className="h-4 w-4" /> : <ChevronLeft className="h-4 w-4" />}
        </button>
      </aside>

      <div className={cn('transition-[margin]', collapsed ? 'ml-16' : 'ml-64')}>
        <header className="sticky top-0 z-20 flex h-16 items-center gap-3 border-b bg-background/95 px-5 backdrop-blur">
          <div className="relative max-w-md flex-1">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <input
              ref={searchInput}
              aria-label="Global search"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              onFocus={() => searchResults.length > 0 && setSearchOpen(true)}
              placeholder="Search patients, MRN, NIC…  /"
              className="h-9 w-full rounded-md border bg-surface pl-9 pr-3 text-sm"
            />

            {/* Quick search autocomplete results popup */}
            {searchOpen && (
              <div
                ref={searchDropdown}
                className="absolute left-0 right-0 top-11 z-50 rounded-md border bg-surface shadow-panel overflow-hidden"
              >
                <div className="bg-muted/50 px-3 py-1.5 text-[11px] font-semibold uppercase tracking-wider text-muted-foreground">
                  Matching Patient Records
                </div>
                {searchResults.length === 0 ? (
                  <div className="p-3 text-xs text-muted-foreground">No matching patient records found.</div>
                ) : (
                  <div className="divide-y max-h-64 overflow-y-auto">
                    {searchResults.map((p) => (
                      <button
                        key={p.id}
                        type="button"
                        onClick={() => {
                          setSearchOpen(false);
                          setSearchQuery('');
                          navigate(`/patients/${p.id}`);
                        }}
                        className="w-full text-left p-2.5 hover:bg-muted/50 flex items-center justify-between transition-colors"
                      >
                        <div>
                          <div className="flex items-center gap-2">
                            <span className="font-mono text-xs font-bold text-primary">{p.mrn}</span>
                            <span className="font-medium text-sm">{p.fullName}</span>
                          </div>
                          <p className="text-xs text-muted-foreground mt-0.5">
                            NIC: {p.nic ?? 'N/A'} • {p.age}y / {p.sex} • {p.district}
                          </p>
                        </div>
                        <span className="text-xs font-mono text-muted-foreground">{p.phone ?? ''}</span>
                      </button>
                    ))}
                  </div>
                )}
              </div>
            )}
          </div>

          <Button variant="ghost" size="sm" onClick={() => setDark(!dark)} aria-label="Toggle theme">
            {dark ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
          </Button>

          <Button variant="ghost" size="sm" aria-label="Notifications">
            <Bell className="h-4 w-4" />
          </Button>

          <div className="hidden border-l pl-3 text-right text-sm sm:block">
            <Link to="/profile" className="font-medium hover:underline">
              {user?.firstName} {user?.lastName}
            </Link>
            <p className="text-xs text-muted-foreground">{user?.role.replace('_', ' ')}</p>
          </div>

          <Button variant="ghost" size="sm" onClick={() => void logout()} aria-label="Sign out">
            <LogOut className="h-4 w-4" />
          </Button>
        </header>

        <main className="p-5 lg:p-7">
          <Outlet />
        </main>
      </div>

      <IdleTimeout />
    </div>
  );
}
