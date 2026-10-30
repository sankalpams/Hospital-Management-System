import { formatDateTime } from '../lib/utils';
export function DateTimeText({ value }: { value?: string }) { return <time className="tabular-nums" dateTime={value}>{formatDateTime(value)}</time>; }
