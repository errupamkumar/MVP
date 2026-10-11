import { fmt } from '@ecoride/core';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';

import { AvailabilityBadge, Card, DriverStatusBadge, ErrorNote, Input, PageHeader, Pager, Select, SimTag, Stars, Table, Tabs } from '@/components/ui';
import { api } from '@/lib/api';

export default function Drivers() {
  const [params, setParams] = useSearchParams();
  const [q, setQ] = useState(params.get('q') ?? '');
  const status = params.get('status') ?? '';
  const availability = params.get('availability') ?? '';
  const page = Number(params.get('page') ?? 0);
  const set = (k: string, v: string) => {
    const next = new URLSearchParams(params);
    if (v) next.set(k, v);
    else next.delete(k);
    if (k !== 'page') next.delete('page');
    setParams(next);
  };
  const counts = useQuery({ queryKey: ['driver-counts'], queryFn: api.driverCounts }).data;
  const { data, error } = useQuery({
    queryKey: ['drivers', params.toString()],
    queryFn: () => api.drivers({ q: params.get('q') || undefined, status: status || undefined, availability: availability || undefined, page, size: 25 }),
    placeholderData: keepPreviousData,
    refetchInterval: 10000,
  });
  const total = counts ? Object.values(counts).reduce((a, b) => a + b, 0) : undefined;

  return (
    <div>
      <PageHeader title="Drivers" subtitle="Applications, compliance and payouts. Approve a driver to let them go online." />
      <Card pad={false}>
        <div className="flex flex-wrap items-end justify-between gap-3 border-b border-line p-4">
          <Tabs
            value={status}
            onChange={(v) => set('status', v)}
            items={[
              { value: '', label: 'All', count: total },
              { value: 'PENDING_APPROVAL', label: 'Pending approval', count: counts?.PENDING_APPROVAL },
              { value: 'APPROVED', label: 'Approved', count: counts?.APPROVED },
              { value: 'SUSPENDED', label: 'Suspended', count: counts?.SUSPENDED },
              { value: 'REJECTED', label: 'Rejected', count: counts?.REJECTED },
            ]}
          />
          <form
            className="flex flex-wrap items-end gap-3"
            onSubmit={(e) => {
              e.preventDefault();
              set('q', q.trim());
            }}
          >
            <Input placeholder="Name, phone or plate" value={q} onChange={(e) => setQ(e.target.value)} className="w-60" />
            <Select value={availability} onChange={(v) => set('availability', v)} options={[{ value: '', label: 'Any availability' }, { value: 'ONLINE', label: 'Online' }, { value: 'ON_TRIP', label: 'On a trip' }, { value: 'OFFLINE', label: 'Offline' }]} className="w-44" />
          </form>
        </div>
        <ErrorNote error={error} />
        <Table head={['Driver', 'Status', 'Now', 'Vehicle', 'Rating', 'Acceptance', 'Trips', 'Payout balance', 'Joined']} empty={data?.items.length === 0}>
          {(data?.items ?? []).map((d) => (
            <tr key={d.id} className="hover:bg-canvas">
              <td className="px-4 py-2.5">
                <Link to={`/drivers/${d.id}`} className="font-semibold hover:underline">{d.name}</Link>
                <SimTag on={d.simulated} />
                <div className="text-xs text-ink-3">{fmt.phone(d.phone)}</div>
              </td>
              <td className="px-4 py-2.5"><DriverStatusBadge status={d.status} /></td>
              <td className="px-4 py-2.5"><AvailabilityBadge value={d.availability} /></td>
              <td className="px-4 py-2.5">
                <div className="whitespace-nowrap">{d.vehicle}</div>
                <div className="text-xs text-ink-3">{d.vehicleType} · {d.plate}</div>
              </td>
              <td className="px-4 py-2.5"><Stars value={d.ratingCount ? d.rating : null} /></td>
              <td className="num px-4 py-2.5">{d.acceptanceRate != null ? `${d.acceptanceRate}%` : '—'}</td>
              <td className="num px-4 py-2.5">{d.trips}</td>
              <td className="num px-4 py-2.5 font-semibold">{fmt.rupees(d.balance)}</td>
              <td className="whitespace-nowrap px-4 py-2.5 text-ink-3">{fmt.date(d.createdAt)}</td>
            </tr>
          ))}
        </Table>
        {data ? <Pager page={data.page} size={data.size} total={data.total} onPage={(p) => set('page', String(p))} /> : null}
      </Card>
    </div>
  );
}
