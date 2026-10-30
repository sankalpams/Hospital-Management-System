import { forwardRef, type InputHTMLAttributes } from 'react';
import { cn } from '../../lib/utils';
export const Input = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement>>(({ className, ...props }, ref) => <input ref={ref} className={cn('h-10 w-full rounded-md border bg-surface px-3 text-sm placeholder:text-muted-foreground', className)} {...props} />);
Input.displayName = 'Input';
