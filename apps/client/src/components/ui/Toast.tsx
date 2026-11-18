import { createContext, useCallback, useContext, useState, type ReactNode } from 'react';
import { cx } from './cx';

type Tone = 'success' | 'danger' | 'info';
interface ToastItem {
  id: number;
  tone: Tone;
  message: string;
}

type Push = (message: string, tone?: Tone) => void;
const ToastContext = createContext<Push | null>(null);

const TONES: Record<Tone, string> = {
  success: 'border-success',
  danger: 'border-danger',
  info: 'border-info',
};

export function ToastProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<ToastItem[]>([]);
  const push = useCallback<Push>((message, tone = 'info') => {
    const id = Date.now() + Math.random();
    setItems((cur) => [...cur, { id, tone, message }]);
    setTimeout(() => setItems((cur) => cur.filter((t) => t.id !== id)), 6000);
  }, []);
  return (
    <ToastContext.Provider value={push}>
      {children}
      <div className="fixed bottom-4 right-4 z-50 flex flex-col gap-2" aria-live="polite">
        {items.map((t) => (
          <div
            key={t.id}
            role={t.tone === 'danger' ? 'alert' : 'status'}
            className={cx(
              'rounded-control border-l-4 bg-surface px-4 py-3 text-sm text-text shadow-overlay',
              TONES[t.tone],
            )}
          >
            {t.message}
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast(): Push {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error('useToast must be used inside <ToastProvider>');
  return ctx;
}
