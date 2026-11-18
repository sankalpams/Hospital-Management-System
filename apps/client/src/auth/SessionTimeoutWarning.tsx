import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button } from '../components/ui/Button';
import { Dialog } from '../components/ui/Dialog';
import { useAuth } from './useAuth';

export const WARNING_SECONDS = 60;

const secondsLeft = (expiresAt: Date) => Math.ceil((expiresAt.getTime() - Date.now()) / 1000);

/**
 * Warns when 60s or less remain on the session, counts down live, and signs the user out at 0.
 * "Stay signed in" calls refresh() (GET /auth/me), which slides the server session and updates expiresAt.
 */
export function SessionTimeoutWarning() {
  const { expiresAt, logout, refresh } = useAuth();
  const navigate = useNavigate();
  const [remaining, setRemaining] = useState<number | null>(null);
  const [staying, setStaying] = useState(false);
  const expiredRef = useRef(false);
  const logoutRef = useRef(logout);
  const navigateRef = useRef(navigate);
  useEffect(() => {
    logoutRef.current = logout;
    navigateRef.current = navigate;
  });

  // A new expiresAt (e.g. after "Stay signed in") re-arms the auto-logout.
  useEffect(() => {
    expiredRef.current = false;
  }, [expiresAt]);

  useEffect(() => {
    if (!expiresAt) {
      setRemaining(null);
      return;
    }
    const tick = () => {
      const left = secondsLeft(expiresAt);
      setRemaining(left);
      if (left <= 0 && !expiredRef.current) {
        expiredRef.current = true;
        void logoutRef
          .current()
          .catch(() => undefined)
          .finally(() => navigateRef.current('/login?reason=timeout', { replace: true }));
      }
    };
    tick();
    const id = setInterval(tick, 1000);
    return () => clearInterval(id);
  }, [expiresAt]);

  const stay = async () => {
    setStaying(true);
    try {
      await refresh();
    } finally {
      setStaying(false);
    }
  };

  const onLogout = async () => {
    expiredRef.current = true;
    try {
      await logout();
    } catch {
      /* cleared client-side regardless */
    }
    navigate('/login', { replace: true });
  };

  const open = remaining !== null && remaining > 0 && remaining <= WARNING_SECONDS;
  return (
    <Dialog open={open} title="Session expiring soon" onClose={() => void stay()}>
      <p className="text-sm text-text">
        You will be signed out for inactivity in{' '}
        <strong data-testid="countdown" className="tabular-nums">
          {remaining ?? 0}
        </strong>{' '}
        seconds. Unsaved changes will be lost.
      </p>
      <div role="status" className="sr-only">
        {open && remaining !== null && remaining % 15 === 0 ? `${remaining} seconds remaining` : ''}
      </div>
      <div className="mt-6 flex flex-col gap-2 sm:flex-row-reverse">
        <Button loading={staying} onClick={() => void stay()}>
          Stay signed in
        </Button>
        <Button variant="secondary" onClick={() => void onLogout()}>
          Log out
        </Button>
      </div>
    </Dialog>
  );
}
