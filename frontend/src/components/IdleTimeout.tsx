import { useEffect, useRef, useState } from 'react';
import { useAuth } from '../hooks/use-auth';
import { Dialog, DialogContent } from './ui/dialog';
import { Button } from './ui/button';
const TIMEOUT = 15 * 60_000;
const WARNING = 60_000;
export function IdleTimeout() {
  const { session, logout } = useAuth(); const [warning, setWarning] = useState(false); const timer = useRef<number>(); const warningTimer = useRef<number>();
  useEffect(() => {
    if (!session) return;
    const reset = () => { setWarning(false); window.clearTimeout(timer.current); window.clearTimeout(warningTimer.current); warningTimer.current = window.setTimeout(() => setWarning(true), TIMEOUT - WARNING); timer.current = window.setTimeout(() => void logout(), TIMEOUT); };
    const events = ['pointerdown', 'keydown', 'mousemove', 'touchstart']; events.forEach((event) => window.addEventListener(event, reset)); reset();
    return () => { events.forEach((event) => window.removeEventListener(event, reset)); window.clearTimeout(timer.current); window.clearTimeout(warningTimer.current); };
  }, [session, logout]);
  return <Dialog open={warning} onOpenChange={(open) => !open && setWarning(false)}><DialogContent title="Still working?"><p className="text-sm text-muted-foreground">For your security, you’ll be signed out in about a minute due to inactivity.</p><div className="mt-6 flex justify-end"><Button onClick={() => { window.dispatchEvent(new Event('mousemove')); setWarning(false); }}>Keep me signed in</Button></div></DialogContent></Dialog>;
}
