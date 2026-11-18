/**
 * Display formatting in the hospital's timezone and currency. The API sends UTC instants
 * (ISO "...Z") and local dates ("2026-11-18"); only formatting happens here, never business logic.
 */
export const HOSPITAL_TZ = 'Asia/Colombo';

const dateFmt = new Intl.DateTimeFormat('en-GB', { day: '2-digit', month: 'short', year: 'numeric', timeZone: HOSPITAL_TZ });
const dateTimeFmt = new Intl.DateTimeFormat('en-GB', {
  day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit', hour12: false, timeZone: HOSPITAL_TZ,
});
const timeFmt = new Intl.DateTimeFormat('en-GB', { hour: '2-digit', minute: '2-digit', hour12: false, timeZone: HOSPITAL_TZ });
const lkr = new Intl.NumberFormat('en-LK', { style: 'currency', currency: 'LKR', minimumFractionDigits: 2 });

/** "2026-11-18" (a calendar date, no timezone) -> "18 Nov 2026" */
export function formatDate(isoDate) {
  if (!isoDate) return '—';
  const [y, m, d] = isoDate.split('-').map(Number);
  return dateFmt.format(new Date(Date.UTC(y, m - 1, d, 6)));
}

export function formatDateTime(instant) {
  return instant ? dateTimeFmt.format(new Date(instant)) : '—';
}

export function formatTime(instant) {
  return instant ? timeFmt.format(new Date(instant)) : '—';
}

export function formatLKR(amount) {
  return amount == null ? '—' : lkr.format(Number(amount));
}

/** Age in completed years at `today` (YYYY-MM-DD strings). */
export function ageFrom(dateOfBirth, today = todayInHospital()) {
  if (!dateOfBirth) return null;
  const [by, bm, bd] = dateOfBirth.split('-').map(Number);
  const [ty, tm, td] = today.split('-').map(Number);
  let age = ty - by;
  if (tm < bm || (tm === bm && td < bd)) age -= 1;
  return age;
}

/** Today's date in the hospital timezone as YYYY-MM-DD. */
export function todayInHospital(now = new Date()) {
  return new Intl.DateTimeFormat('en-CA', { timeZone: HOSPITAL_TZ }).format(now);
}

export function addDays(isoDate, days) {
  const [y, m, d] = isoDate.split('-').map(Number);
  const dt = new Date(Date.UTC(y, m - 1, d + days));
  return dt.toISOString().slice(0, 10);
}
