import { fmt } from '@ecoride/core';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link } from 'react-router-dom';

import { Badge, Button, Card, ErrorNote, PageHeader, ReasonDialog, Spinner, Table } from '@/components/ui';
import { api } from '@/lib/api';

export default function Safety() {
  const queryClient = useQueryClient();
  const { data, error } = useQuery({ queryKey: ['sos'], queryFn: api.sos, refetchInterval: 5000 });
  const [resolving, setResolving] = useState<number | null>(null);
  if (error && !data) return <ErrorNote error={error} />;
  if (!data) return <Spinner />;
  const open = data.filter((s) => s.status === 'OPEN');
  return (
    <div>
      <PageHeader title="Safety" subtitle="SOS alerts raised from the rider or driver app during a ride. Call both parties, then resolve." />
      {open.length ? (
        <div className="mb-6 rounded-xl border border-[#F3B4B4] bg-[#FDECEC] px-5 py-4 text-sm text-[#B42318]">
          <strong>{open.length} open alert{open.length > 1 ? 's' : ''}.</strong> Each shows the ride and where it was raised.
        </div>
      ) : null}
      <Card pad={false}>
        <Table head={['Raised', 'Ride', 'By', 'Rider', 'Driver', 'Where', 'Note', 'Status', '']} empty={!data.length}>
          {data.map((s) => (
            <tr key={s.id} className={s.status === 'OPEN' ? 'bg-[#FFF7F7]' : ''}>
              <td className="whitespace-nowrap px-4 py-2.5">{fmt.dateTime(s.createdAt)}<div className="text-xs text-ink-3">{fmt.ago(s.createdAt)}</div></td>
              <td className="px-4 py-2.5"><Link to={`/rides/${s.rideId}`} className="font-semibold hover:underline">{s.rideCode}</Link></td>
              <td className="px-4 py-2.5">{s.raisedBy.toLowerCase()}</td>
              <td className="px-4 py-2.5">{s.riderName}</td>
              <td className="px-4 py-2.5">{s.driverName ?? '—'}</td>
              <td className="px-4 py-2.5">
                {s.lat != null && s.lng != null ? (
                  <a className="text-brand hover:underline" target="_blank" rel="noreferrer" href={`https://www.openstreetmap.org/?mlat=${s.lat}&mlon=${s.lng}#map=17/${s.lat}/${s.lng}`}>
                    {s.lat.toFixed(4)}, {s.lng.toFixed(4)}
                  </a>
                ) : '—'}
              </td>
              <td className="max-w-[240px] px-4 py-2.5 text-ink-2">{s.note ?? '—'}</td>
              <td className="px-4 py-2.5">{s.status === 'OPEN' ? <Badge tone="danger" icon="⚠">Open</Badge> : <Badge tone="brand" icon="✓">Resolved</Badge>}{s.resolvedBy ? <div className="text-xs text-ink-3">by {s.resolvedBy}</div> : null}</td>
              <td className="px-4 py-2.5 text-right">{s.status === 'OPEN' ? <Button size="sm" variant="primary" onClick={() => setResolving(s.id)}>Resolve</Button> : null}</td>
            </tr>
          ))}
        </Table>
      </Card>
      <ReasonDialog
        open={resolving != null}
        title="Resolve SOS"
        label="What was done"
        confirm="Mark resolved"
        onClose={() => setResolving(null)}
        onSubmit={async (note) => {
          await api.resolveSos(resolving!, note);
          await queryClient.invalidateQueries({ queryKey: ['sos'] });
          void queryClient.invalidateQueries({ queryKey: ['sos-count'] });
        }}
      />
    </div>
  );
}
