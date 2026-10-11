import { fmt, type FareBreakdown, type VehicleTypeDto } from '@ecoride/core';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';

import { Button, Card, ErrorNote, Input, KeyValue, PageHeader, Select, Spinner } from '@/components/ui';
import { api } from '@/lib/api';

type Editable = Omit<VehicleTypeDto, 'code' | 'sortOrder' | 'updatedAt'>;

const FIELDS: { key: keyof Editable; label: string; step?: string }[] = [
  { key: 'baseFare', label: 'Base fare (₹)' },
  { key: 'baseKm', label: 'Base covers (km)', step: '0.5' },
  { key: 'perKm', label: 'Per km after (₹)', step: '0.5' },
  { key: 'perMin', label: 'Per minute (₹)', step: '0.25' },
  { key: 'minFare', label: 'Minimum fare (₹)' },
  { key: 'waitFreeMin', label: 'Free waiting (min)' },
  { key: 'waitPerMin', label: 'Waiting per min (₹)', step: '0.5' },
  { key: 'cancelFee', label: 'Cancellation fee (₹)' },
  { key: 'capacity', label: 'Seats' },
];

export default function Pricing() {
  const { data, error } = useQuery({ queryKey: ['vehicle-types'], queryFn: api.vehicleTypes });
  if (error && !data) return <ErrorNote error={error} />;
  if (!data) return <Spinner />;
  return (
    <div>
      <PageHeader title="Fares" subtitle="Fare card per ride type. New quotes use changes at once; rides already booked keep their upfront price." />
      <div className="grid gap-6 xl:grid-cols-[1fr_380px]">
        <div className="space-y-4">
          {data.map((vt) => (
            <FareCardEditor key={vt.code} vt={vt} />
          ))}
        </div>
        <FarePreview types={data} />
      </div>
    </div>
  );
}

function FareCardEditor({ vt }: { vt: VehicleTypeDto }) {
  const queryClient = useQueryClient();
  const [form, setForm] = useState<Editable>(vt);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [saved, setSaved] = useState(false);
  useEffect(() => setForm(vt), [vt]);
  const dirty = FIELDS.some((f) => Number(form[f.key]) !== Number(vt[f.key])) || form.active !== vt.active || form.name !== vt.name || form.description !== vt.description;

  return (
    <Card
      title={
        <span className="flex items-center gap-2">
          {vt.name} <span className="text-xs font-normal text-ink-3">{vt.code} · {vt.description}</span>
        </span>
      }
      action={
        <label className="flex items-center gap-2 text-sm text-ink-2">
          <input type="checkbox" checked={form.active} onChange={(e) => setForm({ ...form, active: e.target.checked })} /> Offered to riders
        </label>
      }
    >
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
        {FIELDS.map((f) => (
          <Input
            key={f.key}
            label={f.label}
            type="number"
            step={f.step ?? '1'}
            min={0}
            value={String(form[f.key])}
            onChange={(e) => setForm({ ...form, [f.key]: e.target.value === '' ? 0 : Number(e.target.value) })}
          />
        ))}
      </div>
      <div className="mt-4 flex flex-wrap items-center justify-between gap-3">
        <p className="text-xs text-ink-3">
          {fmt.rupees(form.baseFare)} for the first {form.baseKm} km, then {fmt.rupees(form.perKm, { decimals: true })}/km and {fmt.rupees(form.perMin, { decimals: true })}/min · last changed {fmt.ago(vt.updatedAt)}
        </p>
        <div className="flex items-center gap-2">
          {saved ? <span className="text-sm font-semibold text-brand">Saved</span> : null}
          <Button variant="primary" disabled={!dirty} loading={busy} onClick={async () => {
            setBusy(true);
            setError(null);
            try {
              await api.updateVehicleType(vt.code, form);
              await queryClient.invalidateQueries({ queryKey: ['vehicle-types'] });
              setSaved(true);
              setTimeout(() => setSaved(false), 2500);
            } catch (e) {
              setError(e);
            } finally {
              setBusy(false);
            }
          }}>
            Save {vt.name}
          </Button>
        </div>
      </div>
      {error ? <div className="mt-3"><ErrorNote error={error} /></div> : null}
    </Card>
  );
}

