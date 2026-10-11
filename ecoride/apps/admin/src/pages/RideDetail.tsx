import { decodePolyline, fmt, type FareBreakdown } from '@ecoride/core';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Marker, Polyline } from 'react-leaflet';
import { Link, useParams } from 'react-router-dom';

import { MapCanvas, pointIcon } from '@/components/map';
import { Badge, Button, Card, ErrorNote, KeyValue, PageHeader, ReasonDialog, RideStatusBadge, Spinner, Stars, Table } from '@/components/ui';
import { api } from '@/lib/api';

const ACTIVE = ['SEARCHING', 'ACCEPTED', 'ARRIVED', 'IN_PROGRESS'];

export default function RideDetail() {
  const id = Number(useParams().id);
  const queryClient = useQueryClient();
  const { data: d, error } = useQuery({
    queryKey: ['ride', id],
    queryFn: () => api.ride(id),
    refetchInterval: (q) => (q.state.data && ACTIVE.includes(q.state.data.row.status) ? 3000 : false),
  });
  const [dialog, setDialog] = useState<'cancel' | 'refund' | null>(null);
  if (error && !d) return <ErrorNote error={error} />;
  if (!d) return <Spinner />;

  const route = decodePolyline(d.routePolyline);
  const toPickup = decodePolyline(d.pickupPolyline);
  const pickupTrace = d.track.filter((t) => t.phase === 'PICKUP');
  const tripTrace = d.track.filter((t) => t.phase === 'TRIP');
  const deviation = d.estDistanceM ? Math.round((Math.abs(d.actualDistanceM - d.estDistanceM) / d.estDistanceM) * 100) : 0;
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['ride', id] });
  const paid = d.fare ? d.fare.total + d.tip : d.cancellationFee;

  return (
    <div>
      <PageHeader
        title={`Ride ${d.row.code}`}
        subtitle={
          <span className="flex flex-wrap items-center gap-2">
            <RideStatusBadge status={d.row.status} /> {d.row.vehicleType} · requested {fmt.dateTime(d.row.requestedAt)}
            {d.row.simulated ? <Badge>Simulated rider</Badge> : null}
          </span>
        }
        actions={
          <>
            <Link to="/rides" className="text-sm font-semibold text-ink-3 hover:text-ink">← All rides</Link>
            {ACTIVE.includes(d.row.status) ? (
              <Button variant="danger" onClick={() => setDialog('cancel')}>Cancel ride</Button>
            ) : null}
            {paid > 0 && !ACTIVE.includes(d.row.status) ? <Button onClick={() => setDialog('refund')}>Refund to wallet</Button> : null}
          </>
        }
      />

      <div className="grid gap-6 xl:grid-cols-[1fr_380px]">
        <div className="space-y-6">
          <Card pad={false}>
            <MapCanvas height={440} fit={[d.pickup, d.drop, ...route]} fitKey={`ride-${id}`}>
              <Polyline positions={route.map((p) => [p.lat, p.lng] as [number, number])} pathOptions={{ color: '#9CA3AF', weight: 6, opacity: 0.6 }} />
              {toPickup.length > 1 ? <Polyline positions={toPickup.map((p) => [p.lat, p.lng] as [number, number])} pathOptions={{ color: '#9CA3AF', weight: 4, opacity: 0.5, dashArray: '4 6' }} /> : null}
              {pickupTrace.length > 1 ? <Polyline positions={pickupTrace.map((p) => [p.lat, p.lng] as [number, number])} pathOptions={{ color: '#1baf7a', weight: 3 }} /> : null}
              {tripTrace.length > 1 ? <Polyline positions={tripTrace.map((p) => [p.lat, p.lng] as [number, number])} pathOptions={{ color: '#2a78d6', weight: 3 }} /> : null}
              <Marker position={[d.pickup.lat, d.pickup.lng]} icon={pointIcon('pickup', 'Pickup')} />
              <Marker position={[d.drop.lat, d.drop.lng]} icon={pointIcon('drop', 'Drop')} />
            </MapCanvas>
            <div className="flex flex-wrap gap-x-6 gap-y-2 border-t border-line px-5 py-3 text-xs text-ink-2">
              <span className="inline-flex items-center gap-2"><span className="inline-block h-1.5 w-5 rounded bg-[#9CA3AF]" />Planned route ({d.routeProvider === 'OSRM' ? 'road network' : 'estimate'})</span>
              <span className="inline-flex items-center gap-2"><span className="inline-block h-1.5 w-5 rounded bg-[#1baf7a]" />GPS: driver to pickup</span>
              <span className="inline-flex items-center gap-2"><span className="inline-block h-1.5 w-5 rounded bg-[#2a78d6]" />GPS: trip with rider</span>
              <span className="ml-auto text-ink-3">{d.track.length} GPS points</span>
            </div>
          </Card>

          <div className="grid gap-4 sm:grid-cols-3">
            <Metric label="Planned distance" value={`${(d.estDistanceM / 1000).toFixed(2)} km`} sub={`${Math.round(d.estDurationS / 60)} min with traffic`} />
            <Metric label="GPS distance (trip)" value={d.actualDistanceM ? `${(d.actualDistanceM / 1000).toFixed(2)} km` : '—'} sub={d.actualDistanceM ? `${deviation}% from plan` : 'Not started'} />
            <Metric label="Dead mileage (to pickup)" value={d.pickupTravelM ? `${(d.pickupTravelM / 1000).toFixed(2)} km` : '—'} sub={d.farePolicy ? `Fare: ${d.farePolicy.toLowerCase()}` : 'Fare not final'} />
          </div>

          <Card title="Timeline" pad={false}>
            <ol className="divide-y divide-line">
              {d.timeline.map((e, i) => (
                <li key={i} className="flex gap-4 px-5 py-2.5 text-sm">
                  <span className="num w-24 shrink-0 whitespace-nowrap text-ink-3">{new Date(e.at).toLocaleTimeString('en-IN', { hour: 'numeric', minute: '2-digit', second: '2-digit' })}</span>
                  <span className="w-28 shrink-0"><Badge tone={e.type.includes('CANCEL') || e.type === 'SOS' || e.type === 'NO_DRIVERS' ? 'danger' : e.type === 'COMPLETED' || e.type === 'PAYMENT' ? 'brand' : 'neutral'}>{e.type.replace(/_/g, ' ').toLowerCase()}</Badge></span>
                  <span className="text-ink-2">{e.message}</span>
                </li>
              ))}
            </ol>
          </Card>

          <Card title="Dispatch attempts" pad={false}>
            <Table head={['#', 'Driver', 'Straight-line distance', 'Pickup ETA', 'Outcome', 'Answered in']} empty={!d.offers.length}>
              {d.offers.map((o, i) => (
                <tr key={i}>
                  <td className="px-4 py-2.5 text-ink-3">{i + 1}</td>
                  <td className="px-4 py-2.5">
                    <Link to={`/drivers/${o.driverId}`} className="font-semibold hover:underline">{o.driverName}</Link>
                    {o.simulated ? <span className="ml-1 text-xs text-ink-3">(sim)</span> : null}
                  </td>
                  <td className="num px-4 py-2.5">{fmt.distance(o.distanceM)}</td>
                  <td className="num px-4 py-2.5">{fmt.minutes(o.etaS)}</td>
                  <td className="px-4 py-2.5">
                    <Badge tone={o.status === 'ACCEPTED' ? 'brand' : o.status === 'PENDING' ? 'warn' : 'neutral'}>{o.status.toLowerCase()}</Badge>
                  </td>
                  <td className="num px-4 py-2.5 text-ink-2">{o.respondedAt ? `${Math.round((new Date(o.respondedAt).getTime() - new Date(o.offeredAt).getTime()) / 1000)} s` : '—'}</td>
                </tr>
              ))}
            </Table>
          </Card>
        </div>

        <div className="space-y-6">
          <Card title="People">
            <div className="text-xs font-semibold uppercase tracking-wide text-ink-3">Rider</div>
            <Link to={`/riders/${d.rider.id}`} className="mt-1 block font-semibold hover:underline">{d.rider.name}</Link>
            <div className="text-sm text-ink-3">{fmt.phone(d.rider.phone)} · ★ {fmt.rating(d.rider.rating)}{d.rider.extra ? ` · ${d.rider.extra}` : ''}</div>
            <div className="mt-4 text-xs font-semibold uppercase tracking-wide text-ink-3">Driver</div>
            {d.driver ? (
              <>
                <Link to={`/drivers/${d.driver.id}`} className="mt-1 block font-semibold hover:underline">{d.driver.name}</Link>
                <div className="text-sm text-ink-3">{fmt.phone(d.driver.phone)} · ★ {fmt.rating(d.driver.rating)}</div>
                <div className="text-sm text-ink-3">{d.driver.extra}</div>
              </>
            ) : (
              <div className="mt-1 text-sm text-ink-3">Not assigned</div>
            )}
            <div className="mt-4 grid grid-cols-2 gap-3 text-sm">
              <div><div className="text-ink-3">Rider rated driver</div><Stars value={d.driverRating} /></div>
              <div><div className="text-ink-3">Driver rated rider</div><Stars value={d.riderRating} /></div>
            </div>
            {d.driverFeedback ? <div className="mt-2 rounded-lg bg-mist px-3 py-2 text-sm text-ink-2">“{d.driverFeedback}”</div> : null}
            <div className="mt-4 text-xs text-ink-3">Start PIN {d.otp}</div>
          </Card>

          <Card title="Fare">
            {d.fare ? <FareTable fare={d.fare} quote={d.quote} /> : d.quote ? <FareTable fare={d.quote} /> : null}
            <div className="mt-3 space-y-0">
              {d.surgeMultiplier > 1 ? <KeyValue label="Surge at booking">{d.surgeMultiplier}x</KeyValue> : null}
              {d.promoCode ? <KeyValue label="Promo">{d.promoCode}</KeyValue> : null}
              {d.commission != null ? <KeyValue label="Platform commission">{fmt.rupees(d.commission, { decimals: true })}</KeyValue> : null}
              {d.driverEarning != null ? <KeyValue label="Driver earning">{fmt.rupees(d.driverEarning, { decimals: true })}</KeyValue> : null}
              {d.tip > 0 ? <KeyValue label="Tip">{fmt.rupees(d.tip)}</KeyValue> : null}
              {d.cancellationFee > 0 ? <KeyValue label="Cancellation fee">{fmt.rupees(d.cancellationFee)}</KeyValue> : null}
              {d.cancelledBy ? <KeyValue label="Cancelled by">{d.cancelledBy.toLowerCase()} · {d.cancelReason}</KeyValue> : null}
            </div>
          </Card>

          <Card title="Payments" pad={false}>
            {d.payments.length ? (
              <ul className="divide-y divide-line">
                {d.payments.map((p) => (
                  <li key={p.id} className="flex items-center justify-between px-5 py-2.5 text-sm">
                    <div>
                      <div className="font-medium">{p.kind.replace('_', ' ').toLowerCase()} · {p.method}</div>
                      <div className="text-xs text-ink-3">{p.reference ?? 'no reference'} · {fmt.time(p.createdAt)}</div>
                    </div>
                    <div className="text-right">
                      <div className="num font-semibold">{fmt.rupees(p.amount, { decimals: true })}</div>
                      <Badge tone={p.status === 'PAID' ? 'brand' : p.status === 'REFUNDED' ? 'info' : 'warn'}>{p.status.toLowerCase()}</Badge>
                    </div>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="px-5 py-5 text-sm text-ink-3">No payments yet.</p>
            )}
          </Card>
        </div>
      </div>

      <ReasonDialog open={dialog === 'cancel'} title={`Cancel ride ${d.row.code}`} label="Reason (shown to the rider and driver)" confirm="Cancel ride" danger onClose={() => setDialog(null)} onSubmit={async (reason) => { await api.cancelRide(id, reason); await refresh(); }} />
      <ReasonDialog open={dialog === 'refund'} title={`Refund on ${d.row.code}`} label="Note for the rider" confirm="Refund to wallet" withAmount onClose={() => setDialog(null)} onSubmit={async (note, amount) => { await api.refund(id, amount ?? 0, note); await refresh(); }} />
    </div>
  );
}

function Metric({ label, value, sub }: { label: string; value: string; sub: string }) {
  return (
    <div className="rounded-xl border border-line bg-white p-4">
      <div className="text-xs text-ink-3">{label}</div>
      <div className="mt-1 text-xl font-semibold">{value}</div>
      <div className="mt-0.5 text-xs text-ink-3">{sub}</div>
    </div>
  );
}

function FareTable({ fare, quote }: { fare: FareBreakdown; quote?: FareBreakdown | null }) {
  const rows: [string, number, number | undefined][] = [
    [`Base (first ${fare.baseKm} km)`, fare.baseFare, quote?.baseFare],
    [`Distance (${fare.distanceKm} km)`, fare.distanceFare, quote?.distanceFare],
    [`Time (${fare.durationMin} min)`, fare.timeFare, quote?.timeFare],
    ['Minimum-fare top-up', fare.minimumFareTopUp, quote?.minimumFareTopUp],
    [`Surge (${fare.surgeMultiplier}x)`, fare.surgeFare, quote?.surgeFare],
    [`Waiting (${fare.waitingMin} min)`, fare.waitingFare, quote?.waitingFare],
    ['Discount', -fare.discount, quote ? -quote.discount : undefined],
    [`GST ${fare.taxPct}%`, fare.tax, quote?.tax],
  ];
  return (
    <table className="w-full text-sm">
      <thead>
        <tr className="text-xs text-ink-3">
          <th className="pb-1 text-left font-medium"></th>
          {quote ? <th className="pb-1 text-right font-medium">Quoted</th> : null}
          <th className="pb-1 text-right font-medium">{quote ? 'Final' : 'Quoted'}</th>
        </tr>
      </thead>
      <tbody>
        {rows
          .filter(([, v, q]) => Math.abs(v) > 0.004 || (q != null && Math.abs(q) > 0.004))
          .map(([label, v, q]) => (
            <tr key={label} className="border-t border-line">
              <td className="py-1.5 text-ink-2">{label}</td>
              {quote ? <td className="num py-1.5 text-right text-ink-3">{q != null ? fmt.rupees(q, { decimals: true }) : '—'}</td> : null}
              <td className="num py-1.5 text-right">{fmt.rupees(v, { decimals: true })}</td>
            </tr>
          ))}
        <tr className="border-t-2 border-ink font-semibold">
          <td className="py-2">Total</td>
          {quote ? <td className="num py-2 text-right text-ink-3">{fmt.rupees(quote.total)}</td> : null}
          <td className="num py-2 text-right">{fmt.rupees(fare.total)}</td>
        </tr>
      </tbody>
    </table>
  );
}
