import { useId, useState } from 'react';
import { Bar, BarChart, CartesianGrid, LabelList, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import clsx from 'clsx';
import { humanise } from '../../components/ui/Badge';
import { formatBucket, formatShort, formatValue } from './figures';

/*
 * Chart kit for the analytics screens. Colours are the validated chart tokens (chart-1 blue,
 * chart-2 orange: CVD-separated and >= 3:1 on the surface). Marks are thin, grids recessive,
 * text uses text tokens, every chart has a hover tooltip and a table view.
 */
const SERIES = ['var(--color-chart-1)', 'var(--color-chart-2)'];
const INK = 'var(--color-foreground-muted)';
const GRID = 'var(--color-border)';

const count = new Intl.NumberFormat('en-GB');

// ---------------------------------------------------------------- chart frame with table view

function ChartCard({ title, description, table, children, className }) {
  const [showTable, setShowTable] = useState(false);
  const id = useId();
  return (
    <section aria-labelledby={id} className={clsx('rounded-lg border border-border bg-surface shadow-sm', className)}>
      <header className="flex items-start justify-between gap-3 px-4 pt-3">
        <div>
          <h3 id={id} className="text-sm font-semibold">{title}</h3>
          {description && <p className="text-xs text-foreground-muted">{description}</p>}
        </div>
        <button type="button" onClick={() => setShowTable((s) => !s)} aria-pressed={showTable}
                className="no-print shrink-0 rounded px-2 py-1 text-xs font-medium text-foreground-muted hover:bg-surface-muted hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring">
          {showTable ? 'Show chart' : 'Show table'}
        </button>
      </header>
      <div className="px-2 pb-3 pt-2">{showTable ? table : children}</div>
    </section>
  );
}

function ValueTable({ head, rows }) {
  return (
    <div className="max-h-72 overflow-auto px-2">
      <table className="w-full text-left text-sm tabular">
        <thead className="sticky top-0 bg-surface text-xs text-foreground-muted">
          <tr>{head.map((h, i) => <th key={h} className={clsx('py-1.5 pr-3 font-medium', i > 0 && 'text-right')}>{h}</th>)}</tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr key={r[0]} className="border-t border-border">
              {r.map((c, i) => <td key={i} className={clsx('py-1.5 pr-3', i > 0 && 'text-right')}>{c}</td>)}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function TooltipBox({ active, payload, label, unit, labelFormat }) {
  if (!active || !payload?.length) return null;
  return (
    <div className="rounded-md border border-border bg-surface px-3 py-2 text-xs shadow-md">
      <p className="mb-1 font-medium">{labelFormat ? labelFormat(label) : label}</p>
      {payload.map((p) => (
        <p key={p.dataKey} className="flex items-center gap-2 tabular">
          {payload.length > 1 && <span aria-hidden="true" className="inline-block h-2 w-2 rounded-full" style={{ background: p.color }} />}
          <span className="text-foreground-muted">{p.name}</span>
          <span className="ml-auto pl-3 font-medium text-foreground">{formatValue(p.value, unit)}</span>
        </p>
      ))}
    </div>
  );
}

// ---------------------------------------------------------------- trends

/** One or two series that share a unit, drawn on one axis. */
export function TrendChart({ title, description, series, granularity, height = 240 }) {
  if (!series?.length) return null;
  const unit = series[0].unit;
  const data = series[0].points.map((pt, i) => {
    const row = { bucket: pt.bucket };
    series.forEach((s) => { row[s.key] = Number(s.points[i]?.value ?? 0); });
    return row;
  });
  const fmt = (b) => formatBucket(b, granularity);
  return (
    <ChartCard title={title} description={description}
               table={<ValueTable head={['Period', ...series.map((s) => s.label)]} rows={data.map((d) => [fmt(d.bucket), ...series.map((s) => formatValue(d[s.key], unit))])} />}>
      {series.length > 1 && (
        <ul className="mb-1 flex flex-wrap gap-4 px-2 text-xs text-foreground-muted">
          {series.map((s, i) => (
            <li key={s.key} className="flex items-center gap-1.5"><span aria-hidden="true" className="inline-block h-0.5 w-4 rounded" style={{ background: SERIES[i] }} />{s.label}</li>
          ))}
        </ul>
      )}
      <ResponsiveContainer width="100%" height={height}>
        <LineChart data={data} margin={{ top: 8, right: 16, bottom: 0, left: 8 }}>
          <CartesianGrid vertical={false} stroke={GRID} strokeDasharray="0" />
          <XAxis dataKey="bucket" tickFormatter={fmt} tick={{ fill: INK, fontSize: 11 }} tickLine={false} axisLine={{ stroke: GRID }} minTickGap={24} />
          <YAxis tickFormatter={(v) => formatShort(v, unit === 'LKR' ? 'count' : unit)} tick={{ fill: INK, fontSize: 11 }} tickLine={false} axisLine={false} width={56}
                 domain={unit === 'percent' ? [0, 100] : [0, 'auto']} allowDecimals={false} />
          <Tooltip cursor={{ stroke: 'var(--color-foreground-subtle)', strokeWidth: 1 }} content={<TooltipBox unit={unit} labelFormat={fmt} />} />
          {series.map((s, i) => (
            <Line key={s.key} type="linear" dataKey={s.key} name={s.label} stroke={SERIES[i]} strokeWidth={2} dot={false}
                  activeDot={{ r: 4, stroke: 'var(--color-surface)', strokeWidth: 2 }} isAnimationActive={false} />
          ))}
        </LineChart>
      </ResponsiveContainer>
    </ChartCard>
  );
}

// ---------------------------------------------------------------- breakdowns

/** Horizontal bars, one hue, value at the end of each bar. */
export function BreakdownChart({ title, description, rows, unit }) {
  if (!rows?.length) {
    return <ChartCard title={title} description={description} table={null}><p className="px-2 py-8 text-center text-sm text-foreground-muted">No data for this period.</p></ChartCard>;
  }
  const data = rows.map((r) => ({ label: /^[A-Z_]+$/.test(r.label) ? humanise(r.label) : r.label, value: Number(r.value) }));
  const height = data.length * 30 + 16;
  // label column fits the longest label (about 6.5px a character at 12px) instead of a fixed share of a narrow screen
  const labelWidth = Math.max(56, Math.min(150, Math.max(...data.map((d) => d.label.length)) * 6.5));
  return (
    <ChartCard title={title} description={description ?? (unit === 'LKR' ? 'Amounts in LKR' : undefined)}
               table={<ValueTable head={['', 'Value']} rows={data.map((d) => [d.label, formatValue(d.value, unit)])} />}>
      <ResponsiveContainer width="100%" height={height}>
        <BarChart data={data} layout="vertical" margin={{ top: 0, right: 56, bottom: 0, left: 8 }} barCategoryGap={6}>
          <XAxis type="number" hide domain={[0, unit === 'percent' ? 100 : 'auto']} />
          <YAxis type="category" dataKey="label" width={labelWidth} tick={{ fill: 'var(--color-foreground)', fontSize: 12 }} tickLine={false} axisLine={false} interval={0} />
          <Tooltip cursor={{ fill: 'var(--color-surface-muted)' }} content={<TooltipBox unit={unit} />} />
          <Bar dataKey="value" name={title} fill={SERIES[0]} barSize={16} radius={[0, 4, 4, 0]} isAnimationActive={false}>
            <LabelList dataKey="value" position="right" formatter={(v) => formatShort(v, unit === 'LKR' ? 'count' : unit)} style={{ fill: INK, fontSize: 11 }} />
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </ChartCard>
  );
}

// ---------------------------------------------------------------- detail tables

export function ReportTable({ table }) {
  return (
    <section className="rounded-lg border border-border bg-surface shadow-sm">
      <h3 className="px-4 pt-3 text-sm font-semibold">{table.label}</h3>
      {table.rows.length === 0 ? (
        <p className="px-4 py-6 text-sm text-foreground-muted">Nothing to show for this period.</p>
      ) : (
        <div className="relative max-h-[28rem] overflow-auto px-4 pb-3 pt-2">
          <table className="w-full min-w-[560px] text-left text-sm">
            <thead className="sticky top-0 bg-surface text-xs text-foreground-muted">
              <tr>{table.columns.map((c, i) => <th key={c} className={clsx('py-2 pr-3 font-medium', i > 0 && 'text-right')}>{c}</th>)}</tr>
            </thead>
            <tbody className="tabular">
              {table.rows.map((row, ri) => (
                <tr key={ri} className="border-t border-border">
                  {row.map((cell, i) => (
                    <td key={i} className={clsx('py-1.5 pr-3', i > 0 && 'text-right', i === 0 && 'font-sans')}>
                      {typeof cell === 'number' ? count.format(cell) : String(cell)}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
