import { fmt, type SurgeZoneDto } from '@ecoride/core';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { Circle, Tooltip, useMapEvents } from 'react-leaflet';

import { MapCanvas } from '@/components/map';
import { Badge, Button, Card, ErrorNote, Input, PageHeader, Select, Spinner, Table } from '@/components/ui';
import { api } from '@/lib/api';

type Draft = Omit<SurgeZoneDto, 'id' | 'currentMultiplier' | 'demand' | 'supply' | 'updatedAt'> & { id: number | null };

const EMPTY: Draft = { id: null, name: '', lat: 18.5204, lng: 73.8567, radiusM: 1500, mode: 'MANUAL', manualMultiplier: 1.2, active: true };

function PickOnMap({ onPick }: { onPick: (lat: number, lng: number) => void }) {
  useMapEvents({ click: (e) => onPick(e.latlng.lat, e.latlng.lng) });
  return null;
}

export default function Surge() {
  const queryClient = useQueryClient();
  const { data, error } = useQuery({ queryKey: ['zones'], queryFn: api.zones, refetchInterval: 10000 });
  const settings = useQuery({ queryKey: ['settings'], queryFn: api.settings }).data;
  const [draft, setDraft] = useState<Draft | null>(null);
  const [busy, setBusy] = useState(false);
  const [saveError, setSaveError] = useState<unknown>(null);
  useEffect(() => setSaveError(null), [draft?.id]);
  if (error && !data) return <ErrorNote error={error} />;
  if (!data) return <Spinner />;
  const cap = settings?.find((s) => s.key === 'SURGE_CAP')?.value;
  const enabled = settings?.find((s) => s.key === 'SURGE_ENABLED')?.value === 'true';

  const save = async () => {
    if (!draft) return;
    setBusy(true);
    setSaveError(null);
    try {
      const { id, ...body } = draft;
      await api.saveZone(id, body);
      await queryClient.invalidateQueries({ queryKey: ['zones'] });
      setDraft(null);
    } catch (e) {
      setSaveError(e);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div>
      <PageHeader
        title="Surge zones"
        subtitle={`Surge applies only inside an active zone, capped at ${cap ?? '1.5'}x (Maharashtra aggregator rules). Surge is ${enabled ? 'on' : 'off'} (Settings).`}
        actions={<Button variant="primary" onClick={() => setDraft({ ...EMPTY })}>Add zone</Button>}
      />
      <div className="grid gap-6 xl:grid-cols-[1fr_400px]">
        <Card pad={false}>
          <MapCanvas height={560} zoom={12}>
            {data.map((z) => (
              <Circle
                key={z.id}
                center={[z.lat, z.lng]}
                radius={z.radiusM}
                eventHandlers={{ click: () => setDraft({ ...z }) }}
                pathOptions={{ color: !z.active ? '#9CA3AF' : z.currentMultiplier > 1 ? '#eb6834' : '#2a78d6', weight: 2, fillOpacity: z.active ? 0.12 : 0.04, dashArray: z.active ? undefined : '4 6' }}
              >
                <Tooltip permanent direction="center" className="!border-0 !bg-transparent !shadow-none">
                  <span className="rounded bg-white/95 px-1.5 py-0.5 text-[11px] font-semibold text-ink">{z.name} · {z.active ? `${z.currentMultiplier}x` : 'off'}</span>
                </Tooltip>
              </Circle>
            ))}
            {draft ? <Circle center={[draft.lat, draft.lng]} radius={draft.radiusM} pathOptions={{ color: '#111827', weight: 2, dashArray: '6 6', fillOpacity: 0.05 }} /> : null}
            {draft ? <PickOnMap onPick={(lat, lng) => setDraft({ ...draft, lat: Number(lat.toFixed(5)), lng: Number(lng.toFixed(5)) })} /> : null}
          </MapCanvas>
          <div className="border-t border-line px-5 py-2 text-xs text-ink-3">{draft ? 'Click the map to move the zone centre.' : 'Click a zone to edit it.'}</div>
        </Card>
        <div className="space-y-6">
          {draft ? (
            <Card title={draft.id ? `Edit ${draft.name}` : 'New surge zone'}>
              <div className="space-y-3">
                <Input label="Name" value={draft.name} onChange={(e) => setDraft({ ...draft, name: e.target.value })} />
                <div className="grid grid-cols-2 gap-3">
                  <Input label="Latitude" type="number" step="0.0001" value={draft.lat} onChange={(e) => setDraft({ ...draft, lat: Number(e.target.value) })} />
                  <Input label="Longitude" type="number" step="0.0001" value={draft.lng} onChange={(e) => setDraft({ ...draft, lng: Number(e.target.value) })} />
                </div>
                <Input label="Radius (m)" type="number" min={300} max={20000} step={100} value={draft.radiusM} onChange={(e) => setDraft({ ...draft, radiusM: Number(e.target.value) })} />
                <Select label="Mode" value={draft.mode} onChange={(v) => setDraft({ ...draft, mode: v as 'AUTO' | 'MANUAL' })} options={[{ value: 'AUTO', label: 'Automatic (demand vs free drivers)' }, { value: 'MANUAL', label: 'Manual multiplier' }]} />
                {draft.mode === 'MANUAL' ? (
                  <Input label="Multiplier (x)" type="number" min={1} max={3} step="0.1" value={draft.manualMultiplier} onChange={(e) => setDraft({ ...draft, manualMultiplier: Number(e.target.value) })} hint={`Riders are never charged more than the ${cap ?? '1.5'}x cap.`} />
                ) : null}
                <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={draft.active} onChange={(e) => setDraft({ ...draft, active: e.target.checked })} /> Active</label>
                <ErrorNote error={saveError} />
                <div className="flex justify-between gap-2 pt-2">
                  {draft.id ? <Button variant="ghost" onClick={async () => { await api.deleteZone(draft.id!); await queryClient.invalidateQueries({ queryKey: ['zones'] }); setDraft(null); }}>Delete</Button> : <span />}
                  <div className="flex gap-2">
                    <Button onClick={() => setDraft(null)}>Cancel</Button>
                    <Button variant="primary" loading={busy} disabled={!draft.name.trim()} onClick={save}>Save zone</Button>
                  </div>
                </div>
              </div>
            </Card>
          ) : null}
          <Card title="Zones" pad={false}>
            <Table head={['Zone', 'Mode', 'Now', 'Demand / free']}>
              {data.map((z) => (
                <tr key={z.id} className="cursor-pointer hover:bg-canvas" onClick={() => setDraft({ ...z })}>
                  <td className="px-4 py-2.5"><div className="font-semibold">{z.name}</div><div className="text-xs text-ink-3">{fmt.distance(z.radiusM)} radius</div></td>
                  <td className="px-4 py-2.5">{z.mode === 'AUTO' ? 'Auto' : `Manual ${z.manualMultiplier}x`}</td>
                  <td className="px-4 py-2.5">{!z.active ? <Badge>Off</Badge> : z.currentMultiplier > 1 ? <Badge tone="warn" icon="⤴">{z.currentMultiplier}x</Badge> : <Badge tone="brand">1.0x</Badge>}</td>
                  <td className="num px-4 py-2.5 text-ink-2">{z.demand != null ? `${z.demand} / ${z.supply}` : '—'}</td>
                </tr>
              ))}
            </Table>
          </Card>
        </div>
      </div>
    </div>
  );
}
