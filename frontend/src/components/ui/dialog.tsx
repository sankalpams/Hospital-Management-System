import * as DialogPrimitive from '@radix-ui/react-dialog';
import { X } from 'lucide-react';
import type { ReactNode } from 'react';
export const Dialog = DialogPrimitive.Root;
export function DialogContent({ title, children }: { title: string; children: ReactNode }) { return <DialogPrimitive.Portal><DialogPrimitive.Overlay className="fixed inset-0 z-40 bg-slate-950/40" /><DialogPrimitive.Content className="fixed left-1/2 top-1/2 z-50 w-[min(92vw,36rem)] -translate-x-1/2 -translate-y-1/2 rounded-lg border bg-surface p-6 shadow-panel"><div className="mb-4 flex items-center justify-between"><DialogPrimitive.Title className="text-lg font-semibold">{title}</DialogPrimitive.Title><DialogPrimitive.Close aria-label="Close"><X className="h-5 w-5" /></DialogPrimitive.Close></div>{children}</DialogPrimitive.Content></DialogPrimitive.Portal>; }
