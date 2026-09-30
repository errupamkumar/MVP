/**
 * Formatting helpers.
 *
 * The server sends plant-local wall time without an offset
 * ("2026-09-30T14:05:12.1234567"). These helpers read the digits straight
 * out of the string and never build a JS Date from it, so a phone set to a
 * different time zone (or a JS engine that rejects 7-digit fractions) still
 * shows exactly the time the plant sees.
 */

/** IST, the plant's zone. Used to build scheduled pickup times regardless of the phone's own zone. */
export const PLANT_UTC_OFFSET_MINUTES = 330;

const WEEKDAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

interface WallTime {
  y: number;
  mo: number;
  d: number;
  h: number;
  mi: number;
}

export function wall(iso: string | null | undefined): WallTime | null {
  if (!iso) return null;
  const m = /^(\d{4})-(\d{2})-(\d{2})(?:T(\d{2}):(\d{2}))?/.exec(iso);
  if (!m) return null;
  return { y: +m[1], mo: +m[2], d: +m[3], h: m[4] ? +m[4] : 0, mi: m[5] ? +m[5] : 0 };
}

const pad = (n: number) => String(n).padStart(2, '0');

export function time(iso: string | null | undefined): string {
  const w = wall(iso);
  return w ? `${pad(w.h)}:${pad(w.mi)}` : '—';
}

export function shortDate(iso: string | null | undefined): string {
  const w = wall(iso);
  return w ? `${w.d} ${MONTHS[w.mo - 1]}` : '—';
}

export function dateTime(iso: string | null | undefined): string {
  const w = wall(iso);
  return w ? `${w.d} ${MONTHS[w.mo - 1]} ${w.y} · ${pad(w.h)}:${pad(w.mi)}` : '—';
}

function weekday(w: WallTime): string {
  return WEEKDAYS[new Date(Date.UTC(w.y, w.mo - 1, w.d)).getUTCDay()];
}

/** "Today · 14:05", "Tomorrow · 08:05", "Fri · 17:40" or "12 Sep · 09:10". */
export function dayAndTime(iso: string | null | undefined): string {
  const w = wall(iso);
  if (!w) return '—';
  const today = plantNow();
  const dayIndex = (x: WallTime) => Math.floor(Date.UTC(x.y, x.mo - 1, x.d) / 86_400_000);
  const diff = dayIndex(w) - dayIndex(today);
  const t = `${pad(w.h)}:${pad(w.mi)}`;
  if (diff === 0) return `Today · ${t}`;
  if (diff === 1) return `Tomorrow · ${t}`;
  if (diff === -1) return `Yesterday · ${t}`;
  if (diff > -7 && diff < 0) return `${weekday(w)} · ${t}`;
  return `${w.d} ${MONTHS[w.mo - 1]} · ${t}`;
}

/** The current wall time at the plant, whatever the phone's time zone. */
export function plantNow(): WallTime {
  const shifted = new Date(Date.now() + PLANT_UTC_OFFSET_MINUTES * 60_000);
  return {
    y: shifted.getUTCFullYear(),
    mo: shifted.getUTCMonth() + 1,
    d: shifted.getUTCDate(),
    h: shifted.getUTCHours(),
    mi: shifted.getUTCMinutes(),
  };
}

/** Plant-local ISO string (no offset) for "now + minutes", as the API expects for scheduledAt. */
export function plantIsoIn(minutes: number): string {
  const shifted = new Date(Date.now() + (PLANT_UTC_OFFSET_MINUTES + minutes) * 60_000);
  return `${shifted.getUTCFullYear()}-${pad(shifted.getUTCMonth() + 1)}-${pad(shifted.getUTCDate())}T${pad(
    shifted.getUTCHours(),
  )}:${pad(shifted.getUTCMinutes())}:00`;
}

/** Plant-local ISO string for a clock time on a day relative to today (0 = today, 1 = tomorrow). */
export function plantIsoAt(dayOffset: number, hour: number, minute: number): string {
  const n = plantNow();
  const d = new Date(Date.UTC(n.y, n.mo - 1, n.d + dayOffset, hour, minute));
  return `${d.getUTCFullYear()}-${pad(d.getUTCMonth() + 1)}-${pad(d.getUTCDate())}T${pad(hour)}:${pad(minute)}:00`;
}

/** Parses an Instant ("...Z") safely: trims fractional seconds beyond milliseconds first. */
export function parseInstant(iso: string): Date {
  return new Date(iso.replace(/(\.\d{3})\d+/, '$1'));
}

export function rupees(amount: number | null | undefined): string {
  if (amount == null) return '—';
  return `₹${amount.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

export function wholeRupees(amount: number | null | undefined): string {
  if (amount == null) return '—';
  return `₹${Math.round(amount).toLocaleString('en-IN')}`;
}

export function km(value: number | null | undefined): string {
  if (value == null) return '—';
  return `${Number(value).toFixed(1)} km`;
}

export function minutes(value: number | null | undefined): string {
  if (value == null) return '—';
  if (value < 1) return 'now';
  if (value < 60) return `${value} min`;
  return `${Math.floor(value / 60)} h ${value % 60} min`;
}

export function hoursMinutes(total: number): string {
  return `${Math.floor(total / 60)}h ${pad(total % 60)}m`;
}

export function initials(name: string | null | undefined): string {
  if (!name) return '?';
  const parts = name.replace(/\./g, ' ').split(/\s+/).filter(Boolean);
  return (parts[0]?.[0] ?? '') + (parts.length > 1 ? parts[parts.length - 1][0] : '');
}

export function greeting(): string {
  const h = plantNow().h;
  if (h < 12) return 'Good morning';
  if (h < 17) return 'Good afternoon';
  return 'Good evening';
}

export function titleCase(value: string): string {
  return value
    .toLowerCase()
    .split('_')
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
    .join(' ');
}

export function currentPeriod(): string {
  const n = plantNow();
  return `${n.y}-${pad(n.mo)}`;
}

export function shiftPeriod(period: string, months: number): string {
  const [y, m] = period.split('-').map(Number);
  const d = new Date(Date.UTC(y, m - 1 + months, 1));
  return `${d.getUTCFullYear()}-${pad(d.getUTCMonth() + 1)}`;
}

export function periodLabel(period: string): string {
  const [y, m] = period.split('-').map(Number);
  return `${['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'][m - 1]} ${y}`;
}
