import type { PaymentMethod, RideStatus } from './types';

/** ₹225 (whole rupees), or ₹42.50 with `decimals` for receipts and statements. */
export function rupees(value: number | null | undefined, opts: { decimals?: boolean } = {}): string {
  if (value == null || Number.isNaN(value)) return '—';
  const negative = value < -0.004;
  const abs = Math.abs(value);
  const text = opts.decimals
    ? abs.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
    : Math.round(abs).toLocaleString('en-IN');
  return `${negative ? '−' : ''}₹${text}`;
}

/** 850 m, 4.2 km, 18 km. */
export function distance(metres: number | null | undefined): string {
  if (metres == null) return '—';
  if (metres < 1000) return `${Math.max(0, Math.round(metres / 10) * 10)} m`;
  const km = metres / 1000;
  return `${km < 10 ? km.toFixed(1) : Math.round(km)} km`;
}

/** 1 min, 14 min, 1 h 05 min. Anything under a minute reads "1 min". */
export function minutes(seconds: number | null | undefined): string {
  if (seconds == null) return '—';
  const m = Math.max(1, Math.round(seconds / 60));
  if (m < 60) return `${m} min`;
  return `${Math.floor(m / 60)} h ${String(m % 60).padStart(2, '0')} min`;
}

/** 0:42 or 3:05: a countdown or a waiting timer. */
export function clock(seconds: number | null | undefined): string {
  if (seconds == null) return '—';
  const s = Math.max(0, Math.round(seconds));
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
}

export function time(iso: string | null | undefined): string {
  if (!iso) return '—';
  return new Date(iso).toLocaleTimeString('en-IN', { hour: 'numeric', minute: '2-digit' });
}

export function date(iso: string | null | undefined): string {
  if (!iso) return '—';
  return new Date(iso).toLocaleDateString('en-IN', { day: 'numeric', month: 'short' });
}

export function dateTime(iso: string | null | undefined): string {
  if (!iso) return '—';
  return `${date(iso)}, ${time(iso)}`;
}

/** The time a number of seconds from now: "Arrives by 6:42 pm". */
export function timeIn(seconds: number): string {
  return new Date(Date.now() + seconds * 1000).toLocaleTimeString('en-IN', { hour: 'numeric', minute: '2-digit' });
}

export function ago(iso: string | null | undefined): string {
  if (!iso) return '—';
  const s = Math.round((Date.now() - new Date(iso).getTime()) / 1000);
  if (s < 60) return 'just now';
  if (s < 3600) return `${Math.floor(s / 60)} min ago`;
  if (s < 86400) return `${Math.floor(s / 3600)} h ago`;
  return date(iso);
}

export function rating(value: number | null | undefined): string {
  return value == null ? '—' : Number(value).toFixed(2).replace(/0$/, '');
}

/** "1 trip", "1,204 trips" */
export function count(n: number | null | undefined, one: string, many = `${one}s`): string {
  const v = Number(n ?? 0);
  return `${v.toLocaleString('en-IN')} ${v === 1 ? one : many}`;
}

/** "98765 00001" */
export function phone(value: string | null | undefined): string {
  if (!value) return '—';
  return value.length === 10 ? `${value.slice(0, 5)} ${value.slice(5)}` : value;
}

/** The first part of an address: "Balewadi High Street, Balewadi, Pune" -> "Balewadi High Street". */
export function shortPlace(address: string | null | undefined): string {
  if (!address) return '—';
  return address.split(',')[0].trim();
}

export function initials(name: string | null | undefined): string {
  if (!name) return '?';
  const parts = name.trim().split(/\s+/);
  return ((parts[0]?.[0] ?? '') + (parts.length > 1 ? parts[parts.length - 1][0] : '')).toUpperCase();
}

export const PAYMENT_LABEL: Record<PaymentMethod, string> = {
  CASH: 'Cash',
  UPI: 'UPI',
  WALLET: 'EcoRide Wallet',
};

export const STATUS_LABEL: Record<RideStatus, string> = {
  SEARCHING: 'Finding a driver',
  ACCEPTED: 'Driver on the way',
  ARRIVED: 'Driver arrived',
  IN_PROGRESS: 'On trip',
  COMPLETED: 'Completed',
  CANCELLED: 'Cancelled',
  NO_DRIVERS: 'No drivers',
};

export const VEHICLE_EMOJI: Record<string, string> = {
  BIKE: '🏍️',
  AUTO: '🛺',
  MINI: '🚗',
  SEDAN: '🚘',
  SUV: '🚙',
};
