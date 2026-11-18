import type { LabelHTMLAttributes } from 'react';
import { cx } from './cx';

export function Label({ className, ...rest }: LabelHTMLAttributes<HTMLLabelElement>) {
  return <label className={cx('mb-1 block text-sm font-medium text-text', className)} {...rest} />;
}
