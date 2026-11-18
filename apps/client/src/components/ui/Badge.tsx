import type { ReactNode } from 'react';
import { StatusBadge, type StatusTone } from './StatusBadge';

/** Kept for existing call sites; StatusBadge is the underlying component. */
export function Badge({ tone = 'neutral', children }: { tone?: StatusTone; children: ReactNode }) {
  return <StatusBadge tone={tone}>{children}</StatusBadge>;
}
