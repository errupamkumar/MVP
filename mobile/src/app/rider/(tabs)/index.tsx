import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { Pressable, Text, View } from 'react-native';

import type { BookingDto } from '@/api/types';
import { StatusPill } from '@/components/StatusPill';
import { Avatar, Banner, Card, ErrorState, Eyebrow, LoadingState, Muted, Row, Screen, SectionTitle } from '@/components/ui';
import { useBookingDraft } from '@/features/booking/BookingDraft';
import { useRiderHome, useStops } from '@/hooks/queries';
import { greeting, initials, minutes } from '@/lib/format';
import { colors } from '@/lib/theme';

export default function RiderHome() {
  const router = useRouter();
  const home = useRiderHome();
  const stops = useStops();
  const { update, reset } = useBookingDraft();

  if (home.isPending) {
    return (
      <Screen>
        <LoadingState label="Loading your rides…" />
      </Screen>
    );
  }
  if (home.isError) {
    return (
      <Screen>
        <ErrorState error={home.error} onRetry={() => home.refetch()} />
      </Screen>
    );
  }

  const { profile, activeBooking, savedPlaces, nextShuttle, defaultFromStopId, upcomingCount } = home.data;
  const defaultFrom = stops.data?.find((s) => s.id === defaultFromStopId) ?? null;

  const startBooking = (to?: { id: number; name: string }) => {
    reset();
    update({
      from: defaultFrom ? { id: defaultFrom.id, name: defaultFrom.name } : null,
      to: to ?? null,
      riderName: profile.fullName,
      riderPhone: profile.phone ?? '',
      riderEmail: profile.email ?? '',
    });
    router.push('/rider/book');
  };

  return (
    <Screen refreshing={home.isRefetching} onRefresh={() => home.refetch()}>
      <Row className="mb-5 justify-between">
        <View className="flex-1">
          <Muted>{greeting()},</Muted>
          <Text className="text-2xl font-bold text-ink">{profile.fullName}</Text>
        </View>
        <Avatar label={initials(profile.fullName)} />
      </Row>

      {profile.bookingPaused ? (
        <Banner
          tone="danger"
          icon="pause-circle"
          title="Booking is paused"
          message={`${profile.noShowsInWindow} no-shows in the last 30 days. Contact the transport desk to restore booking.`}
        />
      ) : null}

      {activeBooking ? <LiveRideCard booking={activeBooking} onPress={() => router.push(`/rider/booking/${activeBooking.id}`)} /> : null}

      <Pressable
        onPress={() => startBooking()}
        accessibilityRole="button"
        accessibilityLabel="Where to? Book a ride"
        disabled={profile.bookingPaused}
        className={`flex-row items-center rounded-card bg-ink px-5 py-5 active:opacity-90 ${profile.bookingPaused ? 'opacity-50' : ''}`}
      >
        <Ionicons name="search" size={22} color={colors.white} />
        <Text className="ml-3 flex-1 text-xl font-bold text-white">Where to?</Text>
        <Ionicons name="arrow-forward-circle" size={28} color={colors.accent} />
      </Pressable>

      {nextShuttle ? (
        <Card className="mt-3 flex-row items-center bg-teal-soft">
          <View className="h-10 w-10 items-center justify-center rounded-full bg-teal">
            <Ionicons name="bus" size={20} color={colors.white} />
          </View>
          <View className="ml-3 flex-1">
            <Text className="text-base font-bold text-ink">
              Shuttle {nextShuttle.vehicleCode} {nextShuttle.firstRunAt ? 'starts' : 'leaves'} {nextShuttle.stopName}
            </Text>
            <Muted>
              {nextShuttle.firstRunAt ? `at ${nextShuttle.firstRunAt}` : `in ${minutes(nextShuttle.minutesToNext)}`} ·{' '}
              {nextShuttle.routeLabel} loop every {nextShuttle.frequencyMin} min · no booking
            </Muted>
          </View>
        </Card>
      ) : null}

      <SectionTitle title="Saved places" />
      {savedPlaces.length === 0 ? (
        <Card>
          <Muted>Places you ride to often will appear here for one-tap rebooking.</Muted>
        </Card>
      ) : (
        savedPlaces.map((place) => (
          <Card
            key={place.stopId}
            className="mb-2 flex-row items-center"
            onPress={() => startBooking({ id: place.stopId, name: place.stopName })}
          >
            <View className="h-10 w-10 items-center justify-center rounded-full bg-accent-soft">
              <Ionicons name="bookmark" size={18} color={colors.accent} />
            </View>
            <View className="ml-3 flex-1">
              <Text className="text-base font-bold text-ink">{place.stopName}</Text>
              <Muted>{place.hint}</Muted>
            </View>
            <Ionicons name="chevron-forward" size={20} color={colors.ink3} />
          </Card>
        ))
      )}

      {upcomingCount > 0 ? (
        <Pressable onPress={() => router.push('/rider/rides')} accessibilityRole="link" className="mt-4">
          <Text className="text-center text-base font-semibold text-accent">
            {upcomingCount} upcoming {upcomingCount === 1 ? 'ride' : 'rides'} · see all
          </Text>
        </Pressable>
      ) : null}

      <View className="mt-6 rounded-card border border-dashed border-line p-4">
        <Eyebrow>Your cost centre</Eyebrow>
        <Text className="mt-1 text-base font-semibold text-ink">
          {profile.costCentreCode} · {profile.costCentreName}
        </Text>
        <Muted className="mt-0.5">Every ride is charged here on measured GPS distance.</Muted>
      </View>
    </Screen>
  );
}

function LiveRideCard({ booking, onPress }: { booking: BookingDto; onPress: () => void }) {
  const p = booking.progress;
  const eta = p.stage === 'ON_TRIP' ? p.etaToDropMinutes : p.etaToPickupMinutes;
  return (
    <Card className="mb-3 border-accent bg-accent-soft" onPress={onPress}>
      <Row className="justify-between">
        <StatusPill status={booking.status} />
        {booking.vehicle ? <Text className="text-sm font-bold text-ink">{booking.vehicle.code}</Text> : null}
      </Row>
      <Text className="mt-2 text-lg font-bold text-ink">{p.stageLabel}</Text>
      <Muted>
        {booking.fromStop.name} → {booking.toStop.name}
        {eta != null ? ` · ${p.stage === 'ON_TRIP' ? 'drop' : 'pickup'} in ${minutes(eta)}` : ''}
      </Muted>
      <Row className="mt-2">
        <Text className="text-sm font-semibold text-accent">Track your ride</Text>
        <Ionicons name="chevron-forward" size={16} color={colors.accent} />
      </Row>
    </Card>
  );
}
