import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { Text, useWindowDimensions, View } from 'react-native';

import type { AlertDto, VehicleLiveDto } from '@/api/types';
import { DeskHeader } from '@/components/DeskHeader';
import { Button, Card, EmptyState, ErrorBanner, ErrorState, LoadingState, Muted, Pill, Row, Screen, SectionTitle, StatTile } from '@/components/ui';
import { useAlertAction, useDeskBoard, useVehicleStatus } from '@/hooks/queries';
import { confirmAction } from '@/lib/dialogs';
import { colors } from '@/lib/theme';

const STATUS_TONE: Record<VehicleLiveDto['status'], 'teal' | 'accent' | 'neutral' | 'danger' | 'amber' | 'ink'> = {
  ON_TRIP: 'teal',
  ASSIGNED: 'accent',
  SHUTTLE: 'ink',
  IDLE: 'amber',
  OFF_DUTY: 'neutral',
  BLOCKED: 'danger',
  OFF_ROAD: 'danger',
};

const SEVERITY_COLOR: Record<AlertDto['severity'], string> = {
  CRITICAL: colors.danger,
  HIGH: colors.accent,
  MEDIUM: colors.amber,
  LOW: colors.ink3,
};

export default function LiveBoard() {
  const router = useRouter();
  const board = useDeskBoard();
  const { width } = useWindowDimensions();
  const twoColumns = width >= 1200;

  if (board.isPending) {
    return (
      <Screen width="xl">
        <LoadingState label="Loading the live board…" />
      </Screen>
    );
  }
  if (board.isError) {
    return (
      <Screen width="xl">
        <ErrorState error={board.error} onRetry={() => board.refetch()} />
      </Screen>
    );
  }

  const { kpis, vehicles, alerts, queue } = board.data;
  return (
    <Screen width="xl" refreshing={board.isRefetching} onRefresh={() => board.refetch()}>
      <DeskHeader title="Live board" subtitle="Whole fleet on one screen · refreshes every 5 s" updatedAt={board.data.generatedAt} />

      <View className="flex-row flex-wrap">
        <StatTile value={`${kpis.vehiclesOnTrip} / ${kpis.fleetSize}`} label="Vehicles in service" />
        <StatTile value={`${kpis.seatOccupancyPct}%`} label="Seat occupancy today" tone="teal" />
        <StatTile value={`${kpis.avgWaitMinutes} min`} label="Average wait today" />
        <StatTile value={`${kpis.onTimePickupPct}%`} label="On-time pickups" tone={kpis.onTimePickupPct >= 90 ? 'teal' : 'accent'} />
        <StatTile value={String(kpis.waitingBookings)} label="Riders waiting" tone={kpis.waitingBookings > 0 ? 'accent' : 'ink'} />
        <StatTile value={String(kpis.openAlerts)} label="Open alerts" tone={kpis.openAlerts > 0 ? 'danger' : 'ink'} />
      </View>

      <View className={twoColumns ? 'flex-row items-start' : ''}>
        <View className={twoColumns ? 'mr-4 flex-[3]' : ''}>
          <SectionTitle title={`Live fleet · ${vehicles.length} vehicles`} />
          {vehicles.map((v) => (
            <VehicleRow key={v.id} v={v} />
          ))}
        </View>

        <View className={twoColumns ? 'flex-[2]' : ''}>
          <SectionTitle title={`Needs attention · ${alerts.length}`} />
          {alerts.length === 0 ? (
            <Card>
              <EmptyState icon="shield-checkmark" title="All clear" message="No open alerts." />
            </Card>
          ) : (
            alerts.map((a) => <AlertRow key={a.id} a={a} />)
          )}

          <SectionTitle
            title={`Waiting for a cab · ${queue.length}`}
            action={queue.length > 0 ? <Button label="Open queue" size="sm" variant="ghost" onPress={() => router.push('/desk/queue')} /> : null}
          />
          {queue.length === 0 ? (
            <Card>
              <Muted>Nobody is waiting. Every request has a cab.</Muted>
            </Card>
          ) : (
            queue.slice(0, 5).map((q) => (
              <Card key={q.bookingId} className="mb-2" onPress={() => router.push('/desk/queue')}>
                <Row className="justify-between">
                  <Text className="text-base font-bold text-ink">{q.riderName}</Text>
                  <Pill label={q.status === 'PENDING_APPROVAL' ? 'Approval' : `${q.waitingMinutes} min`} tone={q.status === 'PENDING_APPROVAL' ? 'amber' : 'accent'} />
                </Row>
                <Muted>
                  {q.fromStopName} → {q.toStopName} · {q.rideType === 'EXCLUSIVE' ? 'exclusive' : 'shared'}
                </Muted>
              </Card>
            ))
          )}
        </View>
      </View>
    </Screen>
  );
}

