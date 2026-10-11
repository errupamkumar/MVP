import { fmt } from '@ecoride/core';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';

import { Card, ErrorNote, Input, PageHeader, Pager, RideStatusBadge, Select, SimTag, Table } from '@/components/ui';
import { api } from '@/lib/api';

const STATUSES = ['', 'SEARCHING', 'ACCEPTED', 'ARRIVED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'NO_DRIVERS'];
const TYPES = ['', 'BIKE', 'AUTO', 'MINI', 'SEDAN', 'SUV'];

export default function Rides() {
  const [params, setParams] = useSearchParams();
  const [q, setQ] = useState(params.get('q') ?? '');
  const status = params.get('status') ?? '';
  const vehicleType = params.get('type') ?? '';
  const page = Number(params.get('page') ?? 0);
  const set = (k: string, v: string) => {
    const next = new URLSearchParams(params);
    if (v) next.set(k, v);
    else next.delete(k);
    if (k !== 'page') next.delete('page');
    setParams(next);
  };
  const { data, error } = useQuery({
    queryKey: ['rides', params.toString()],
    queryFn: () => api.rides({ q: params.get('q') || undefined, status: status || undefined, vehicleType: vehicleType || undefined, page, size: 25 }),
    placeholderData: keepPreviousData,
    refetchInterval: 10000,
  });

  return (
    <div>
      <PageHeader title="Rides" subtitle="Every request, from search to receipt." />
      <Card pad={false}>
        <form
          className="flex flex-wrap items-end gap-3 border-b border-line p-4"
          onSubmit={(e) => {
            e.preventDefault();
            set('q', q.trim());
          }}
        >
          <Input label="Search" placeholder="Ride code, rider, driver or place" value={q} onChange={(e) => setQ(e.target.value)} className="min-w-[260px] flex-1" />
          <Select label="Status" value={status} onChange={(v) => set('status', v)} options={STATUSES.map((s) => ({ value: s, label: s ? fmt.STATUS_LABEL[s as keyof typeof fmt.STATUS_LABEL] : 'All statuses' }))} className="w-48" />
          <Select label="Ride type" value={vehicleType} onChange={(v) => set('type', v)} options={TYPES.map((t) => ({ value: t, label: t || 'All types' }))} className="w-36" />
        </form>
        <ErrorNote error={error} />
        <Table head={['Ride', 'Status', 'Rider', 'Driver', 'Pickup → drop', 'Type', 'Distance', 'Fare', 'Payment']} empty={data?.items.length === 0}>
          {(data?.items ?? []).map((r) => (
            <tr key={r.id} className="hover:bg-canvas">
              <td className="px-4 py-2.5">
                <Link to={`/rides/${r.id}`} className="font-semibold hover:underline">
                  {r.code}
                </Link>
                <div className="text-xs text-ink-3">{fmt.dateTime(r.requestedAt)}</div>
              </td>
              <td className="px-4 py-2.5"><RideStatusBadge status={r.status} /></td>
              <td className="whitespace-nowrap px-4 py-2.5">{r.riderName}<SimTag on={r.simulated} /></td>
              <td className="whitespace-nowrap px-4 py-2.5">{r.driverName ?? '—'}</td>
              <td className="max-w-[280px] truncate px-4 py-2.5 text-ink-2">{fmt.shortPlace(r.pickupAddress)} → {fmt.shortPlace(r.dropAddress)}</td>
              <td className="px-4 py-2.5">{r.vehicleType}</td>
              <td className="num px-4 py-2.5">{r.distanceKm} km</td>
              <td className="num px-4 py-2.5 font-semibold">{r.fare ? fmt.rupees(r.fare) : '—'}</td>
              <td className="px-4 py-2.5 text-ink-2">{r.paymentMethod}{r.paymentStatus === 'PENDING' && r.status === 'COMPLETED' ? ' · due' : ''}</td>
            </tr>
          ))}
        </Table>
        {data ? <Pager page={data.page} size={data.size} total={data.total} onPage={(p) => set('page', String(p))} /> : null}
      </Card>
    </div>
  );
}
