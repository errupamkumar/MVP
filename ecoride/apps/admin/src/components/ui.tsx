import { ApiError, fmt, toApiError } from '@ecoride/core';
import { ReactNode, useEffect, useState } from 'react';

import { API_URL } from '@/lib/api';

export function cx(...parts: (string | false | null | undefined)[]) {
  return parts.filter(Boolean).join(' ');
}

export function Card({ children, className, title, action, pad = true }: { children: ReactNode; className?: string; title?: ReactNode; action?: ReactNode; pad?: boolean }) {
  return (
    <section className={cx('rounded-xl border border-line bg-white shadow-[0_1px_2px_rgba(17,24,39,0.04)]', className)}>
      {title || action ? (
        <header className="flex items-center justify-between gap-3 border-b border-line px-5 py-3.5">
          <h2 className="text-[15px] font-semibold text-ink">{title}</h2>
          {action}
        </header>
      ) : null}
      <div className={pad ? 'p-5' : ''}>{children}</div>
    </section>
  );
}

export function PageHeader({ title, subtitle, actions }: { title: string; subtitle?: ReactNode; actions?: ReactNode }) {
  return (
    <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-ink">{title}</h1>
        {subtitle ? <p className="mt-1 text-sm text-ink-3">{subtitle}</p> : null}
      </div>
      {actions ? <div className="flex flex-wrap items-center gap-2">{actions}</div> : null}
    </div>
  );
}

type ButtonVariant = 'primary' | 'brand' | 'secondary' | 'danger' | 'ghost';

export function Button({
  children,
  onClick,
  variant = 'secondary',
  disabled,
  loading,
  type = 'button',
  size = 'md',
  title,
}: {
  children: ReactNode;
  onClick?: () => void;
  variant?: ButtonVariant;
  disabled?: boolean;
  loading?: boolean;
  type?: 'button' | 'submit';
  size?: 'sm' | 'md';
  title?: string;
}) {
  const styles: Record<ButtonVariant, string> = {
    primary: 'bg-ink text-white hover:bg-ink-2',
    brand: 'bg-brand text-white hover:bg-brand-dark',
    secondary: 'border border-line bg-white text-ink hover:bg-mist',
    danger: 'bg-critical text-white hover:opacity-90',
    ghost: 'text-ink-2 hover:bg-mist',
  };
  return (
    <button
      type={type}
      title={title}
      onClick={onClick}
      disabled={disabled || loading}
      className={cx(
        'inline-flex items-center justify-center gap-2 rounded-lg font-semibold transition disabled:cursor-not-allowed disabled:opacity-50',
        size === 'sm' ? 'h-8 px-3 text-xs' : 'h-10 px-4 text-sm',
        styles[variant],
      )}
    >
      {loading ? <span className="h-4 w-4 animate-spin rounded-full border-2 border-current border-t-transparent" /> : null}
      {children}
    </button>
  );
}

const TONES = {
  neutral: 'bg-mist text-ink-2',
  brand: 'bg-brand-soft text-brand-dark',
  info: 'bg-[#E8F0FE] text-[#1D5FD6]',
  warn: 'bg-[#FEF3C7] text-[#92400E]',
  danger: 'bg-[#FDECEC] text-[#B42318]',
  dark: 'bg-ink text-white',
} as const;
export type Tone = keyof typeof TONES;

export function Badge({ children, tone = 'neutral', icon }: { children: ReactNode; tone?: Tone; icon?: string }) {
  return (
    <span className={cx('inline-flex items-center gap-1 whitespace-nowrap rounded-full px-2 py-0.5 text-xs font-semibold', TONES[tone])}>
      {icon ? <span aria-hidden>{icon}</span> : null}
      {children}
    </span>
  );
}

/** Ride status: icon + label + colour, never colour alone. */
export function RideStatusBadge({ status }: { status: string }) {
  const map: Record<string, [Tone, string]> = {
    SEARCHING: ['warn', '⌕'],
    ACCEPTED: ['info', '➜'],
    ARRIVED: ['info', '⚑'],
    IN_PROGRESS: ['dark', '●'],
    COMPLETED: ['brand', '✓'],
    CANCELLED: ['danger', '✕'],
    NO_DRIVERS: ['danger', '∅'],
  };
  const [tone, icon] = map[status] ?? ['neutral', '•'];
  return (
    <Badge tone={tone} icon={icon}>
      {fmt.STATUS_LABEL[status as keyof typeof fmt.STATUS_LABEL] ?? status}
    </Badge>
  );
}