function FarePreview({ types }: { types: VehicleTypeDto[] }) {
  const [vehicleType, setVehicleType] = useState('MINI');
  const [distanceKm, setDistanceKm] = useState(8);
  const [minutes, setMinutes] = useState(25);
  const [surge, setSurge] = useState(1);
  const [waitingMin, setWaitingMin] = useState(0);
  const [promoCode, setPromoCode] = useState('');
  const [fare, setFare] = useState<FareBreakdown | null>(null);
  const [error, setError] = useState<unknown>(null);
  useEffect(() => {
    const t = setTimeout(() => {
      api.farePreview({ vehicleType, distanceKm, minutes, surge, waitingMin, promoCode: promoCode || undefined }).then(setFare, setError);
    }, 250);
    return () => clearTimeout(t);
  }, [vehicleType, distanceKm, minutes, surge, waitingMin, promoCode, types]);
  return (
    <Card title="Fare calculator" className="h-fit xl:sticky xl:top-6">
      <div className="grid grid-cols-2 gap-3">
        <Select label="Ride type" value={vehicleType} onChange={setVehicleType} options={types.map((t) => ({ value: t.code, label: t.name }))} />
        <Input label="Distance (km)" type="number" min={0.1} step="0.5" value={distanceKm} onChange={(e) => setDistanceKm(Number(e.target.value))} />
        <Input label="Duration (min)" type="number" min={1} value={minutes} onChange={(e) => setMinutes(Number(e.target.value))} />
        <Input label="Surge (x)" type="number" min={1} max={3} step="0.1" value={surge} onChange={(e) => setSurge(Number(e.target.value))} />
        <Input label="Waiting (min)" type="number" min={0} value={waitingMin} onChange={(e) => setWaitingMin(Number(e.target.value))} />
        <Input label="Promo code" value={promoCode} onChange={(e) => setPromoCode(e.target.value.toUpperCase())} />
      </div>
      <ErrorNote error={error} />
      {fare ? (
        <div className="mt-4">
          <KeyValue label={`Base (first ${fare.baseKm} km)`}>{fmt.rupees(fare.baseFare, { decimals: true })}</KeyValue>
          <KeyValue label="Distance">{fmt.rupees(fare.distanceFare, { decimals: true })}</KeyValue>
          <KeyValue label="Time">{fmt.rupees(fare.timeFare, { decimals: true })}</KeyValue>
          {fare.minimumFareTopUp > 0 ? <KeyValue label="Minimum-fare top-up">{fmt.rupees(fare.minimumFareTopUp, { decimals: true })}</KeyValue> : null}
          {fare.surgeFare > 0 ? <KeyValue label={`Surge ${fare.surgeMultiplier}x`}>{fmt.rupees(fare.surgeFare, { decimals: true })}</KeyValue> : null}
          {fare.waitingFare > 0 ? <KeyValue label={`Waiting (${fare.waitingMin} min charged)`}>{fmt.rupees(fare.waitingFare, { decimals: true })}</KeyValue> : null}
          {fare.discount > 0 ? <KeyValue label={`Promo ${fare.promoCode}`}>−{fmt.rupees(fare.discount, { decimals: true })}</KeyValue> : null}
          <KeyValue label={`GST ${fare.taxPct}%`}>{fmt.rupees(fare.tax, { decimals: true })}</KeyValue>
          <div className="mt-3 flex items-baseline justify-between">
            <span className="text-sm font-semibold">Rider pays</span>
            <span className="text-3xl font-bold tracking-tight">{fmt.rupees(fare.total)}</span>
          </div>
          <div className="mt-1 text-right text-xs text-ink-3">Driver earns {fmt.rupees(fare.fareBeforeDiscount * 0.8)} at 20% commission</div>
        </div>
      ) : null}
    </Card>
  );
}
