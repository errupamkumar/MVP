import { fmt, type PromoDto } from '@ecoride/core';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';

import { Badge, Button, Card, ErrorNote, Input, PageHeader, Select, Spinner, Table } from '@/components/ui';
import { api } from '@/lib/api';

type Draft = Omit<PromoDto, 'totalUses' | 'createdAt'>;
const EMPTY: Draft = { code: '', description: '', percentOff: 20, flatOff: null, maxDiscount: 50, minFare: 0, usesPerRider: 1, validUntil: null, active: true };

export default function Promos() {
  const queryClient = useQueryClient();
  const { data, error } = useQuery({ queryKey: ['promos'], queryFn: api.promos });
  const [draft, setDraft] = useState<Draft | null>(null);
  const [kind, setKind] = useState<'PERCENT' | 'FLAT'>('PERCENT');
  const [busy, setBusy] = useState(false);
  const [saveError, setSaveError] = useState<unknown>(null);
  if (error && !data) return <ErrorNote error={error} />;
  if (!data) return <Spinner />;

  const edit = (p: Draft) => {
    setDraft(p);
    setKind(p.percentOff != null ? 'PERCENT' : 'FLAT');
    setSaveError(null);
  };
  const save = async () => {
    if (!draft) return;
    setBusy(true);
    setSaveError(null);
    try {
      await api.savePromo({
        ...draft,
        percentOff: kind === 'PERCENT' ? draft.percentOff : null,
        maxDiscount: kind === 'PERCENT' ? draft.maxDiscount : null,
        flatOff: kind === 'FLAT' ? draft.flatOff : null,
      });
      await queryClient.invalidateQueries({ queryKey: ['promos'] });
      setDraft(null);
    } catch (e) {
      setSaveError(e);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div>
      <PageHeader title="Promotions" subtitle="Promo codes riders enter on the ride options screen. EcoRide funds the discount; drivers earn on the full fare." actions={<Button variant="primary" onClick={() => edit({ ...EMPTY })}>New promo</Button>} />
      <div className="grid gap-6 xl:grid-cols-[1fr_380px]">
        <Card pad={false}>
          <Table head={['Code', 'Offer', 'Conditions', 'Valid until', 'Used', 'Status']}>
            {data.map((p) => (
              <tr key={p.code} className="cursor-pointer hover:bg-canvas" onClick={() => edit(p)}>
                <td className="px-4 py-2.5 font-mono font-semibold">{p.code}</td>
                <td className="px-4 py-2.5">
                  <div className="font-medium">{p.percentOff != null ? `${p.percentOff}% off${p.maxDiscount ? `, up to ${fmt.rupees(p.maxDiscount)}` : ''}` : `${fmt.rupees(p.flatOff)} off`}</div>
                  <div className="text-xs text-ink-3">{p.description}</div>
                </td>
                <td className="px-4 py-2.5 text-ink-2">{p.usesPerRider} per rider{p.minFare > 0 ? ` · fares above ${fmt.rupees(p.minFare)}` : ''}</td>
                <td className="whitespace-nowrap px-4 py-2.5 text-ink-2">{p.validUntil ? new Date(p.validUntil).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' }) : 'No end date'}</td>
                <td className="num px-4 py-2.5">{p.totalUses}</td>
                <td className="px-4 py-2.5">{p.active ? <Badge tone="brand" icon="✓">Active</Badge> : <Badge icon="○">Off</Badge>}</td>
              </tr>
            ))}
          </Table>
        </Card>
        {draft ? (
          <Card title={data.some((p) => p.code === draft.code) ? `Edit ${draft.code}` : 'New promo code'} className="h-fit">
            <div className="space-y-3">
              <Input label="Code" value={draft.code} onChange={(e) => setDraft({ ...draft, code: e.target.value.toUpperCase() })} placeholder="DIWALI30" />
              <Input label="Description (shown to riders)" value={draft.description} onChange={(e) => setDraft({ ...draft, description: e.target.value })} />
              <Select label="Discount" value={kind} onChange={(v) => setKind(v as 'PERCENT' | 'FLAT')} options={[{ value: 'PERCENT', label: 'Percentage' }, { value: 'FLAT', label: 'Flat amount' }]} />
              {kind === 'PERCENT' ? (
                <div className="grid grid-cols-2 gap-3">
                  <Input label="Percent off" type="number" min={1} max={100} value={draft.percentOff ?? ''} onChange={(e) => setDraft({ ...draft, percentOff: Number(e.target.value) })} />
                  <Input label="Up to (₹)" type="number" min={1} value={draft.maxDiscount ?? ''} onChange={(e) => setDraft({ ...draft, maxDiscount: Number(e.target.value) })} />
                </div>
              ) : (
                <Input label="Amount off (₹)" type="number" min={1} value={draft.flatOff ?? ''} onChange={(e) => setDraft({ ...draft, flatOff: Number(e.target.value) })} />
              )}
              <div className="grid grid-cols-2 gap-3">
                <Input label="Minimum fare (₹)" type="number" min={0} value={draft.minFare} onChange={(e) => setDraft({ ...draft, minFare: Number(e.target.value) })} />
                <Input label="Uses per rider" type="number" min={1} value={draft.usesPerRider} onChange={(e) => setDraft({ ...draft, usesPerRider: Number(e.target.value) })} />
              </div>
              <Input label="Valid until (optional)" type="date" value={draft.validUntil ?? ''} onChange={(e) => setDraft({ ...draft, validUntil: e.target.value || null })} />
              <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={draft.active} onChange={(e) => setDraft({ ...draft, active: e.target.checked })} /> Active</label>
              <ErrorNote error={saveError} />
              <div className="flex justify-end gap-2 pt-2">
                <Button onClick={() => setDraft(null)}>Cancel</Button>
                <Button variant="primary" loading={busy} disabled={draft.code.length < 3 || !draft.description.trim()} onClick={save}>Save promo</Button>
              </div>
            </div>
          </Card>
        ) : null}
      </div>
    </div>
  );
}