export function DriverStatusBadge({ status }: { status: string }) {
  const map: Record<string, [Tone, string, string]> = {
    PENDING_APPROVAL: ['warn', '⏳', 'Pending approval'],
    APPROVED: ['brand', '✓', 'Approved'],
    SUSPENDED: ['danger', '⏸', 'Suspended'],
    REJECTED: ['neutral', '✕', 'Rejected'],
  };
  const [tone, icon, label] = map[status] ?? ['neutral', '•', status];
  return (
    <Badge tone={tone} icon={icon}>
      {label}
    </Badge>
  );
}

export function AvailabilityBadge({ value }: { value: string }) {
  if (value === 'ONLINE') return <Badge tone="brand" icon="●">Online</Badge>;
  if (value === 'ON_TRIP') return <Badge tone="info" icon="●">On trip</Badge>;
  return <Badge icon="○">Offline</Badge>;
}

export function SimTag({ on }: { on: boolean }) {
  return on ? (
    <span className="ml-1.5 rounded bg-mist px-1 py-px text-[10px] font-semibold uppercase tracking-wide text-ink-3" title="Simulated demo account">
      sim
    </span>
  ) : null;
}

export function StatTile({ label, value, sub, accent }: { label: string; value: ReactNode; sub?: ReactNode; accent?: boolean }) {
  return (
    <div className={cx('rounded-xl border p-4', accent ? 'border-ink bg-ink text-white' : 'border-line bg-white')}>
      <div className={cx('text-xs font-medium', accent ? 'text-ink-4' : 'text-ink-3')}>{label}</div>
      <div className="mt-1 text-2xl font-semibold tracking-tight">{value}</div>
      {sub ? <div className={cx('mt-1 text-xs', accent ? 'text-ink-4' : 'text-ink-3')}>{sub}</div> : null}
    </div>
  );
}

export function KeyValue({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex items-start justify-between gap-4 border-b border-line py-2 text-sm last:border-b-0">
      <span className="text-ink-3">{label}</span>
      <span className="text-right font-medium text-ink">{children}</span>
    </div>
  );
}

export function Input(props: React.InputHTMLAttributes<HTMLInputElement> & { label?: string; hint?: string }) {
  const { label, hint, className, ...rest } = props;
  return (
    <label className={cx('block', className)}>
      {label ? <span className="mb-1 block text-xs font-semibold text-ink-2">{label}</span> : null}
      <input
        {...rest}
        className="h-10 w-full rounded-lg border border-line bg-white px-3 text-sm text-ink outline-none transition focus:border-ink focus:ring-2 focus:ring-ink/10"
      />
      {hint ? <span className="mt-1 block text-xs text-ink-3">{hint}</span> : null}
    </label>
  );
}

export function Select({ label, value, onChange, options, className }: { label?: string; value: string; onChange: (v: string) => void; options: { value: string; label: string }[]; className?: string }) {
  return (
    <label className={cx('block', className)}>
      {label ? <span className="mb-1 block text-xs font-semibold text-ink-2">{label}</span> : null}
      <select
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="h-10 w-full rounded-lg border border-line bg-white px-3 text-sm text-ink outline-none focus:border-ink"
      >
        {options.map((o) => (
          <option key={o.value} value={o.value}>
            {o.label}
          </option>
        ))}
      </select>
    </label>
  );
}

export function Tabs({ value, onChange, items }: { value: string; onChange: (v: string) => void; items: { value: string; label: string; count?: number }[] }) {
  return (
    <div className="flex flex-wrap gap-1 rounded-lg bg-mist p-1">
      {items.map((i) => (
        <button
          key={i.value}
          onClick={() => onChange(i.value)}
          className={cx('rounded-md px-3 py-1.5 text-sm font-semibold transition', value === i.value ? 'bg-white text-ink shadow-sm' : 'text-ink-3 hover:text-ink')}
        >
          {i.label}
          {i.count != null ? <span className="ml-1.5 text-xs text-ink-4">{i.count}</span> : null}
        </button>
      ))}
    </div>
  );
}

