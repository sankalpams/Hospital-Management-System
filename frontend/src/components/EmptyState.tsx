import { Inbox } from 'lucide-react';
import type { ReactNode } from 'react';
export function EmptyState({ title, detail, action }: { title: string; detail: string; action?: ReactNode }) { return <div className="flex min-h-48 flex-col items-center justify-center border border-dashed p-8 text-center"><Inbox className="mb-3 h-7 w-7 text-muted-foreground" /><p className="font-medium">{title}</p><p className="mt-1 max-w-sm text-sm text-muted-foreground">{detail}</p>{action && <div className="mt-4">{action}</div>}</div>; }
