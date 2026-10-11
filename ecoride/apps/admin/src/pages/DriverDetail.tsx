import { fmt } from '@ecoride/core';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Marker } from 'react-leaflet';
import { Link, useParams } from 'react-router-dom';

import { AVAILABILITY_COLOR, carIcon, MapCanvas } from '@/components/map';
import { AvailabilityBadge, Badge, Button, Card, DriverStatusBadge, ErrorNote, KeyValue, PageHeader, ReasonDialog, RideStatusBadge, Spinner, StatTile, Table } from '@/components/ui';
import { api } from '@/lib/api';

export default function DriverDetail() {
  const id = Number(useParams().id);
  const queryClient = useQueryClient();
  const { data: d, error } = useQuery({ queryKey: ['driver', id], queryFn: () => api.driver(id), refetchInterval: 8000 });
  const [dialog, setDialog] = useState<'reject' | 'suspend' | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [actionError, setActionError] = useState<unknown>(null);
  if (error && !d) return <ErrorNote error={error} />;
  if (!d) return <Spinner />;
  const r = d.row;

  const run = async (key: string, fn: () => Promise<unknown>) => {
    setBusy(key);
    setActionError(null);
    try {
      await fn();
      await queryClient.invalidateQueries({ queryKey: ['driver', id] });
      void queryClient.invalidateQueries({ queryKey: ['driver-counts'] });
    } catch (e) {
      setActionError(e);
    } finally {
      setBusy(null);
    }
  };

  return (
    <div>
      <PageHeader
        title={r.name}
        subtitle={
          <span className="flex flex-wrap items-center gap-2">
            <DriverStatusBadge status={r.status} /> <AvailabilityBadge value={r.availability} /> {r.simulated ? <Badge>Simulated driver</Badge> : null}
            <span>{fmt.phone(r.phone)}{d.email ? ` · ${d.email}` : ''}</span>
          </span>
        }
        actions={
          <>
            <Link to="/drivers" className="text-sm font-semibold text-ink-3 hover:text-ink">← All drivers</Link>
            {r.status === 'PENDING_APPROVAL' ? (
              <>
                <Button variant="brand" loading={busy === 'approve'} onClick={() => run('approve', () => api.driverAction(id, 'approve'))}>Approve driver</Button>
                <Button variant="danger" onClick={() => setDialog('reject')}>Reject</Button>
              </>
            ) : null}
            {r.status === 'APPROVED' ? <Button variant="danger" onClick={() => setDialog('suspend')}>Suspend</Button> : null}
            {r.status === 'SUSPENDED' || r.status === 'REJECTED' ? <Button variant="brand" loading={busy === 'reactivate'} onClick={() => run('reactivate', () => api.driverAction(id, 'reactivate'))}>Reactivate</Button> : null}
            {r.availability === 'ONLINE' ? <Button loading={busy === 'offline'} onClick={() => run('offline', () => api.driverAction(id, 'offline'))}>Take offline</Button> : null}
            {r.balance > 0 ? <Button variant="primary" loading={busy === 'payout'} onClick={() => run('payout', () => api.driverAction(id, 'payout'))}>Pay out {fmt.rupees(r.balance)}</Button> : null}
          </>
        }
      />
      {actionError ? <div className="mb-4"><ErrorNote error={actionError} /></div> : null}
      {r.status === 'PENDING_APPROVAL' ? (
        <div className="mb-6 rounded-xl border border-[#F5D68A] bg-[#FFFBEB] px-5 py-4 text-sm text-[#92400E]">
          <strong>Application waiting for review.</strong> Check the driving licence <strong>{d.licenseNo}</strong> and the registration <strong>{r.plate}</strong> ({r.vehicle}, {d.vehicleYear}), then approve to let {r.name.split(' ')[0]} go online.
        </div>
      ) : null}
      {d.statusReason ? <div className="mb-6 rounded-xl bg-[#FDECEC] px-5 py-4 text-sm text-[#B42318]">{d.statusReason}</div> : null}

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatTile label="Earned this week" value={fmt.rupees(d.earnedThisWeek)} sub={fmt.count(d.tripsThisWeek, 'trip')} />
        <StatTile label="Payout balance" value={fmt.rupees(r.balance, { decimals: true })} sub="Paid every Monday" />
        <StatTile label="Rating" value={r.ratingCount ? `★ ${fmt.rating(r.rating)}` : '—'} sub={`${fmt.count(r.ratingCount, 'rating')} · ${fmt.count(r.trips, 'trip')}`} />
        <StatTile label="Acceptance rate" value={r.acceptanceRate != null ? `${r.acceptanceRate}%` : '—'} sub="Requests accepted" />
      </div>

      <div className="mt-6 grid gap-6 xl:grid-cols-[1fr_380px]">
        <div className="space-y-6">
          <Card title="Recent rides" pad={false}>
            <Table head={['Ride', 'Status', 'Rider', 'Route', 'Fare']} empty={!d.recentRides.length}>
              {d.recentRides.map((x) => (
                <tr key={x.id} className="hover:bg-canvas">
                  <td className="px-4 py-2.5"><Link to={`/rides/${x.id}`} className="font-semibold hover:underline">{x.code}</Link><div className="text-xs text-ink-3">{fmt.dateTime(x.requestedAt)}</div></td>
                  <td className="px-4 py-2.5"><RideStatusBadge status={x.status} /></td>
                  <td className="px-4 py-2.5">{x.riderName}</td>
                  <td className="max-w-[260px] truncate px-4 py-2.5 text-ink-2">{fmt.shortPlace(x.pickupAddress)} → {fmt.shortPlace(x.dropAddress)}</td>
                  <td className="num px-4 py-2.5 font-semibold">{x.fare ? fmt.rupees(x.fare) : '—'}</td>
                </tr>
              ))}
            </Table>
          </Card>
          <Card title="Earnings ledger" pad={false}>
            <Table head={['When', 'Type', 'Note', 'Amount']} empty={!d.ledger.length}>
              {d.ledger.map((l) => (
                <tr key={l.id}>
                  <td className="whitespace-nowrap px-4 py-2 text-ink-3">{fmt.dateTime(l.at)}</td>
                  <td className="px-4 py-2"><Badge tone={l.amount >= 0 ? 'brand' : 'neutral'}>{l.type.replace('_', ' ').toLowerCase()}</Badge></td>
                  <td className="px-4 py-2 text-ink-2">{l.note}</td>
                  <td className="num px-4 py-2 text-right font-semibold">{fmt.rupees(l.amount, { decimals: true })}</td>
                </tr>
              ))}
            </Table>
          </Card>
        </div>
        <div className="space-y-6">
          <Card title="Vehicle and documents">
            <KeyValue label="Vehicle">{r.vehicle}</KeyValue>
            <KeyValue label="Ride type">{r.vehicleType}</KeyValue>
            <KeyValue label="Registration">{r.plate}</KeyValue>
            <KeyValue label="Year">{d.vehicleYear}</KeyValue>
            <KeyValue label="Driving licence">{d.licenseNo}</KeyValue>
            <KeyValue label="Applied">{fmt.dateTime(r.createdAt)}</KeyValue>
            <KeyValue label="Approved">{d.approvedAt ? fmt.date(d.approvedAt) : '—'}</KeyValue>
            <KeyValue label="Last sign-in">{d.lastLoginAt ? fmt.ago(d.lastLoginAt) : '—'}</KeyValue>
          </Card>
          <Card title="Last known position" pad={false}>
            {d.lat != null && d.lng != null ? (
              <MapCanvas height={260} center={{ lat: d.lat, lng: d.lng }} zoom={14}>
                <Marker position={[d.lat, d.lng]} icon={carIcon(r.availability === 'ON_TRIP' ? AVAILABILITY_COLOR.ON_TRIP : AVAILABILITY_COLOR.ONLINE, null, r.name.split(' ')[0], true)} />
              </MapCanvas>
            ) : (
              <p className="px-5 py-6 text-sm text-ink-3">No GPS fix yet.</p>
            )}
            <div className="border-t border-line px-5 py-2 text-xs text-ink-3">GPS updated {r.locatedAt ? fmt.ago(r.locatedAt) : 'never'}</div>
          </Card>
        </div>
      </div>

      <ReasonDialog open={dialog === 'reject'} title={`Reject ${r.name}`} label="Reason (shown to the applicant)" confirm="Reject application" danger onClose={() => setDialog(null)} onSubmit={(reason) => run('reject', () => api.driverReason(id, 'reject', reason))} />
      <ReasonDialog open={dialog === 'suspend'} title={`Suspend ${r.name}`} label="Reason (shown in the driver app)" confirm="Suspend driver" danger onClose={() => setDialog(null)} onSubmit={(reason) => run('suspend', () => api.driverReason(id, 'suspend', reason))} />
    </div>
  );
}