export function Table({ head, children, empty }: { head: ReactNode[]; children: ReactNode; empty?: boolean }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-left text-sm">
        <thead>
          <tr className="border-b border-line text-xs font-semibold uppercase tracking-wide text-ink-3">
            {head.map((h, i) => (
              <th key={i} className="whitespace-nowrap px-4 py-2.5">
                {h}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-line">{children}</tbody>
      </table>
      {empty ? <div className="px-4 py-10 text-center text-sm text-ink-3">Nothing to show.</div> : null}
    </div>
  );
}

export function Spinner({ label }: { label?: string }) {
  return (
    <div className="flex items-center justify-center gap-3 py-16 text-sm text-ink-3">
      <span className="h-5 w-5 animate-spin rounded-full border-2 border-ink border-t-transparent" />
      {label ?? 'Loading…'}
    </div>
  );
}

export function ErrorNote({ error }: { error: unknown }) {
  if (!error) return null;
  const e = error instanceof ApiError ? error : toApiError(error, API_URL);
  return <div className="rounded-lg bg-[#FDECEC] px-4 py-3 text-sm text-[#B42318]">{e.message}</div>;
}

export function Modal({ open, title, children, onClose, footer }: { open: boolean; title: string; children: ReactNode; onClose: () => void; footer?: ReactNode }) {
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open, onClose]);
  if (!open) return null;
  return (
    <div className="fixed inset-0 z-[2000] flex items-center justify-center bg-ink/40 p-4" onClick={onClose}>
      <div className="w-full max-w-md rounded-xl bg-white shadow-xl" onClick={(e) => e.stopPropagation()} role="dialog" aria-label={title}>
        <div className="border-b border-line px-5 py-3.5 text-[15px] font-semibold">{title}</div>
        <div className="p-5">{children}</div>
        {footer ? <div className="flex justify-end gap-2 border-t border-line px-5 py-3">{footer}</div> : null}
      </div>
    </div>
  );
}

/** A modal asking for a reason (or an amount and a note) before an admin action. */
export function ReasonDialog({
  open,
  title,
  label,
  confirm,
  danger,
  withAmount,
  onClose,
  onSubmit,
}: {
  open: boolean;
  title: string;
  label: string;
  confirm: string;
  danger?: boolean;
  withAmount?: boolean;
  onClose: () => void;
  onSubmit: (text: string, amount?: number) => Promise<unknown>;
}) {
  const [text, setText] = useState('');
  const [amount, setAmount] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  useEffect(() => {
    if (open) {
      setText('');
      setAmount('');
      setError(null);
    }
  }, [open]);
  return (
    <Modal
      open={open}
      title={title}
      onClose={onClose}
      footer={
        <>
          <Button onClick={onClose}>Cancel</Button>
          <Button
            variant={danger ? 'danger' : 'primary'}
            loading={busy}
            disabled={!text.trim() || (withAmount && !(Number(amount) > 0))}
            onClick={async () => {
              setBusy(true);
              setError(null);
              try {
                await onSubmit(text.trim(), withAmount ? Number(amount) : undefined);
                onClose();
              } catch (e) {
                setError(e);
              } finally {
                setBusy(false);
              }
            }}
          >
            {confirm}
          </Button>
        </>
      }
    >
      <div className="space-y-3">
        {withAmount ? <Input label="Amount (₹)" type="number" min={1} value={amount} onChange={(e) => setAmount(e.target.value)} /> : null}
        <Input label={label} value={text} onChange={(e) => setText(e.target.value)} autoFocus />
        <ErrorNote error={error} />
      </div>
    </Modal>
  );
}

export function Pager({ page, size, total, onPage }: { page: number; size: number; total: number; onPage: (p: number) => void }) {
  const pages = Math.max(1, Math.ceil(total / size));
  return (
    <div className="flex items-center justify-between border-t border-line px-4 py-3 text-sm text-ink-3">
      <span className="num">
        {total === 0 ? '0' : `${page * size + 1}–${Math.min(total, (page + 1) * size)}`} of {total.toLocaleString('en-IN')}
      </span>
      <div className="flex gap-2">
        <Button size="sm" disabled={page === 0} onClick={() => onPage(page - 1)}>
          Previous
        </Button>
        <Button size="sm" disabled={page + 1 >= pages} onClick={() => onPage(page + 1)}>
          Next
        </Button>
      </div>
    </div>
  );
}

export function Stars({ value }: { value: number | null }) {
  if (value == null) return <span className="text-ink-4">—</span>;
  return (
    <span className="num whitespace-nowrap text-ink-2" aria-label={`${value} out of 5`}>
      <span className="text-[#F59E0B]">★</span> {Number(value).toFixed(1)}
    </span>
  );
}