function VehicleRow({ v }: { v: VehicleLiveDto }) {
  const status = useVehicleStatus();
  const toggle = async () => {
    const goingOff = v.status !== 'OFF_ROAD';
    const ok = await confirmAction(
      goingOff ? `Take ${v.code} off-road?` : `Put ${v.code} back on the road?`,
      goingOff ? 'Anyone waiting for or riding in it is re-matched to another cab.' : 'It can be dispatched again once a driver is on duty.',
      goingOff ? 'Take off-road' : 'Back on road',
      goingOff,
    );
    if (ok) status.mutate({ vehicleId: v.id, status: goingOff ? 'OFF_ROAD' : 'ACTIVE' });
  };
  return (
    <Card className="mb-2">
      <Row className="items-start">
        <View className="w-16">
          <Text className="text-lg font-bold text-ink">{v.code}</Text>
          <Muted className="text-xs">{v.vehicleType === 'SHUTTLE' ? 'Shuttle' : 'Cab'}</Muted>
        </View>
        <View className="flex-1">
          <Row className="flex-wrap">
            <View className="mb-1 mr-1.5">
              <Pill label={v.statusLabel} tone={STATUS_TONE[v.status]} />
            </View>
            {v.flags
              .filter((f) => !(f === 'BREAKDOWN' && v.status === 'OFF_ROAD') && !(f === 'DOC_EXPIRY' && v.status === 'BLOCKED'))
              .map((f) => (
              <View key={f} className="mb-1 mr-1.5">
                <Pill label={f === 'DOC_EXPIRY' ? 'Doc expiry' : f === 'OVERSPEED' ? 'Overspeed' : f === 'SOS' ? 'SOS' : 'Breakdown'} tone="danger" />
              </View>
            ))}
          </Row>
          {v.routeLabel ? <Text className="text-sm font-semibold text-ink-2">{v.routeLabel}</Text> : null}
          <Muted>{v.locationLabel}</Muted>
        </View>
        <View className="ml-2 items-end">
          <Text className="text-base font-bold text-ink">
            {v.riders} of {v.capacity}
          </Text>
          <Muted className="text-xs">riders</Muted>
          {v.driverName ? <Text className="mt-1 text-sm text-ink-2">{v.driverName}</Text> : null}
        </View>
      </Row>
      {status.isError ? <View className="mt-2"><ErrorBanner error={status.error} /></View> : null}
      {v.status === 'OFF_ROAD' || v.status === 'ON_TRIP' || v.status === 'ASSIGNED' || v.status === 'IDLE' ? (
        <Row className="mt-2 justify-end">
          <Button
            label={v.status === 'OFF_ROAD' ? 'Back on road' : 'Take off-road'}
            size="sm"
            variant="ghost"
            icon={v.status === 'OFF_ROAD' ? 'checkmark-circle' : 'construct'}
            loading={status.isPending}
            onPress={toggle}
          />
        </Row>
      ) : null}
    </Card>
  );
}

function AlertRow({ a }: { a: AlertDto }) {
  const action = useAlertAction();
  return (
    <Card className="mb-2">
      <Row className="items-start">
        <Ionicons
          name={a.type === 'SOS' ? 'warning' : a.type === 'DOC_EXPIRY' ? 'document-text' : a.type === 'OVERSPEED' ? 'speedometer' : 'alert-circle'}
          size={22}
          color={SEVERITY_COLOR[a.severity]}
        />
        <View className="ml-3 flex-1">
          <Text className="text-base font-semibold text-ink">{a.message}</Text>
          <Muted className="mt-0.5 text-xs">
            {a.severity.toLowerCase()} · {a.minutesAgo < 1 ? 'just now' : `${a.minutesAgo} min ago`} · {a.raisedBy}
            {a.status === 'ACKNOWLEDGED' ? ` · acknowledged by ${a.acknowledgedBy}` : ''}
          </Muted>
        </View>
      </Row>
      {action.isError ? <View className="mt-2"><ErrorBanner error={action.error} /></View> : null}
      <Row className="mt-2 justify-end">
        {a.status === 'OPEN' ? (
          <Button label="Acknowledge" size="sm" variant="secondary" className="mr-2" loading={action.isPending && action.variables?.action === 'ack'} onPress={() => action.mutate({ id: a.id, action: 'ack' })} />
        ) : null}
        <Button label="Resolve" size="sm" variant="dark" loading={action.isPending && action.variables?.action === 'resolve'} onPress={() => action.mutate({ id: a.id, action: 'resolve' })} />
      </Row>
    </Card>
  );
}
