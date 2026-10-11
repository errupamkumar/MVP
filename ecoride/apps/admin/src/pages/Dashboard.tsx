import { fmt } from '@ecoride/core';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';

import { BarList, ColumnChart, SERIES } from '@/components/charts';
import { Badge, Card, DriverStatusBadge, ErrorNote, PageHeader, RideStatusBadge, SimTag, Spinner, StatTile, Table } from '@/components/ui';
import { api } from '@/lib/api';

export default function Dashboard() {
  const { data, error } = useQuery({ queryKey: ['dashboard'], queryFn: api.dashboard, refetchInterval: 10000 });
  if (error && !data) return <ErrorNote error={error} />;
  if (!data) return <Spinner />;
  const k = data.kpis;
  const days = data.days.map((d) => ({
    label: new Date(`${d.date}T00:00:00`).toLocaleDateString('en-IN', { day: 'numeric' }),
    title: new Date(`${d.date}T00:00:00`).toLocaleDateString('en-IN', { weekday: 'short', day: 'numeric', month: 'short' }),
    values: { completed: d.completed, cancelled: d.cancelled, gmv: Number(d.gmv) },
  }));
  const nowHour = new Date().getHours();

  return (
    <div>
      <PageHeader
        title="Dashboard"
        subtitle={`Today in Pune · updated ${new Date().toLocaleTimeString('en-IN', { hour: 'numeric', minute: '2-digit' })}`}
        actions={
          <>
            <Link to="/live" className="rounded-lg bg-ink px-4 py-2 text-sm font-semibold text-white hover:bg-ink-2">
              Open live map
            </Link>
          </>
        }
      />

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatTile accent label="Gross bookings today" value={fmt.rupees(k.gmvToday)} sub={`${fmt.count(k.completedToday, 'trip')} · avg fare ${fmt.rupees(k.avgFareToday)}`} />
        <StatTile label="Platform commission today" value={fmt.rupees(k.commissionToday)} sub="20% of fares before tax" />
        <StatTile label="Rides right now" value={k.activeNow + k.searchingNow} sub={`${k.activeNow} with a driver · ${k.searchingNow} searching`} />
        <StatTile label="Drivers online" value={k.onlineDrivers + k.onTripDrivers} sub={`${k.onTripDrivers} on a trip · ${k.onlineDrivers} free`} />
        <StatTile label="Rides requested today" value={k.ridesToday} sub={`${k.cancelledToday} cancelled or unserved (${k.cancelRateToday ?? 0}%)`} />
        <StatTile label="Average pickup time" value={k.avgPickupMinToday != null ? `${k.avgPickupMinToday} min` : '—'} sub="Accept to arrival, today" />
        <StatTile label="Driver rating today" value={k.avgRatingToday != null ? `★ ${k.avgRatingToday}` : '—'} sub={`${k.riders.toLocaleString('en-IN')} riders · ${k.newRidersToday} new today`} />
        <StatTile label="Needs attention" value={k.openSos + k.pendingDrivers} sub={`${k.openSos} open SOS · ${k.pendingDrivers} driver applications`} />
      </div>

      <div className="mt-6 grid gap-6 xl:grid-cols-3">
        <Card title="Rides, last 14 days" className="xl:col-span-2">
          <ColumnChart
            data={days}
            series={[
              { key: 'completed', label: 'Completed', color: SERIES.blue },
              { key: 'cancelled', label: 'Cancelled or no driver', color: SERIES.orange },
            ]}
          />
        </Card>
        <Card title="Gross bookings by ride type, 30 days">
          <BarList items={data.byType.map((t) => ({ key: t.vehicleType, label: `${t.name} · ${fmt.count(t.rides, 'trip')}`, value: Number(t.gmv) }))} format={(v) => fmt.rupees(v)} />
        </Card>
      </div>

      <div className="mt-6 grid gap-6 xl:grid-cols-3">
        <Card title="Gross bookings per day (₹)" className="xl:col-span-2">
          <ColumnChart data={days.map((d) => ({ ...d, values: { gmv: d.values.gmv } }))} series={[{ key: 'gmv', label: 'Gross bookings', color: SERIES.blue }]} format={(v) => (v >= 1000 ? `₹${Math.round(v / 1000)}k` : `₹${Math.round(v)}`)} highlightLast />
        </Card>
        <Card title="Requests by hour, today">
          <ColumnChart
            data={data.hours.map((h) => ({ label: String(h.hour), title: `${h.hour}:00–${h.hour}:59`, values: { rides: h.hour <= nowHour ? h.rides : 0 } }))}
            series={[{ key: 'rides', label: 'Requests', color: SERIES.blue }]}
            labelEvery={4}
            height={200}
          />
        </Card>
      </div>

      <div className="mt-6 grid gap-6 xl:grid-cols-3">
        <Card title="Latest rides" className="xl:col-span-2" pad={false} action={<Link to="/rides" className="text-sm font-semibold text-brand">All rides →</Link>}>
          <Table head={['Ride', 'Status', 'Rider', 'Driver', 'Route', 'Fare']}>
            {data.recent.map((r) => (
              <tr key={r.id} className="hover:bg-canvas">
                <td className="px-4 py-2.5">
                  <Link to={`/rides/${r.id}`} className="font-semibold text-ink hover:underline">
                    {r.code}
                  </Link>
                  <div className="text-xs text-ink-3">{fmt.time(r.requestedAt)}</div>
                </td>
                <td className="px-4 py-2.5"><RideStatusBadge status={r.status} /></td>
                <td className="px-4 py-2.5">{r.riderName}<SimTag on={r.simulated} /></td>
                <td className="px-4 py-2.5">{r.driverName ?? '—'}</td>
                <td className="max-w-[260px] truncate px-4 py-2.5 text-ink-2">{fmt.shortPlace(r.pickupAddress)} → {fmt.shortPlace(r.dropAddress)}</td>
                <td className="num px-4 py-2.5 font-semibold">{r.fare ? fmt.rupees(r.fare) : '—'}</td>
              </tr>
            ))}
          </Table>
        </Card>
        <div className="space-y-6">
          <Card title="Driver applications" pad={false} action={<Link to="/drivers?status=PENDING_APPROVAL" className="text-sm font-semibold text-brand">Review →</Link>}>
            {data.pendingDrivers.length ? (
              <ul className="divide-y divide-line">
                {data.pendingDrivers.map((d) => (
                  <li key={d.id} className="flex items-center justify-between gap-3 px-5 py-3">
                    <div>
                      <Link to={`/drivers/${d.id}`} className="font-semibold hover:underline">{d.name}</Link>
                      <div className="text-xs text-ink-3">{d.vehicle} · {d.plate}</div>
                    </div>
                    <DriverStatusBadge status={d.status} />
                  </li>
                ))}
              </ul>
            ) : (
              <p className="px-5 py-6 text-sm text-ink-3">No applications waiting.</p>
            )}
          </Card>
          <Card title="Open SOS alerts" pad={false} action={<Link to="/safety" className="text-sm font-semibold text-brand">Safety →</Link>}>
            {data.openSos.length ? (
              <ul className="divide-y divide-line">
                {data.openSos.map((s) => (
                  <li key={s.id} className="px-5 py-3">
                    <div className="flex items-center gap-2">
                      <Badge tone="danger" icon="⚠">SOS</Badge>
                      <Link to={`/rides/${s.rideId}`} className="font-semibold hover:underline">{s.rideCode}</Link>
                    </div>
                    <div className="mt-1 text-xs text-ink-3">Raised by the {s.raisedBy.toLowerCase()} · {fmt.ago(s.createdAt)}</div>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="px-5 py-6 text-sm text-ink-3">No open alerts.</p>
            )}
          </Card>
        </div>
      </div>
    </div>
  );
}
