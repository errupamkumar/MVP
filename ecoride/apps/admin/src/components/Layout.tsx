import { useQuery } from '@tanstack/react-query';
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';

import { api } from '@/lib/api';
import { useAuth } from '@/lib/auth';

import { cx } from './ui';

const NAV: { to: string; label: string; icon: string; section?: string }[] = [
  { to: '/', label: 'Dashboard', icon: '▦', section: 'Operations' },
  { to: '/live', label: 'Live map', icon: '◉' },
  { to: '/rides', label: 'Rides', icon: '⇄' },
  { to: '/safety', label: 'Safety (SOS)', icon: '⚠' },
  { to: '/drivers', label: 'Drivers', icon: '◎', section: 'People' },
  { to: '/riders', label: 'Riders', icon: '☺' },
  { to: '/pricing', label: 'Fares', icon: '₹', section: 'Pricing' },
  { to: '/surge', label: 'Surge zones', icon: '⤴' },
  { to: '/promos', label: 'Promotions', icon: '%' },
  { to: '/finance', label: 'Finance', icon: '∑', section: 'Business' },
  { to: '/settings', label: 'Settings', icon: '⚙' },
  { to: '/audit', label: 'Audit log', icon: '☰' },
];

export function Logo({ light = false }: { light?: boolean }) {
  return (
    <div className="flex items-center gap-2.5">
      <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-brand">
        <svg viewBox="0 0 32 32" className="h-6 w-6">
          <path d="M22.5 8.5c-7.5.3-12.8 3.9-13.9 10.2-.3 1.6-.2 3.2.2 4.8 1.4-3.9 4.2-6.9 8-8.6-3.1 2.1-5.3 5.2-6.3 8.9 1 .4 2.1.6 3.2.6 6.2 0 9.4-6.1 8.8-15.9z" fill="#fff" />
        </svg>
      </div>
      <div className={cx('text-lg font-extrabold tracking-tight', light ? 'text-white' : 'text-ink')}>
        EcoRide <span className={cx('font-medium', light ? 'text-ink-4' : 'text-ink-3')}>Admin</span>
      </div>
    </div>
  );
}

export function Layout() {
  const { session, signOut } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const sos = useQuery({ queryKey: ['sos-count'], queryFn: api.sos, refetchInterval: 10000, select: (rows) => rows.filter((r) => r.status === 'OPEN').length });
  const pending = useQuery({ queryKey: ['driver-counts'], queryFn: api.driverCounts, refetchInterval: 15000 });
  const badge = (to: string) =>
    to === '/safety' && sos.data ? sos.data : to === '/drivers' && pending.data?.PENDING_APPROVAL ? pending.data.PENDING_APPROVAL : null;

  return (
    <div className="flex h-full">
      <aside className="hidden w-60 shrink-0 flex-col bg-ink px-3 py-5 text-sm md:flex">
        <div className="px-2">
          <Logo light />
        </div>
        <nav className="mt-6 flex-1 space-y-0.5 overflow-y-auto">
          {NAV.map((n) => (
            <div key={n.to}>
              {n.section ? <div className="mb-1 mt-5 px-3 text-[11px] font-semibold uppercase tracking-wider text-ink-3 first:mt-0">{n.section}</div> : null}
              <NavLink
                to={n.to}
                end={n.to === '/'}
                className={({ isActive }) =>
                  cx('flex items-center gap-3 rounded-lg px-3 py-2 font-medium transition', isActive ? 'bg-white/10 text-white' : 'text-ink-4 hover:bg-white/5 hover:text-white')
                }
              >
                <span className="w-4 text-center text-base leading-none opacity-80">{n.icon}</span>
                <span className="flex-1">{n.label}</span>
                {badge(n.to) ? <span className={cx('rounded-full px-1.5 text-[11px] font-bold', n.to === '/safety' ? 'bg-critical text-white' : 'bg-warning text-ink')}>{badge(n.to)}</span> : null}
              </NavLink>
            </div>
          ))}
        </nav>
        <div className="border-t border-white/10 px-3 pt-4">
          <div className="text-sm font-semibold text-white">{session?.admin.name}</div>
          <div className="text-xs text-ink-4">{session?.admin.email}</div>
          <button className="mt-2 text-xs font-semibold text-ink-4 hover:text-white" onClick={() => signOut()}>
            Sign out
          </button>
        </div>
      </aside>
      <main className="flex-1 overflow-y-auto">
        <div className="sticky top-0 z-[1000] flex items-center justify-between border-b border-line bg-white/90 px-4 py-3 backdrop-blur md:hidden">
          <Logo />
          <select className="rounded-md border border-line px-2 py-1 text-sm" value={location.pathname} onChange={(e) => navigate(e.target.value)}>
            {NAV.map((n) => (
              <option key={n.to} value={n.to}>
                {n.label}
              </option>
            ))}
          </select>
        </div>
        <div className="mx-auto max-w-[1400px] px-4 py-6 md:px-8 md:py-8">
          <Outlet />
        </div>
      </main>
    </div>
  );
}
