import { fmt } from '@ecoride/core';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';

import { Badge, Card, ErrorNote, Input, PageHeader, Pager, SimTag, Stars, Table } from '@/components/ui';
import { api } from '@/lib/api';

export default function Riders() {
  const [params, setParams] = useSearchParams();
  const [q, setQ] = useState(params.get('q') ?? '');
  const page = Number(params.get('page') ?? 0);
  const { data, error } = useQuery({
    queryKey: ['riders', params.toString()],
    queryFn: () => api.riders({ q: params.get('q') || undefined, page, size: 25 }),
    placeholderData: keepPreviousData,
  });
  return (
    <div>
      <PageHeader title="Riders" subtitle="Rider accounts, wallets and ratings." />
      <Card pad={false}>
        <form
          className="flex gap-3 border-b border-line p-4"
          onSubmit={(e) => {
            e.preventDefault();
            setParams(q.trim() ? { q: q.trim() } : {});
          }}
        >
          <Input placeholder="Name or phone" value={q} onChange={(e) => setQ(e.target.value)} className="w-72" />
        </form>
        <ErrorNote error={error} />
        <Table head={['Rider', 'Status', 'Rating', 'Completed rides', 'Wallet', 'Joined', 'Last sign-in']} empty={data?.items.length === 0}>
          {(data?.items ?? []).map((r) => (
            <tr key={r.id} className="hover:bg-canvas">
              <td className="px-4 py-2.5">
                <Link to={`/riders/${r.id}`} className="font-semibold hover:underline">{r.name}</Link>
                <SimTag on={r.simulated} />
                <div className="text-xs text-ink-3">{fmt.phone(r.phone)}{r.email ? ` · ${r.email}` : ''}</div>
              </td>
              <td className="px-4 py-2.5">{r.status === 'ACTIVE' ? <Badge tone="brand" icon="✓">Active</Badge> : <Badge tone="danger" icon="⛔">Blocked</Badge>}</td>
              <td className="px-4 py-2.5"><Stars value={r.ratingCount ? r.rating : null} /></td>
              <td className="num px-4 py-2.5">{r.rides}</td>
              <td className="num px-4 py-2.5 font-semibold">{fmt.rupees(r.wallet)}</td>
              <td className="whitespace-nowrap px-4 py-2.5 text-ink-3">{fmt.date(r.createdAt)}</td>
              <td className="whitespace-nowrap px-4 py-2.5 text-ink-3">{r.lastLoginAt ? fmt.ago(r.lastLoginAt) : '—'}</td>
            </tr>
          ))}
        </Table>
        {data ? <Pager page={data.page} size={data.size} total={data.total} onPage={(p) => setParams({ ...(params.get('q') ? { q: params.get('q')! } : {}), page: String(p) })} /> : null}
      </Card>
    </div>
  );
}
