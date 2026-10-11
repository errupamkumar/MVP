import { fmt } from '@ecoride/core';
import { useQuery } from '@tanstack/react-query';

import { Badge, Card, ErrorNote, PageHeader, Spinner, Table } from '@/components/ui';
import { api } from '@/lib/api';

export default function Audit() {
  const { data, error } = useQuery({ queryKey: ['audit'], queryFn: api.audit, refetchInterval: 15000 });
  if (error && !data) return <ErrorNote error={error} />;
  if (!data) return <Spinner />;
  return (
    <div>
      <PageHeader title="Audit log" subtitle="Who changed what from this console: approvals, suspensions, fares, surge, promos, settings, refunds." />
      <Card pad={false}>
        <Table head={['When', 'Who', 'Action', 'Target', 'Details']} empty={!data.length}>
          {data.map((e) => (
            <tr key={e.id}>
              <td className="whitespace-nowrap px-4 py-2.5 text-ink-3">{fmt.dateTime(e.at)}</td>
              <td className="whitespace-nowrap px-4 py-2.5 font-medium">{e.actor}</td>
              <td className="px-4 py-2.5"><Badge tone={/SUSPEND|REJECT|BLOCK|CANCEL|DELETE/.test(e.action) ? 'danger' : /APPROVED|PAYOUT|REFUND|CREDIT|UNBLOCK|REACTIVATED/.test(e.action) ? 'brand' : 'neutral'}>{e.action.replace(/_/g, ' ').toLowerCase()}</Badge></td>
              <td className="px-4 py-2.5">{e.target}</td>
              <td className="max-w-[420px] px-4 py-2.5 text-ink-2">{e.details}</td>
            </tr>
          ))}
        </Table>
      </Card>
    </div>
  );
}
