import { forwardRef, type ButtonHTMLAttributes } from 'react';
import { cn } from '../../lib/utils';
type Props = ButtonHTMLAttributes<HTMLButtonElement> & { variant?: 'primary' | 'secondary' | 'danger' | 'ghost'; size?: 'sm' | 'md' };
export const Button = forwardRef<HTMLButtonElement, Props>(({ className, variant = 'primary', size = 'md', ...props }, ref) => <button ref={ref} className={cn('inline-flex items-center justify-center gap-2 rounded-md font-medium transition-colors disabled:pointer-events-none disabled:opacity-50', size === 'sm' ? 'h-8 px-3 text-xs' : 'h-10 px-4 text-sm', { primary: 'bg-primary text-primary-foreground hover:opacity-90', secondary: 'bg-muted text-foreground hover:bg-border', danger: 'bg-danger text-white hover:opacity-90', ghost: 'hover:bg-muted text-foreground' }[variant], className)} {...props} />);
Button.displayName = 'Button';
