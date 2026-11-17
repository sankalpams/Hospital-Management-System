// System timezone is Asia/Colombo: UTC+05:30, no DST. Date-only strings (YYYY-MM-DD) from
// date pickers mean whole local days.
const COLOMBO_OFFSET_MS = 5.5 * 60 * 60 * 1000;
const DAY_MS = 24 * 60 * 60 * 1000;
const DATE_ONLY = /^\d{4}-\d{2}-\d{2}$/;

export const isDateOnly = (s: string): boolean => DATE_ONLY.test(s);

/** True when YYYY-MM-DD is a real calendar day (rejects rollovers such as 2020-02-30). */
export function isValidCalendarDate(dateStr: string): boolean {
  if (!DATE_ONLY.test(dateStr)) return false;
  const [y, m, d] = dateStr.split('-').map(Number) as [number, number, number];
  return new Date(Date.UTC(y, m - 1, d)).toISOString().slice(0, 10) === dateStr;
}

/** Instant of 00:00 Colombo time on the given YYYY-MM-DD day. */
export function startOfDayColombo(dateStr: string): Date {
  return new Date(Date.parse(`${dateStr}T00:00:00Z`) - COLOMBO_OFFSET_MS);
}

/** Instant of 00:00 Colombo time on the day after the given YYYY-MM-DD day (exclusive upper bound). */
export function startOfNextDayColombo(dateStr: string): Date {
  return new Date(startOfDayColombo(dateStr).getTime() + DAY_MS);
}
