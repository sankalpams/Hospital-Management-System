import { formatLkr } from '../lib/utils';
export function MoneyText({ value }: { value: number }) { return <span className="tabular-nums">{formatLkr(value)}</span>; }
