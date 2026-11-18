import { Link } from 'react-router-dom';
import clsx from 'clsx';

/** Number formatting and KPI tiles for analytics; no chart library here, so pages can load charts lazily. */
const count = new Intl.NumberFormat('en-GB');
const compact = new Intl.NumberFormat('en-GB', { notation: 'compact', maximumFractionDigits: 1 });
const money = new Intl.NumberFormat('en-GB', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

/** Full value for tooltips, tables and CSV-like reading. */
export function formatValue(value, unit) {
  const n = Number(value ?? 0);
  switch (unit) {
    case 'percent': return `${n.toFixed(1)}%`;
    case 'LKR': return `LKR ${money.format(n)}`;
    case 'minutes': return `${n.toFixed(1)} min`;
    case 'hours': return `${n.toFixed(1)} h`;
    case 'days': return `${n % 1 === 0 ? n : n.toFixed(1)} day${n === 1 ? '' : 's'}`;
    default: return count.format(n);
  }
}

/** Short value for tiles and axis ticks. */
export function formatShort(value, unit) {
  const n = Number(value ?? 0);
  if (unit === 'LKR') return Math.abs(n) >= 100_000 ? `LKR ${compact.format(n)}` : `LKR ${count.format(Math.round(n))}`;
  if (unit === 'count' || !unit) return Math.abs(n) >= 100_000 ? compact.format(n) : count.format(n);
  return formatValue(n, unit);
}

const dayFmt = new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short', timeZone: 'UTC' });
const monthFmt = new Intl.DateTimeFormat('en-GB', { month: 'short', year: 'numeric', timeZone: 'UTC' });

export function formatBucket(bucket, granularity) {
  const d = new Date(`${bucket}T00:00:00Z`);
  if (granularity === 'month') return monthFmt.format(d);
  return granularity === 'week' ? `w/c ${dayFmt.format(d)}` : dayFmt.format(d);
}

// ---------------------------------------------------------------- KPI tiles

export function KpiGrid({ kpis, className }) {
  if (!kpis?.length) return null;
  return (
    <ul className={clsx('grid grid-cols-2 gap-3 lg:grid-cols-3 xl:grid-cols-5', className)}>
      {kpis.map((k) => {
        const body = (
          <>
            <p className="text-xs text-foreground-muted">{k.label}</p>
            <p className="mt-1 font-heading text-2xl font-semibold tabular" title={formatValue(k.value, k.unit)}>{formatShort(k.value, k.unit)}</p>
          </>
        );
        return k.link ? (
          <li key={k.key}><Link to={k.link} className="block h-full rounded-lg border border-border bg-surface px-4 py-3 shadow-sm hover:border-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring">
            {body}
          </Link></li>
        ) : (
          <li key={k.key} className="rounded-lg border border-border bg-surface px-4 py-3 shadow-sm">{body}</li>
        );
      })}
    </ul>
  );
}


/** Groups series that share a `group` so they are drawn on one chart. */
export function trendGroups(trends = []) {
  const groups = [];
  trends.forEach((s) => {
    const g = groups.find((x) => x.group === s.group);
    if (g) g.series.push(s); else groups.push({ group: s.group, series: [s] });
  });
  return groups;
}
