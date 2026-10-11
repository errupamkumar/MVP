import { decodePolyline, fmt, type LatLng, type LiveRide } from '@ecoride/core';
import { useQuery } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { Circle, Marker, Polyline, Popup, Tooltip } from 'react-leaflet';
import { Link } from 'react-router-dom';

import { AVAILABILITY_COLOR, carIcon, MapCanvas, pointIcon } from '@/components/map';
import { Card, cx, ErrorNote, PageHeader, RideStatusBadge, SimTag, Spinner, StatTile } from '@/components/ui';
import { api } from '@/lib/api';

const TYPES = ['ALL', 'BIKE', 'AUTO', 'MINI', 'SEDAN', 'SUV'];

export default function Live() {
  const { data, error, dataUpdatedAt } = useQuery({ queryKey: ['live'], queryFn: api.live, refetchInterval: 3000 });
  const [type, setType] = useState('ALL');
  const [showSim, setShowSim] = useState(true);
  const [focus, setFocus] = useState<{ key: string; points: LatLng[] } | null>(null);

  const drivers = useMemo(
    () => (data?.drivers ?? []).filter((d) => (type === 'ALL' || d.vehicleType === type) && (showSim || !d.simulated)),
    [data, type, showSim],
  );
  const rides = useMemo(
    () => (data?.rides ?? []).filter((r) => (type === 'ALL' || r.vehicleType === type) && (showSim || !r.simulated)),
    [data, type, showSim],
  );

  if (error && !data) return <ErrorNote error={error} />;
  if (!data) return <Spinner />;

  const focusRide = (r: LiveRide) => {
    const pts: LatLng[] = [r.pickup, r.drop];
    if (r.cabLat != null && r.cabLng != null) pts.push({ lat: r.cabLat, lng: r.cabLng });
    setFocus({ key: `${r.id}-${Date.now()}`, points: pts });
  };

  return (
    <div>
      <PageHeader
        title="Live map"
        subtitle={`Refreshed every 3 s · last update ${new Date(dataUpdatedAt).toLocaleTimeString('en-IN')}`}
        actions={
          <div className="flex flex-wrap items-center gap-2">
            <div className="flex gap-1 rounded-lg bg-mist p-1">
              {TYPES.map((t) => (
                <button key={t} onClick={() => setType(t)} className={cx('rounded-md px-2.5 py-1 text-xs font-semibold', type === t ? 'bg-white text-ink shadow-sm' : 'text-ink-3')}>
                  {t === 'ALL' ? 'All' : t.charAt(0) + t.slice(1).toLowerCase()}
                </button>
              ))}
            </div>
            <label className="flex items-center gap-2 text-sm text-ink-2">
              <input type="checkbox" checked={showSim} onChange={(e) => setShowSim(e.target.checked)} /> Simulated fleet
            </label>
          </div>
        }
      />
      <div className="grid gap-4 sm:grid-cols-4">
        <StatTile label="Free drivers" value={data.online} />
        <StatTile label="Drivers on a trip" value={data.onTrip} />
        <StatTile label="Searching for a driver" value={data.searching} />
        <StatTile label="Trips in progress" value={data.inProgress} />
      </div>

      <div className="mt-6 grid gap-6 xl:grid-cols-[1fr_360px]">
        <Card pad={false}>
          <div className="relative">
            <MapCanvas height={620} fit={focus?.points} fitKey={focus?.key}>
              {data.zones.filter((z) => z.active).map((z) => (
                <Circle key={z.id} center={[z.lat, z.lng]} radius={z.radiusM} pathOptions={{ color: z.multiplier > 1 ? '#eb6834' : '#6B7280', weight: 1.5, dashArray: '4 6', fillOpacity: z.multiplier > 1 ? 0.1 : 0.03 }}>
                  <Tooltip direction="center" permanent className="!border-0 !bg-transparent !shadow-none">
                    <span className="rounded bg-white/90 px-1.5 py-0.5 text-[11px] font-semibold text-ink-2">
                      {z.name} · {z.multiplier}x
                    </span>
                  </Tooltip>
                </Circle>
              ))}
              {rides.map((r) => {
                const route = r.status === 'ACCEPTED' ? decodePolyline(r.pickupPolyline) : decodePolyline(r.routePolyline);
                return (
                  <span key={r.id}>
                    {route.length > 1 ? (
                      <Polyline positions={route.map((p) => [p.lat, p.lng] as [number, number])} pathOptions={{ color: r.status === 'ACCEPTED' ? '#0A8F5B' : r.status === 'SEARCHING' ? '#9CA3AF' : '#2a78d6', weight: 3, opacity: 0.8, dashArray: r.status === 'SEARCHING' ? '4 6' : undefined }} />
                    ) : null}
                    {r.status !== 'IN_PROGRESS' ? <Marker position={[r.pickup.lat, r.pickup.lng]} icon={pointIcon('pickup', r.status === 'SEARCHING' ? 'Searching' : undefined)} /> : null}
                    {r.status === 'IN_PROGRESS' ? <Marker position={[r.drop.lat, r.drop.lng]} icon={pointIcon('drop')} /> : null}
                  </span>
                );
              })}
              {drivers.map((d) => (
                <Marker
                  key={d.id}
                  position={[d.lat, d.lng]}
                  icon={carIcon(d.hasOffer ? AVAILABILITY_COLOR.OFFER : d.availability === 'ON_TRIP' ? AVAILABILITY_COLOR.ON_TRIP : AVAILABILITY_COLOR.ONLINE, d.heading, d.simulated ? null : d.name.split(' ')[0], !d.simulated)}
                  zIndexOffset={d.simulated ? 0 : 1000}
                >
                  <Popup>
                    <div className="font-semibold">{d.name}</div>
                    <div className="text-ink-3">{d.vehicleType} · {d.plate}</div>
                    <div className="mt-1">{d.availability === 'ON_TRIP' ? `On ride ${d.rideCode} (${d.rideStatus?.toLowerCase().replace('_', ' ')})` : d.hasOffer ? 'Looking at a request' : 'Free'}</div>
                    <div className="text-ink-3">GPS {d.ageS != null ? `${d.ageS}s ago` : '—'}</div>
                    <Link to={`/drivers/${d.id}`}>Open driver →</Link>
                  </Popup>
                </Marker>
              ))}
            </MapCanvas>
            <div className="pointer-events-none absolute bottom-3 left-3 z-[500] rounded-lg bg-white/95 px-3 py-2 text-xs shadow">
              {[
                ['Free', AVAILABILITY_COLOR.ONLINE],
                ['On a trip', AVAILABILITY_COLOR.ON_TRIP],
                ['Request on screen', AVAILABILITY_COLOR.OFFER],
              ].map(([label, color]) => (
                <div key={label} className="flex items-center gap-2">
                  <span className="inline-block h-2.5 w-2.5 rounded-sm" style={{ background: color }} /> {label}
                </div>
              ))}
              <div className="mt-1 text-ink-3">Named cars are real drivers</div>
            </div>
          </div>
        </Card>
        <Card title={`Rides in flight (${rides.length})`} pad={false}>
          <ul className="max-h-[620px] divide-y divide-line overflow-y-auto">
            {rides.map((r) => (
              <li key={r.id} className="cursor-pointer px-4 py-3 hover:bg-canvas" onClick={() => focusRide(r)}>
                <div className="flex items-center justify-between gap-2">
                  <Link to={`/rides/${r.id}`} className="font-semibold hover:underline" onClick={(e) => e.stopPropagation()}>
                    {r.code}
                  </Link>
                  <RideStatusBadge status={r.status} />
                </div>
                <div className="mt-1 text-sm text-ink-2">
                  {r.riderName}
                  <SimTag on={r.simulated} /> {r.driverName ? `→ ${r.driverName}` : ''}
                </div>
                <div className="truncate text-xs text-ink-3">
                  {r.vehicleType} · {fmt.shortPlace(r.pickup.address)} → {fmt.shortPlace(r.drop.address)} · {fmt.ago(r.requestedAt)}
                </div>
              </li>
            ))}
            {!rides.length ? <li className="px-4 py-8 text-center text-sm text-ink-3">No rides in flight.</li> : null}
          </ul>
        </Card>
      </div>
    </div>
  );
}
