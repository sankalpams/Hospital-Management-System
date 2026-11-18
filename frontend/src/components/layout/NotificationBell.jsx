import { safeAppPath } from '../../lib/forms';
import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { AlertTriangle, Bell, Info, OctagonAlert } from 'lucide-react';
import clsx from 'clsx';
import { api } from '../../api/client';
import { formatDateTime } from '../../lib/format';

const SEVERITY = {
  INFO: { Icon: Info, className: 'text-info', label: 'Information' },
  WARNING: { Icon: AlertTriangle, className: 'text-warning', label: 'Warning' },
  CRITICAL: { Icon: OctagonAlert, className: 'text-danger', label: 'Critical' },
};

/** Unread count is polled; the list loads only when the panel opens. */
export function NotificationBell() {
  const [open, setOpen] = useState(false);
  const ref = useRef(null);
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const count = useQuery({
    queryKey: ['notifications', 'count'],
    queryFn: () => api.get('/notifications/unread-count').then((r) => r.data.count),
    refetchInterval: 60_000,
  });
  const list = useQuery({
    queryKey: ['notifications', 'list'],
    queryFn: () => api.get('/notifications', { params: { size: 10 } }).then((r) => r.data),
    enabled: open,
  });
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['notifications'] });
  const markRead = useMutation({ mutationFn: (id) => api.post(`/notifications/${id}/read`), onSuccess: refresh });
  const markAll = useMutation({ mutationFn: () => api.post('/notifications/read-all'), onSuccess: refresh });

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

  const unread = count.data ?? 0;
  const items = list.data?.content ?? [];

  return (
    <div className="relative" ref={ref}>
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        aria-haspopup="true"
        aria-expanded={open}
        aria-label={unread ? `Notifications, ${unread} unread` : 'Notifications'}
        className="relative flex h-10 w-10 items-center justify-center rounded-md text-foreground-muted hover:bg-surface-muted hover:text-foreground"
      >
        <Bell className="h-5 w-5" aria-hidden="true" />
        {unread > 0 && (
          <span className="absolute right-1.5 top-1.5 flex h-[18px] min-w-[18px] items-center justify-center rounded-full bg-danger px-1 text-[11px] font-semibold leading-none text-white tabular">
            {unread > 99 ? '99+' : unread}
          </span>
        )}
      </button>

      {open && (
        <div className="absolute right-0 z-30 mt-2 w-[min(22rem,calc(100vw-2rem))] overflow-hidden rounded-lg border border-border bg-surface shadow-md">
          <div className="flex items-center justify-between border-b border-border px-4 py-3">
            <h2 className="text-sm">Notifications</h2>
            {unread > 0 && (
              <button type="button" onClick={() => markAll.mutate()} disabled={markAll.isPending}
                      className="text-xs font-medium text-primary hover:underline disabled:opacity-50">
                Mark all as read
              </button>
            )}
          </div>
          <ul className="max-h-96 overflow-y-auto">
            {list.isLoading && <li className="px-4 py-6 text-center text-sm text-foreground-muted">Loading…</li>}
            {list.isSuccess && items.length === 0 && (
              <li className="px-4 py-8 text-center text-sm text-foreground-muted">You’re all caught up.</li>
            )}
            {items.map((n) => {
              const s = SEVERITY[n.severity] ?? SEVERITY.INFO;
              return (
                <li key={n.id} className="border-b border-border last:border-0">
                  <button
                    type="button"
                    onClick={() => {
                      if (!n.readAt) markRead.mutate(n.id);
                      setOpen(false);
                      if (n.linkPath) navigate(safeAppPath(n.linkPath));
                    }}
                    className={clsx('flex w-full gap-3 px-4 py-3 text-left hover:bg-surface-muted', !n.readAt && 'bg-info-soft')}
                  >
                    <s.Icon className={clsx('mt-0.5 h-4 w-4 shrink-0', s.className)} aria-hidden="true" />
                    <span className="min-w-0 flex-1">
                      <span className="sr-only">{s.label}{n.readAt ? '' : ', unread'}: </span>
                      <span className={clsx('block text-sm', !n.readAt && 'font-semibold')}>{n.title}</span>
                      {n.body && <span className="mt-0.5 block text-xs text-foreground-muted">{n.body}</span>}
                      <span className="mt-1 block text-[11px] text-foreground-subtle tabular">{formatDateTime(n.createdAt)}</span>
                    </span>
                  </button>
                </li>
              );
            })}
          </ul>
        </div>
      )}
    </div>
  );
}
