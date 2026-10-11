import { fmt } from '@ecoride/core';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link } from 'react-router-dom';

import { BarList, ColumnChart, SERIES } from '@/components/charts';
import { Badge, Button, Card, ErrorNote, PageHeader, Pager, SimTag, Spinner, StatTile, Table, Tabs } from '@/components/ui';
import { api } from '@/lib/api';

export default function Finance() {
  const queryClient = useQueryClient();
  const [days, setDays] = useState('30');
  const [page, setPage] = useState(0);
  const { data, error } = useQuery({ queryKey: ['finance', days], queryFn: () => api.finance(Number(days)) });
  const payments = useQuery({ queryKey: ['payments', page], queryFn: () => api.payments(page) }).data;
  const [paying, setPaying] = useState<number | null>(null);
  if (error && !data) return <ErrorNote error={error} />;
  if (!data) return <Spinner />;
  const t = data.totals;

  return (
    <div>
      <PageHeader
        title="Finance"
        subtitle="Completed trips in the period. Commission is 20% of the fare before tax; promos are funded by EcoRide."
        actions={<Tabs value={days} onChange={setDays} items={[{ value: '7', label: '7 days' }, { value: '30', label: '30 days' }, { value: '90', label: '90 days' }]} />}
      />
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatTile accent label="Gross bookings" value={fmt.rupees(t.gmv)} sub={fmt.count(t.trips, 'trip')} />
        <StatTile label="Driver earnings" value={fmt.rupees(t.driverEarnings)} sub={`+ ${fmt.rupees(t.tips)} in tips`} />
        <StatTile label="EcoRide net revenue" value={fmt.rupees(t.platformNet)} sub={`${fmt.rupees(t.commission)} commission − ${fmt.rupees(t.discounts)} promos`} />
        <StatTile label="GST collected" value={fmt.rupees(t.tax)} sub={`Cancellation fees ${fmt.rupees(t.cancellationFees)}`} />
      </div>

      <div className="mt-6 grid gap-6 xl:grid-cols-3">
        <Card title="Gross bookings per day (₹)" className="xl:col-span-2">
          <ColumnChart
            data={data.daily.map((d) => ({
              label: new Date(`${d.date}T00:00:00`).toLocaleDateString('en-IN', { day: 'numeric' }),
              title: new Date(`${d.date}T00:00:00`).toLocaleDateString('en-IN', { weekday: 'short', day: 'numeric', month: 'short' }),
              values: { gmv: Number(d.gmv) },
            }))}
            series={[{ key: 'gmv', label: 'Gross bookings', color: SERIES.blue }]}
            format={(v) => (v >= 1000 ? `₹${Math.round(v / 1000)}k` : `₹${Math.round(v)}`)}
            labelEvery={data.daily.length > 31 ? 7 : data.daily.length > 14 ? 3 : 1}
          />
        </Card>
        <Card title="How riders paid">
          <BarList items={data.byMethod.map((m) => ({ key: m.method, label: `${fmt.PAYMENT_LABEL[m.method as keyof typeof fmt.PAYMENT_LABEL] ?? m.method} · ${fmt.count(m.trips, 'trip')}`, value: Number(m.amount) }))} format={(v) => fmt.rupees(v)} />
          <div className="mt-5 border-t border-line pt-4">
            <div className="mb-3 text-xs font-semibold uppercase tracking-wide text-ink-3">By ride type</div>
            <BarList items={data.byType.map((m) => ({ key: m.vehicleType, label: m.name, value: Number(m.gmv) }))} format={(v) => fmt.rupees(v)} />
          </div>
        </Card>
      </div>

      <div className="mt-6 grid gap-6 xl:grid-cols-2">
        <Card title="Driver payouts" pad={false}>
          <Table head={['Driver', 'This week', 'Balance', '']}>
            {data.payouts.map((p) => (
              <tr key={p.driverId}>
                <td className="px-4 py-2.5"><Link to={`/drivers/${p.driverId}`} className="font-semibold hover:underline">{p.name}</Link><SimTag on={p.simulated} /><div className="text-xs text-ink-3">{p.plate}</div></td>
                <td className="num px-4 py-2.5">{fmt.rupees(p.earnedThisWeek)} <span className="text-ink-3">· {fmt.count(p.tripsThisWeek, 'trip')}</span></td>
                <td className={`num px-4 py-2.5 font-semibold ${p.balance < 0 ? 'text-critical' : ''}`}>{fmt.rupees(p.balance, { decimals: true })}</td>
                <td className="px-4 py-2.5 text-right">
                  {p.balance > 0 ? (
                    <Button size="sm" loading={paying === p.driverId} onClick={async () => {
                      setPaying(p.driverId);
                      try {
                        await api.driverAction(p.driverId, 'payout');
                        await queryClient.invalidateQueries({ queryKey: ['finance'] });
                      } finally {
                        setPaying(null);
                      }
                    }}>Pay out</Button>
                  ) : p.balance < 0 ? <Badge tone="warn">Owes cash commission</Badge> : null}
                </td>
              </tr>
            ))}
          </Table>
        </Card>
        <Card title="Payments" pad={false}>
          <Table head={['When', 'Ride', 'Kind', 'Method', 'Amount', 'Status']}>
            {(payments?.items ?? []).map((p) => (
              <tr key={p.id}>
                <td className="whitespace-nowrap px-4 py-2 text-ink-3">{fmt.dateTime(p.createdAt)}</td>
                <td className="px-4 py-2"><Link to={`/rides/${p.rideId}`} className="font-semibold hover:underline">{p.rideCode}</Link><div className="text-xs text-ink-3">{p.riderName}</div></td>
                <td className="px-4 py-2">{p.kind.replace('_', ' ').toLowerCase()}</td>
                <td className="px-4 py-2">{p.method}</td>
                <td className="num px-4 py-2 font-semibold">{fmt.rupees(p.amount, { decimals: true })}</td>
                <td className="px-4 py-2"><Badge tone={p.status === 'PAID' ? 'brand' : p.status === 'REFUNDED' ? 'info' : 'warn'}>{p.status.toLowerCase()}</Badge></td>
              </tr>
            ))}
          </Table>
          {payments ? <Pager page={payments.page} size={payments.size} total={payments.total} onPage={setPage} /> : null}
        </Card>
      </div>
    </div>
  );
}
