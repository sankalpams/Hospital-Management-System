import { useEffect, useId, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { X } from 'lucide-react';
import clsx from 'clsx';
import { Button } from './Button';
import { TextAreaField } from './Field';

const FOCUSABLE = 'a[href],button:not([disabled]),input:not([disabled]),select:not([disabled]),textarea:not([disabled]),[tabindex]:not([tabindex="-1"])';

/**
 * Accessible dialog: focus moves inside on open, Tab is trapped, Esc closes, focus returns to
 * the opener on close (MASTER.md §6). Use for short forms and confirmations only.
 */
export function Modal({ open, onClose, title, description, children, footer, size = 'md' }) {
  const panelRef = useRef(null);
  const titleId = useId();
  const descId = useId();

  useEffect(() => {
    if (!open) return undefined;
    const opener = document.activeElement;
    const panel = panelRef.current;
    const first = panel?.querySelector('[data-autofocus]') ?? panel?.querySelector(FOCUSABLE);
    (first ?? panel)?.focus();

    const onKey = (e) => {
      if (e.key === 'Escape') {
        e.stopPropagation();
        onClose?.();
      } else if (e.key === 'Tab' && panel) {
        const items = [...panel.querySelectorAll(FOCUSABLE)];
        if (items.length === 0) return;
        const firstEl = items[0];
        const lastEl = items[items.length - 1];
        if (e.shiftKey && document.activeElement === firstEl) { e.preventDefault(); lastEl.focus(); }
        else if (!e.shiftKey && document.activeElement === lastEl) { e.preventDefault(); firstEl.focus(); }
      }
    };
    document.addEventListener('keydown', onKey);
    const overflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', onKey);
      document.body.style.overflow = overflow;
      opener?.focus?.();
    };
  }, [open, onClose]);

  if (!open) return null;
  return createPortal(
    <div className="fixed inset-0 z-50 flex items-end justify-center p-0 sm:items-center sm:p-4">
      <div className="absolute inset-0 bg-sidebar opacity-50" onClick={onClose} aria-hidden="true" />
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={description ? descId : undefined}
        tabIndex={-1}
        className={clsx(
          'relative flex max-h-[92vh] w-full flex-col rounded-t-xl bg-surface shadow-lg sm:rounded-xl',
          size === 'sm' && 'sm:max-w-md',
          size === 'md' && 'sm:max-w-lg',
          size === 'lg' && 'sm:max-w-2xl',
        )}
      >
        <header className="flex items-start justify-between gap-4 border-b border-border px-5 py-4">
          <div>
            <h2 id={titleId} className="text-lg">{title}</h2>
            {description && <p id={descId} className="mt-0.5 text-sm text-foreground-muted">{description}</p>}
          </div>
          <button type="button" onClick={onClose} className="rounded-md p-1.5 text-foreground-subtle hover:bg-surface-muted hover:text-foreground" aria-label="Close dialog">
            <X className="h-5 w-5" aria-hidden="true" />
          </button>
        </header>
        <div className="overflow-y-auto px-5 py-4">{children}</div>
        {footer && <footer className="flex flex-col-reverse gap-2 border-t border-border px-5 py-3 sm:flex-row sm:justify-end">{footer}</footer>}
      </div>
    </div>,
    document.body,
  );
}

/**
 * Confirmation for destructive/irreversible actions. States the consequence; optionally
 * requires a reason (sent to the server and stored in the audit trail).
 */
export function ConfirmDialog({
  open, onClose, onConfirm, title, message, confirmLabel = 'Confirm', tone = 'danger',
  requireReason = false, reasonLabel = 'Reason', loading = false, error,
}) {
  const [reason, setReason] = useState('');
  const [touched, setTouched] = useState(false);
  useEffect(() => { if (open) { setReason(''); setTouched(false); } }, [open]);
  const reasonMissing = requireReason && reason.trim().length < 3;

  const submit = () => {
    setTouched(true);
    if (reasonMissing) return;
    onConfirm(reason.trim());
  };

  return (
    <Modal
      open={open}
      onClose={loading ? undefined : onClose}
      title={title}
      size="sm"
      footer={(
        <>
          <Button variant="ghost" onClick={onClose} disabled={loading}>Go back</Button>
          <Button variant={tone === 'danger' ? 'danger' : 'primary'} onClick={submit} loading={loading}>{confirmLabel}</Button>
        </>
      )}
    >
      <div className="flex flex-col gap-4 text-sm">
        <p className="text-foreground-muted">{message}</p>
        {requireReason && (
          <TextAreaField
            label={reasonLabel}
            required
            data-autofocus
            value={reason}
            maxLength={255}
            onChange={(e) => setReason(e.target.value)}
            error={touched && reasonMissing ? 'Please give a reason (at least 3 characters).' : undefined}
          />
        )}
        {error && <p role="alert" className="font-medium text-danger">{error}</p>}
      </div>
    </Modal>
  );
}
