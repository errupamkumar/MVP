import { fmt } from '@ecoride/core';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';

import { Badge, Button, Card, ErrorNote, PageHeader, ReasonDialog, RideStatusBadge, Spinner, StatTile, Table } from '@/components/ui';
import { api } from '@/lib/api';

export default function RiderDetail() {
  const id = Number(useParams().id);
  const queryClient = useQueryClient();
  const { data: d, error } = useQuery({ queryKey: ['rider', id], queryFn: () => api.rider(id) });
  const [dialog, setDialog] = useState<'block' | 'credit' | null>(null);
  if (error && !d) return <ErrorNote error={error} />;
  if (!d) return <Spinner />;
  const r = d.row;
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['rider', id] });

  return (
    <div>
      <PageHeader
        title={r.name}
        subtitle={
          <span className="flex flex-wrap items-center gap-2">
            {r.status === 'ACTIVE' ? <Badge tone="brand" icon="✓">Active</Badge> : <Badge tone="danger" icon="⛔">Blocked</Badge>}
            {r.simulated ? <Badge>Simulated rider</Badge> : null}
            <span>{fmt.phone(r.phone)}{r.email ? ` · ${r.email}` : ''}</span>
          </span>
        }
        actions={
          <>
            <Link to="/riders" className="text-sm font-semibold text-ink-3 hover:text-ink">← All riders</Link>
            <Button onClick={() => setDialog('credit')}>Credit wallet</Button>
            {r.status === 'ACTIVE' ? (
              <Button variant="danger" onClick={() => setDialog('block')}>Block rider</Button>
            ) : (
              <Button variant="brand" onClick={async () => { await api.unblockRider(id); await refresh(); }}>Unblock</Button>
            )}
          </>
        }
      />
      {d.statusReason ? <div className="mb-6 rounded-xl bg-[#FDECEC] px-5 py-4 text-sm text-[#B42318]">Blocked: {d.statusReason}</div> : null}
      <div className="grid gap-4 sm:grid-cols-4">
        <StatTile label="Completed rides" value={r.rides} />
        <StatTile label="Wallet balance" value={fmt.rupees(r.wallet, { decimals: true })} />
        <StatTile label="Rider rating" value={r.ratingCount ? `★ ${fmt.rating(r.rating)}` : '—'} sub={`${fmt.count(r.ratingCount, 'rating')} from drivers`} />
        <StatTile label="Member since" value={fmt.date(r.createdAt)} sub={r.lastLoginAt ? `Last sign-in ${fmt.ago(r.lastLoginAt)}` : undefined} />
      </div>
      <div className="mt-6 grid gap-6 xl:grid-cols-[1fr_380px]">
        <Card title="Recent rides" pad={false}>
          <Table head={['Ride', 'Status', 'Driver', 'Route', 'Fare', 'Payment']} empty={!d.recentRides.length}>
            {d.recentRides.map((x) => (
              <tr key={x.id} className="hover:bg-canvas">
                <td className="px-4 py-2.5"><Link to={`/rides/${x.id}`} className="font-semibold hover:underline">{x.code}</Link><div className="text-xs text-ink-3">{fmt.dateTime(x.requestedAt)}</div></td>
                <td className="px-4 py-2.5"><RideStatusBadge status={x.status} /></td>
                <td className="px-4 py-2.5">{x.driverName ?? '—'}</td>
                <td className="max-w-[260px] truncate px-4 py-2.5 text-ink-2">{fmt.shortPlace(x.pickupAddress)} → {fmt.shortPlace(x.dropAddress)}</td>
                <td className="num px-4 py-2.5 font-semibold">{x.fare ? fmt.rupees(x.fare) : '—'}</td>
                <td className="px-4 py-2.5 text-ink-2">{x.paymentMethod}</td>
              </tr>
            ))}
          </Table>
        </Card>
        <div className="space-y-6">
          <Card title="Wallet statement" pad={false}>
            <ul className="divide-y divide-line">
              {d.wallet.map((l) => (
                <li key={l.id} className="flex items-center justify-between gap-3 px-5 py-2.5 text-sm">
                  <div>
                    <div className="font-medium">{l.note}</div>
                    <div className="text-xs text-ink-3">{fmt.dateTime(l.at)}</div>
                  </div>
                  <div className="num text-right font-semibold">{l.amount >= 0 ? '+' : ''}{fmt.rupees(l.amount, { decimals: true })}</div>
                </li>
              ))}
              {!d.wallet.length ? <li className="px-5 py-5 text-sm text-ink-3">No wallet activity.</li> : null}
            </ul>
          </Card>
          <Card title="Saved places">
            {d.places.length ? (
              <ul className="space-y-2 text-sm">
                {d.places.map((p) => (
                  <li key={p.id}><span className="font-semibold">{p.label === 'OTHER' ? p.name : p.label.toLowerCase()}</span> · <span className="text-ink-2">{p.address}</span></li>
                ))}
              </ul>
            ) : (
              <p className="text-sm text-ink-3">None.</p>
            )}
          </Card>
        </div>
      </div>
      <ReasonDialog open={dialog === 'block'} title={`Block ${r.name}`} label="Reason" confirm="Block rider" danger onClose={() => setDialog(null)} onSubmit={async (reason) => { await api.blockRider(id, reason); await refresh(); }} />
      <ReasonDialog open={dialog === 'credit'} title={`Credit ${r.name}'s wallet`} label="Note (shown on the statement)" confirm="Credit wallet" withAmount onClose={() => setDialog(null)} onSubmit={async (note, amount) => { await api.creditWallet(id, amount ?? 0, note); await refresh(); }} />
    </div>
  );
}
