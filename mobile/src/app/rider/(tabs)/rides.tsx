import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { useState } from 'react';
import { Text, View } from 'react-native';

import type { BookingScope } from '@/api/types';
import { StatusPill } from '@/components/StatusPill';
import { Card, EmptyState, ErrorState, H1, LoadingState, Muted, Row, Screen, Segmented } from '@/components/ui';
import { useMyBookings } from '@/hooks/queries';
import { dayAndTime, km, rupees } from '@/lib/format';
import { colors } from '@/lib/theme';

const SCOPES: { value: BookingScope; label: string }[] = [
  { value: 'upcoming', label: 'Upcoming' },
  { value: 'past', label: 'Past' },
  { value: 'cancelled', label: 'Cancelled' },
];

export default function RidesScreen() {
  const router = useRouter();
  const [scope, setScope] = useState<BookingScope>('upcoming');
  const rides = useMyBookings(scope);

  return (
    <Screen refreshing={rides.isRefetching} onRefresh={() => rides.refetch()}>
      <H1 className="mb-1">My trips</H1>
      <Muted className="mb-4">Every ride, always retrievable: for expense queries and the desk's audit trail.</Muted>
      <Segmented options={SCOPES} value={scope} onChange={setScope} />
      <View className="mt-4">
        {rides.isPending ? (
          <LoadingState />
        ) : rides.isError ? (
          <ErrorState error={rides.error} onRetry={() => rides.refetch()} />
        ) : rides.data.length === 0 ? (
          <EmptyState
            icon={scope === 'upcoming' ? 'calendar-outline' : scope === 'past' ? 'time-outline' : 'close-circle-outline'}
            title={scope === 'upcoming' ? 'No upcoming rides' : scope === 'past' ? 'No past rides yet' : 'Nothing cancelled'}
            message={scope === 'upcoming' ? 'Book a ride from the Home tab.' : undefined}
          />
        ) : (
          rides.data.map((r) => (
            <Card key={r.id} className="mb-2" onPress={() => router.push(`/rider/booking/${r.id}`)}>
              <Row className="justify-between">
                <Muted>{dayAndTime(r.when)}</Muted>
                <StatusPill status={r.status} />
              </Row>
              <Row className="mt-1.5">
                <Text className="flex-1 text-base font-bold text-ink">
                  {r.fromStopName} → {r.toStopName}
                </Text>
                <Ionicons name="chevron-forward" size={18} color={colors.ink3} />
              </Row>
              <Muted className="mt-0.5">
                {r.rideType === 'SHARED' ? 'Shared' : 'Exclusive'}
                {r.distanceKm != null ? ` · ${km(r.distanceKm)}` : ''}
                {r.vehicleCode ? ` · ${r.vehicleCode}` : ''}
                {r.costAmount != null ? ` · ${rupees(r.costAmount)}` : ''}
                {r.rating ? ` · ${r.rating}★` : ''}
              </Muted>
              {r.cancelReason ? <Muted className="mt-1 italic">{r.cancelReason}</Muted> : null}
            </Card>
          ))
        )}
      </View>
    </Screen>
  );
}
