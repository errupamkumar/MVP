import { Ionicons } from '@expo/vector-icons';
import { useState } from 'react';
import { Text, View } from 'react-native';

import type { QueueItemDto } from '@/api/types';
import { DeskHeader } from '@/components/DeskHeader';
import { ReasonModal } from '@/components/ReasonModal';
import {
  Banner,
  Button,
  Card,
  EmptyState,
  ErrorBanner,
  ErrorState,
  LoadingState,
  Muted,
  Pill,
  Row,
  Screen,
} from '@/components/ui';
import { useCandidates, useDeskQueue, useQueueAction } from '@/hooks/queries';
import { dayAndTime, minutes } from '@/lib/format';
import { colors } from '@/lib/theme';

export default function QueueScreen() {
  const queue = useDeskQueue();
  const action = useQueueAction();
  const [expanded, setExpanded] = useState<number | null>(null);
  const [prompt, setPrompt] = useState<{ kind: 'reject' | 'cancel'; item: QueueItemDto } | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const run = (a: Parameters<typeof action.mutate>[0]) =>
    action.mutate(a, {
      onSuccess: (r) => {
        setMessage(`${r.booking.bookingCode}: ${r.message}`);
        setExpanded(null);
      },
    });

  return (
    <Screen width="lg" refreshing={queue.isRefetching} onRefresh={() => queue.refetch()}>
      <DeskHeader title="Queue" subtitle="Riders waiting for a cab or for approval" />
      {message ? <Banner tone="teal" icon="checkmark-circle" title={message} /> : null}
      {action.isError ? <ErrorBanner error={action.error} /> : null}

      {queue.isPending ? (
        <LoadingState />
      ) : queue.isError ? (
        <ErrorState error={queue.error} onRetry={() => queue.refetch()} />
      ) : queue.data.length === 0 ? (
        <EmptyState icon="checkmark-done-circle" title="Queue is empty" message="Every request has a cab. New ones are matched automatically every 15 seconds." />
      ) : (
        queue.data.map((item) => (
          <Card key={item.bookingId} className="mb-3">
            <Row className="items-start justify-between">
              <View className="flex-1 pr-3">
                <Row className="mb-1">
                  <Pill label={item.status === 'PENDING_APPROVAL' ? 'Needs approval' : 'Waiting for a cab'} tone={item.status === 'PENDING_APPROVAL' ? 'amber' : 'accent'} />
                  <View className="ml-2">
                    <Pill label={item.rideType === 'EXCLUSIVE' ? 'Exclusive' : 'Shared'} tone="neutral" />
                  </View>
                </Row>
                <Text className="text-lg font-bold text-ink">
                  {item.fromStopName} → {item.toStopName}
                </Text>
                <Muted>
                  {item.riderName} · {item.personnelNo} · {item.costCentreCode} · {item.seats} {item.seats === 1 ? 'seat' : 'seats'} ·{' '}
                  {item.routeCode.replace('R', 'Route ')}
                </Muted>
                <Muted className="text-xs">
                  {item.bookingCode} · booked {dayAndTime(item.createdAt)}
                  {item.scheduledAt ? ` · pickup ${dayAndTime(item.scheduledAt)}` : ''}
                </Muted>
              </View>
              <View className="items-end">
                <Text className="text-2xl font-bold text-ink">{minutes(item.waitingMinutes)}</Text>
                <Muted className="text-xs">waiting</Muted>
              </View>
            </Row>

            {item.note && item.status === 'REQUESTED' ? (
              <View className="mt-2 rounded-xl bg-mist p-3">
                <Muted className="text-xs">{item.note}</Muted>
              </View>
            ) : null}

            <Row className="mt-3 flex-wrap justify-end">
              {item.status === 'PENDING_APPROVAL' ? (
                <>
                  <Button label="Reject" size="sm" variant="secondary" className="mb-1 mr-2" onPress={() => setPrompt({ kind: 'reject', item })} />
                  <Button label="Approve" size="sm" icon="checkmark" className="mb-1" loading={action.isPending} onPress={() => run({ kind: 'approve', bookingId: item.bookingId })} />
                </>
              ) : (
                <>
                  <Button label="Cancel" size="sm" variant="secondary" className="mb-1 mr-2" onPress={() => setPrompt({ kind: 'cancel', item })} />
                  <Button
                    label={expanded === item.bookingId ? 'Hide cabs' : 'Assign a cab'}
                    size="sm"
                    variant="dark"
                    icon="car"
                    className="mb-1"
                    onPress={() => setExpanded(expanded === item.bookingId ? null : item.bookingId)}
                  />
                </>
              )}
            </Row>

            {expanded === item.bookingId ? (
              <CandidatesPanel bookingId={item.bookingId} busy={action.isPending} onAssign={(vehicleId) => run({ kind: 'assign', bookingId: item.bookingId, vehicleId })} />
            ) : null}
          </Card>
        ))
      )}

      <ReasonModal
        visible={prompt != null}
        title={prompt?.kind === 'reject' ? 'Reject this exclusive ride?' : 'Cancel this booking?'}
        message={prompt ? `${prompt.item.riderName} · ${prompt.item.fromStopName} → ${prompt.item.toStopName}` : undefined}
        confirmLabel={prompt?.kind === 'reject' ? 'Reject' : 'Cancel booking'}
        destructive
        onClose={() => setPrompt(null)}
        onSubmit={(reason) => {
          if (prompt) run({ kind: prompt.kind, bookingId: prompt.item.bookingId, reason });
          setPrompt(null);
        }}
      />
    </Screen>
  );
}

/** The engine's view of every on-duty cab for this booking: who can take it, and why the others cannot. */
function CandidatesPanel({ bookingId, busy, onAssign }: { bookingId: number; busy: boolean; onAssign: (vehicleId: number) => void }) {
  const candidates = useCandidates(bookingId);
  if (candidates.isPending) return <LoadingState label="Asking the matching engine…" />;
  if (candidates.isError) return <ErrorBanner error={candidates.error} />;
  if (candidates.data.length === 0) {
    return <Banner tone="amber" icon="car" title="No cab is on duty" message="Ask a driver to sign on; waiting riders are matched automatically." />;
  }
  return (
    <View className="mt-3 border-t border-line pt-3">
      <Muted className="mb-2 text-xs">Hard rules (occupancy cap, documents, duty hours) still apply to an override.</Muted>
      {candidates.data.map((c) => (
        <Row key={c.vehicleId} className={`mb-2 rounded-xl border p-3 ${c.feasible ? 'border-teal bg-teal-soft' : 'border-line bg-mist'}`}>
          <Ionicons name={c.feasible ? 'checkmark-circle' : 'close-circle'} size={20} color={c.feasible ? colors.teal : colors.ink3} />
          <View className="ml-2 flex-1">
            <Text className={`text-base font-bold ${c.feasible ? 'text-ink' : 'text-ink-3'}`}>
              {c.vehicleCode}
              {c.feasible ? ` · ETA ${minutes(c.etaMinutes)} · ${c.seatsTaken} of ${c.capacity} seats taken` : ''}
            </Text>
            <Muted className="text-xs">{c.reason}</Muted>
          </View>
          {c.feasible ? <Button label="Assign" size="sm" loading={busy} onPress={() => onAssign(c.vehicleId)} /> : null}
        </Row>
      ))}
    </View>
  );
}
