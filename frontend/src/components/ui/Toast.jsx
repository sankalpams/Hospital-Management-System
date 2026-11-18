import { createContext, useCallback, useContext, useMemo, useRef, useState } from 'react';
import { CheckCircle2, Info, X, XCircle } from 'lucide-react';
import clsx from 'clsx';

const ToastContext = createContext(null);

const tones = {
  success: { box: 'border-border bg-surface', icon: 'text-success', Icon: CheckCircle2 },
  error: { box: 'border-border bg-surface', icon: 'text-danger', Icon: XCircle },
  info: { box: 'border-border bg-surface', icon: 'text-info', Icon: Info },
};

/** Success/info feedback after an action (MASTER.md §6). Errors that need reading stay inline. */
export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([]);
  const seq = useRef(0);

  const dismiss = useCallback((id) => setToasts((t) => t.filter((x) => x.id !== id)), []);
  const show = useCallback((message, tone = 'success', ms = 4500) => {
    seq.current += 1;
    const id = seq.current;
    setToasts((t) => [...t.slice(-3), { id, message, tone }]);
    window.setTimeout(() => dismiss(id), ms);
  }, [dismiss]);

  const api = useMemo(() => ({
    success: (m) => show(m, 'success'),
    error: (m) => show(m, 'error', 7000),
    info: (m) => show(m, 'info'),
  }), [show]);

  return (
    <ToastContext.Provider value={api}>
      {children}
      <div aria-live="polite" className="pointer-events-none fixed bottom-4 right-4 z-[60] flex w-[calc(100%-2rem)] max-w-sm flex-col gap-2">
        {toasts.map(({ id, message, tone }) => {
          const t = tones[tone];
          return (
            <div key={id} role="status" className={clsx('pointer-events-auto flex items-start gap-3 rounded-lg border px-4 py-3 text-sm shadow-md', t.box)}>
              <t.Icon className={clsx('mt-0.5 h-4 w-4 shrink-0', t.icon)} aria-hidden="true" />
              <p className="flex-1 text-foreground">{message}</p>
              <button type="button" onClick={() => dismiss(id)} className="text-foreground-subtle hover:text-foreground" aria-label="Dismiss notification">
                <X className="h-4 w-4" aria-hidden="true" />
              </button>
            </div>
          );
        })}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error('useToast must be used inside <ToastProvider>');
  return ctx;
}
