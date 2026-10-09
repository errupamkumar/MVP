import { Ionicons } from '@expo/vector-icons';
import * as Clipboard from 'expo-clipboard';
import { Stack, useLocalSearchParams, useRouter } from 'expo-router';
import { ComponentProps, useState } from 'react';
import { Linking, Platform, Pressable, Share, Text, TextInput, View } from 'react-native';

import { trackingLink } from '@/api/client';
import type { AlternativeSpotDto, BookingDto, RatingTag } from '@/api/types';
import { LiveMap, type MapMarker, type MapStop } from '@/components/LiveMap';
import { StatusPill } from '@/components/StatusPill';
import {
  Banner,
  Button,
  Card,
  Chip,
  ErrorBanner,
  ErrorState,
  Eyebrow,
  H1,
  KeyValue,
  LoadingState,
  Muted,
  OtpDigits,
  Pill,
  Row,
  Screen,
  SectionTitle,
} from '@/components/ui';
import { NearbyCabs, NearbySpots, SearchingCard, SearchProgress, WhenRideComes } from '@/features/availability/AvailabilityPanels';
import { useBookingDraft } from '@/features/booking/BookingDraft';
import { useBooking, useBookingAvailability, useCancelBooking, useRateBooking, useRiderSos, useSearchAgain } from '@/hooks/queries';
import { useRiderLocationSharing } from '@/hooks/useRiderLocationSharing';
import { confirmAction, showMessage } from '@/lib/dialogs';
import { ago, dayAndTime, distance, km, minutes, rupees, time } from '@/lib/format';
import { colors } from '@/lib/theme';

type IconName = ComponentProps<typeof Ionicons>['name'];

export default function BookingScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const bookingId = Number(id);
  const booking = useBooking(bookingId);

  if (!Number.isFinite(bookingId)) {
    return (
      <Screen topInset={false}>
        <ErrorState error={new Error('This booking link is not valid.')} />
      </Screen>
    );
  }
  if (booking.isPending) {
    return (
      <Screen topInset={false}>
        <LoadingState label="Loading your ride…" />
      </Screen>
    );
  }
  if (booking.isError) {
    return (
      <Screen topInset={false}>
        <ErrorState error={booking.error} onRetry={() => booking.refetch()} />
      </Screen>
    );
  }
  return <BookingView b={booking.data} refreshing={booking.isRefetching} onRefresh={() => booking.refetch()} />;
}

function BookingView({ b, refreshing, onRefresh }: { b: BookingDto; refreshing: boolean; onRefresh: () => void }) {
  const p = b.progress;
  const cancel = useCancelBooking();
  const sos = useRiderSos();
  const live = ['ASSIGNED', 'ONBOARD'].includes(b.status);

  const onCancel = async () => {
    if (await confirmAction('Cancel this ride?', `${b.fromStop.name} to ${b.toStop.name}`, 'Cancel ride', true)) {
      cancel.mutate({ id: b.id });
    }
  };

  const onSos = async () => {
    const ok = await confirmAction(
      'Send SOS?',
      'The transport desk and plant security get your cab and its location immediately.',
      'Send SOS',
      true,
    );
    if (ok) {
      sos.mutate(
        { bookingId: b.id },
        { onSuccess: (r) => showMessage('Help is on the way', r.message), onError: (e) => showMessage('SOS not sent', e.message) },
      );
    }
  };

  return (
    <Screen topInset={false} refreshing={refreshing} onRefresh={onRefresh}>
      <Stack.Screen options={{ title: b.bookingCode }} />
      <Row className="justify-between">
        <StatusPill status={b.status} />
        <Muted>{b.route.label}</Muted>
      </Row>
      <H1 className="mt-2">{p.stageLabel}</H1>
      <Muted className="mt-0.5">
        {b.fromStop.name} → {b.toStop.name}
        {b.scheduledAt ? ` · pickup ${dayAndTime(b.scheduledAt)}` : ''}
      </Muted>

      {b.status === 'ASSIGNED' || b.status === 'ONBOARD' ? <Eta b={b} /> : null}
      {b.status === 'ASSIGNED' || b.status === 'ONBOARD' ? <ProgressSteps b={b} /> : null}

      {b.status === 'REQUESTED' ? <WaitingForCab b={b} /> : null}
      {b.status === 'PENDING_APPROVAL' ? (
        <Banner tone="amber" icon="shield-checkmark" title="Waiting for approval" message="Exclusive rides are released by the transport desk or your HOD." />
      ) : null}
      {b.status === 'SCHEDULED' ? (
        <Banner tone="teal" icon="calendar" title={`Scheduled for ${dayAndTime(b.scheduledAt)}`} message="A cab is assigned about 20 minutes before pickup." />
      ) : null}
      {b.status === 'NO_SHOW' || b.status === 'CANCELLED' || b.status === 'REJECTED' ? (
        <Banner tone={b.status === 'NO_SHOW' ? 'danger' : 'amber'} icon="close-circle" title={p.stageLabel} message={b.cancelReason ?? undefined} />
      ) : null}

      {p.stage === 'AT_PICKUP' && b.otp ? (
        <Card className="mt-4 items-center border-accent">
          <Eyebrow>Show this to the driver</Eyebrow>
          <View className="my-3">
            <OtpDigits code={b.otp} />
          </View>
          <Muted className="text-center">
            {b.vehicle?.code} is at {b.fromStop.name}. The driver types this code to start your trip.
          </Muted>
          <Muted className="mt-1 text-center">
            {p.seatsTaken} of {p.capacity} riders on this pick-up
            {b.vehicle && b.vehicle.seatCapacity > p.capacity ? ` · seat ${b.vehicle.seatCapacity} held free by policy` : ''}
          </Muted>
        </Card>
      ) : null}

      {live && b.cabLocation ? <CabOnMap b={b} /> : null}
      {b.status === 'ASSIGNED' && b.driver ? <ShareMyLocation b={b} /> : null}
      {b.vehicle && live ? <VehicleCard b={b} /> : null}

      {b.status === 'ASSIGNED' && p.stage !== 'AT_PICKUP' && b.otp ? (
        <Card className="mt-3 items-center">
          <Eyebrow>Boarding OTP</Eyebrow>
          <View className="my-3">
            <OtpDigits code={b.otp} />
          </View>
          <Muted>Keep this ready. The driver asks for it when you board.</Muted>
        </Card>
      ) : null}

      {live ? <TrackingCard b={b} /> : null}

      {b.status === 'ONBOARD' || (b.status === 'ASSIGNED' && p.stops.length > 2) ? (
        <>
          <SectionTitle title="Stops on this run" />
          <Card>
            {p.stops.map((s) => (
              <Row key={s.seq} className="py-2">
                <View
                  className={`h-3.5 w-3.5 rounded-full border-2 ${
                    s.state === 'HERE' ? 'border-accent bg-accent' : s.state === 'PASSED' ? 'border-ink-3 bg-ink-3' : s.state === 'NEXT' ? 'border-accent' : 'border-line'
                  }`}
                />
                <Text className={`ml-3 flex-1 text-base ${s.state === 'PASSED' ? 'text-ink-3' : 'font-semibold text-ink'}`}>{s.stopName}</Text>
                <Muted>
                  {s.state === 'HERE' ? 'cab is here' : s.state === 'NEXT' ? 'next stop' : s.pickup ? 'your pickup' : s.drop ? 'your drop' : s.state === 'PASSED' ? 'passed' : ''}
                </Muted>
              </Row>
            ))}
            {p.coRiders > 0 ? (
              <Muted className="mt-2">
                Sharing with {p.coRiders} {p.coRiders === 1 ? 'colleague' : 'colleagues'} · {p.seatsTaken} of {p.capacity} seats used
              </Muted>
            ) : null}
          </Card>
        </>
      ) : null}

      {b.status === 'COMPLETED' ? <CompletedCard b={b} /> : null}
      {b.canRate ? <RatingCard b={b} /> : null}
      {b.status === 'COMPLETED' && b.rating ? (
        <Card className="mt-3 flex-row items-center">
          <Ionicons name="star" size={20} color={colors.accent} />
          <Text className="ml-2 text-base text-ink">
            You rated this ride {b.rating}/5{b.ratingTags.length ? ` · ${b.ratingTags.map((t) => t.replace('_', ' ').toLowerCase()).join(', ')}` : ''}
          </Text>
        </Card>
      ) : null}

      <SectionTitle title="Booking" />
      <Card>
        <KeyValue label="Booking" value={b.bookingCode} />
        <KeyValue label="Booked" value={dayAndTime(b.createdAt)} />
        <KeyValue label="Rider" value={b.forGuest ? `${b.riderName} (guest)` : b.riderName} />
        <KeyValue label="Ride" value={`${b.rideType === 'SHARED' ? 'Shared' : 'Exclusive'} · ${b.seats} ${b.seats === 1 ? 'seat' : 'seats'}`} />
        <KeyValue label="Distance" value={`${km(b.rideKm)} · about ${minutes(b.rideMinutes)}`} />
        <KeyValue label="Charged to" value={`${b.costCentreCode} · ${b.costCentreName}`} />
      </Card>

      {cancel.isError ? <View className="mt-3"><ErrorBanner error={cancel.error} /></View> : null}
      <View className="mt-5">
        {live ? <Button label="SOS" variant="danger" icon="warning" onPress={onSos} loading={sos.isPending} /> : null}
        {b.canCancel ? (
          <Button label="Cancel ride" variant="secondary" icon="close" onPress={onCancel} loading={cancel.isPending} className="mt-3" />
        ) : null}
      </View>
    </Screen>
  );
}

function Eta({ b }: { b: BookingDto }) {
  const p = b.progress;
  const onTrip = p.stage === 'ON_TRIP';
  const eta = onTrip ? p.etaToDropMinutes : p.etaToPickupMinutes;
  if (p.stage === 'AT_PICKUP' || eta == null) return null;
  return (
    <Card className="mt-4 flex-row items-center">
      <View className="flex-1">
        <Muted>
          {eta < 1
            ? onTrip
              ? `Almost at ${b.toStop.name}`
              : `Your cab is at or near ${b.fromStop.name}`
            : onTrip
              ? `Drop at ${b.toStop.name} in`
              : `Arriving at ${b.fromStop.name} in`}
        </Muted>
        <Text className="text-4xl font-bold text-ink">{eta < 1 ? 'Any moment' : minutes(eta)}</Text>
        {b.cabLocation?.live ? (
          <Muted>Live GPS{b.cabLocation.nearStopName ? ` · near ${b.cabLocation.nearStopName}` : ''}</Muted>
        ) : p.vehicleAt ? (
          <Muted>Last seen at {p.vehicleAt}</Muted>
        ) : null}
      </View>
      <View className="h-14 w-14 items-center justify-center rounded-full bg-accent-soft">
        <Ionicons name={onTrip ? 'navigate' : 'car'} size={26} color={colors.accent} />
      </View>
    </Card>
  );
}

const STEPS: { key: string; label: string }[] = [
  { key: 'BOOKED', label: 'Booked' },
  { key: 'ASSIGNED', label: 'Driver assigned' },
  { key: 'ON_THE_WAY', label: 'On the way' },
  { key: 'AT_PICKUP', label: 'At your stop' },
  { key: 'ON_TRIP', label: 'On trip' },
];

function ProgressSteps({ b }: { b: BookingDto }) {
  const reached = b.progress.stage === 'ON_TRIP' ? 4 : b.progress.stage === 'AT_PICKUP' ? 3 : 2;
  return (
    <Row className="mt-4 justify-between px-1">
      {STEPS.map((s, i) => (
        <View key={s.key} className="flex-1 items-center">
          <View className={`h-3 w-3 rounded-full ${i <= reached ? 'bg-accent' : 'bg-line'}`} />
          <Text className={`mt-1 text-center text-[11px] ${i <= reached ? 'font-semibold text-ink' : 'text-ink-3'}`}>{s.label}</Text>
        </View>
      ))}
    </Row>
  );
}

/**
 * A request no cab could take yet: live search progress with Search again now,
 * when a ride can reach the pickup, nearby pickup points (rebook from one),
 * and every cab on duty with its driver.
 */
function WaitingForCab({ b }: { b: BookingDto }) {
  const router = useRouter();
  const { update } = useBookingDraft();
  const availability = useBookingAvailability(b.id, true);
  const searchAgain = useSearchAgain();
  const cancel = useCancelBooking();
  const [openedAt] = useState(() => Date.now());

  const onSearchAgain = () => searchAgain.mutate(b.id, { onSettled: () => void availability.refetch() });

  const rebook = async (s: AlternativeSpotDto) => {
    const ok = await confirmAction(
      `Rebook from ${s.stop.name}?`,
      `Your request from ${b.fromStop.name} is cancelled, and you choose a ride from ${s.stop.name} (${s.walkMinutes} min walk).`,
      'Rebook',
    );
    if (!ok) return;
    cancel.mutate(
      { id: b.id, reason: `Rebooked from ${s.stop.name}` },
      {
        onSuccess: () => {
          update({
            from: { id: s.stop.id, name: s.stop.name },
            to: { id: b.toStop.id, name: b.toStop.name },
            seats: b.seats,
            when: { kind: 'now' },
            riderName: b.riderName,
            riderPhone: b.riderPhone,
            riderEmail: b.riderEmail ?? '',
            forGuest: b.forGuest,
          });
          router.replace('/rider/options');
        },
      },
    );
  };

  if (!availability.data) {
    return availability.isError ? (
      <View className="mt-4"><ErrorBanner error={availability.error} /></View>
    ) : (
      <SearchingCard startedAt={openedAt} pickupName={b.fromStop.name} />
    );
  }
  const a = availability.data;
  return (
    <View>
      {a.search ? (
        <SearchProgress search={a.search} receivedAt={availability.dataUpdatedAt} onSearchAgain={onSearchAgain} busy={searchAgain.isPending} />
      ) : null}
      {searchAgain.isError ? <View className="mt-3"><ErrorBanner error={searchAgain.error} /></View> : null}
      {cancel.isError ? <View className="mt-3"><ErrorBanner error={cancel.error} /></View> : null}
      <WhenRideComes a={a} />
      <NearbySpots a={a} actionLabel="Rebook from here" onUse={rebook} busy={cancel.isPending} />
      <NearbyCabs a={a} />
    </View>
  );
}

function VehicleCard({ b }: { b: BookingDto }) {
  return (
    <Card className="mt-3">
      <Row className="justify-between">
        <View>
          <Muted>Vehicle number</Muted>
          <Text className="text-xl font-bold text-ink">{b.vehicle?.registrationNo}</Text>
          <Muted>
            {b.vehicle?.vehicleType === 'SHUTTLE' ? 'Shuttle' : 'Cab'} {b.vehicle?.code} · {b.vehicle?.seatCapacity} seats
          </Muted>
        </View>
        <View className="h-12 w-12 items-center justify-center rounded-full bg-ink">
          <Ionicons name="car" size={22} color={colors.white} />
        </View>
      </Row>
      {b.driver ? (
        <Row className="mt-3 justify-between border-t border-line pt-3">
          <View className="flex-1">
            <Muted>Driver</Muted>
            <Text className="text-base font-semibold text-ink">{b.driver.name}</Text>
            <Muted>{b.driver.phone}</Muted>
            <Muted>{b.driver.fleet}</Muted>
          </View>
          <Button
            label="Call"
            icon="call"
            size="md"
            variant="secondary"
            onPress={() => Linking.openURL(`tel:${b.driver?.phone.replace(/\s/g, '')}`)}
          />
        </Row>
      ) : null}
      {b.promisedPickupAt && b.status === 'ASSIGNED' ? (
        <Muted className="mt-2">Promised pickup by {time(b.promisedPickupAt)}</Muted>
      ) : null}
    </Card>
  );
}

/** Where the cab is right now, on a map of this run's stops, with the rider's own shared position. */
function CabOnMap({ b }: { b: BookingDto }) {
  const cab = b.cabLocation;
  if (!cab) return null;
  const p = b.progress;
  const onBoard = b.status === 'ONBOARD';
  const stops: MapStop[] = p.stops.map((s) => ({
    key: String(s.seq),
    name: s.stopName,
    latitude: s.latitude,
    longitude: s.longitude,
    tone: s.pickup && !onBoard ? 'highlight' : s.drop ? 'target' : s.state === 'PASSED' ? 'passed' : 'normal',
  }));
  const markers: MapMarker[] = [];
  if (b.myLocation && !onBoard) {
    markers.push({
      key: 'me',
      kind: 'rider',
      label: 'You',
      latitude: b.myLocation.latitude,
      longitude: b.myLocation.longitude,
      live: b.myLocation.live,
      accuracyM: b.myLocation.accuracyM,
    });
  }
  markers.push({ key: 'cab', kind: 'cab', label: cab.vehicleCode, latitude: cab.latitude, longitude: cab.longitude, live: cab.live, accuracyM: cab.accuracyM });
  // Fit the view to the cab and where it is heading for this rider: their pickup (and them), or their drop.
  const target = p.stops.find((s) => (onBoard ? s.drop : s.pickup));
  const focus = [...markers.map((m) => m.key), ...(target ? [String(target.seq)] : [])];

  const headline =
    p.stage === 'AT_PICKUP'
      ? `${cab.vehicleCode} is at ${b.fromStop.name}`
      : cab.live && cab.metresToStop != null && cab.towardsStopName
        ? `${cab.vehicleCode} is ${distance(cab.metresToStop)} from ${cab.towardsStopName}`
        : `${cab.vehicleCode} was last seen${cab.nearStopName ? ` near ${cab.nearStopName}` : ''}`;
  const detail = cab.live
    ? [
        cab.speedKmh == null ? null : cab.speedKmh >= 3 ? `Moving at ${Math.round(cab.speedKmh)} km/h` : 'Stopped',
        cab.nearStopName ? `near ${cab.nearStopName}` : null,
        `updated ${ago(cab.secondsAgo)}`,
      ]
        .filter(Boolean)
        .join(' · ')
    : `The driver's GPS is not live${cab.secondsAgo != null ? ` (last update ${ago(cab.secondsAgo)})` : ''}. Times above follow the route timetable.`;
  return (
    <Card className="mt-3">
      <Row className="justify-between">
        <Eyebrow>Where your cab is</Eyebrow>
        <Pill label={cab.live ? 'Live' : 'GPS not live'} tone={cab.live ? 'teal' : 'neutral'} />
      </Row>
      <Text className="mt-1 text-lg font-bold text-ink">{headline}</Text>
      <Muted>{detail}</Muted>
      <View className="mt-3">
        <LiveMap stops={stops} markers={markers} focus={focus} />
      </View>
      {b.driver ? (
        <Muted className="mt-2">
          {b.driver.name} · {b.vehicle?.registrationNo} · straight-line distances
        </Muted>
      ) : null}
    </Card>
  );
}

/** The rider's own position, shared with the driver coming for them until they board. */
function ShareMyLocation({ b }: { b: BookingDto }) {
  const sharing = useRiderLocationSharing(b.id, true);
  const driver = b.driver?.name ?? 'your driver';
  const mine = b.myLocation;
  const ack = sharing.lastAck;

  if (sharing.stopped) {
    return (
      <Card className="mt-3">
        <Row>
          <Ionicons name="location-outline" size={22} color={colors.ink3} />
          <View className="ml-2 flex-1">
            <Text className="text-base font-bold text-ink">Location sharing is off</Text>
            <Muted>
              {driver} looks for you at the {b.fromStop.name} pickup point.
            </Muted>
          </View>
        </Row>
        <Button label="Share my location" icon="location" size="md" variant="secondary" onPress={sharing.resume} className="mt-3" />
      </Card>
    );
  }
  if (sharing.permission === 'denied' || sharing.permission === 'unsupported') {
    return (
      <View className="mt-3">
        <Banner
          tone="amber"
          icon="location"
          title="Your location is not shared"
          message={
            sharing.permission === 'denied'
              ? `Allow location for Plant Ride so ${driver} can find you at ${b.fromStop.name}.`
              : `This device cannot share its location. ${driver} looks for you at ${b.fromStop.name}.`
          }
        />
      </View>
    );
  }
  if (ack?.outsideServiceArea) {
    return (
      <View className="mt-3">
        <Banner tone="amber" icon="locate" title="Your location is not shared" message={ack.message ?? undefined} />
      </View>
    );
  }

  const metres = mine?.metresToPickup ?? ack?.metresToPickup ?? null;
  const atPickup = mine?.atPickup ?? ack?.atPickup ?? false;
  const shared = metres != null;
  return (
    <Card className="mt-3">
      <Row>
        <View className={`h-10 w-10 items-center justify-center rounded-full ${shared ? 'bg-teal-soft' : 'bg-mist'}`}>
          <Ionicons name="location" size={20} color={shared ? colors.teal : colors.ink3} />
        </View>
        <View className="ml-3 flex-1">
          <Text className="text-base font-bold text-ink">
            {shared ? `Sharing your location with ${ack?.sharedWith ?? driver}` : 'Finding your location…'}
          </Text>
          <Muted>
            {!shared
              ? `So ${driver} can find you at ${b.fromStop.name}.`
              : `${atPickup ? `You are at the ${b.fromStop.name} pickup point` : `You are ${distance(metres)} from ${b.fromStop.name}`}${
                  mine ? ` · updated ${ago(mine.secondsAgo)}` : ''
                }`}
          </Muted>
        </View>
      </Row>
      <Muted className="mt-2">Only your driver sees it, and only until you board.</Muted>
      <Button label="Stop sharing" icon="eye-off" size="sm" variant="ghost" onPress={sharing.stop} className="mt-1 self-start" />
    </Card>
  );
}

function TrackingCard({ b }: { b: BookingDto }) {
  const url = trackingLink(b.trackingToken);
  const [copied, setCopied] = useState(false);
  const share = async () => {
    const message = `Track my Plant Ride cab ${b.vehicle?.code ?? ''}: ${url}`;
    try {
      if (Platform.OS === 'web' && !(globalThis.navigator as Navigator | undefined)?.share) {
        await Clipboard.setStringAsync(url);
        setCopied(true);
        return;
      }
      await Share.share({ message, url });
    } catch {
      await Clipboard.setStringAsync(url);
      setCopied(true);
    }
  };
  return (
    <Card className="mt-3">
      <Eyebrow>Live tracking link</Eyebrow>
      <Pressable onPress={() => Linking.openURL(url)} accessibilityRole="link">
        <Text className="mt-1 text-base font-semibold text-accent" numberOfLines={1}>
          {url.replace(/^https?:\/\//, '')}
        </Text>
      </Pressable>
      <Muted className="mt-1">Shows the vehicle, never the people. Expires when the trip closes.</Muted>
      <Row className="mt-3">
        <Button label="Share trip" icon="share-social" size="sm" variant="secondary" onPress={share} className="mr-2" />
        <Button
          label={copied ? 'Copied' : 'Copy link'}
          icon={copied ? 'checkmark' : 'copy'}
          size="sm"
          variant="secondary"
          onPress={async () => {
            await Clipboard.setStringAsync(url);
            setCopied(true);
          }}
        />
      </Row>
    </Card>
  );
}

function CompletedCard({ b }: { b: BookingDto }) {
  return (
    <Card className="mt-4 bg-teal-soft">
      <Row>
        <Ionicons name="checkmark-circle" size={26} color={colors.teal} />
        <Text className="ml-2 text-lg font-bold text-ink">
          {b.fromStop.name} to {b.toStop.name}
        </Text>
      </Row>
      <Muted className="mt-1">
        {time(b.boardedAt)} – {time(b.droppedAt)} · {km(b.distanceKm)} · {b.rideType === 'SHARED' ? 'Shared' : 'Exclusive'}
      </Muted>
      <View className="mt-3 rounded-xl bg-white p-3">
        <Muted>Charged to</Muted>
        <Row className="justify-between">
          <Text className="text-base font-bold text-ink">
            {b.costCentreCode} · {b.costCentreName}
          </Text>
          <Text className="text-lg font-bold text-ink">{b.costAmount != null ? rupees(b.costAmount) : 'pending'}</Text>
        </Row>
        {b.costAmount == null ? <Muted>Final once the cab's run closes.</Muted> : null}
      </View>
    </Card>
  );
}

const TAGS: { tag: RatingTag; label: string; icon: IconName }[] = [
  { tag: 'ON_TIME', label: 'On time', icon: 'time' },
  { tag: 'CLEAN_CAB', label: 'Clean cab', icon: 'sparkles' },
  { tag: 'SAFE_DRIVING', label: 'Safe driving', icon: 'shield-checkmark' },
  { tag: 'LONG_WAIT', label: 'Long wait', icon: 'hourglass' },
  { tag: 'HARD_TO_FIND', label: 'Hard to find', icon: 'help-circle' },
];

function RatingCard({ b }: { b: BookingDto }) {
  const rate = useRateBooking();
  const [stars, setStars] = useState(0);
  const [tags, setTags] = useState<RatingTag[]>([]);
  const [note, setNote] = useState('');
  return (
    <Card className="mt-3">
      <Text className="text-lg font-bold text-ink">How was the ride?</Text>
      <Muted>Ratings feed the vendor SLA report, not just a score.</Muted>
      <Row className="my-3 justify-center">
        {[1, 2, 3, 4, 5].map((n) => (
          <Pressable key={n} onPress={() => setStars(n)} accessibilityRole="button" accessibilityLabel={`${n} star${n > 1 ? 's' : ''}`} hitSlop={6} className="mx-1.5">
            <Ionicons name={n <= stars ? 'star' : 'star-outline'} size={36} color={n <= stars ? colors.accent : colors.ink3} />
          </Pressable>
        ))}
      </Row>
      <View className="flex-row flex-wrap">
        {TAGS.map((t) => (
          <Chip
            key={t.tag}
            label={t.label}
            icon={t.icon}
            selected={tags.includes(t.tag)}
            onPress={() => setTags((cur) => (cur.includes(t.tag) ? cur.filter((x) => x !== t.tag) : [...cur, t.tag]))}
          />
        ))}
      </View>
      <TextInput
        value={note}
        onChangeText={setNote}
        placeholder="Add a note (optional)"
        placeholderTextColor={colors.ink3}
        multiline
        maxLength={500}
        className="mt-1 min-h-[70px] rounded-xl border border-line bg-white p-3 text-base text-ink"
        accessibilityLabel="Rating note"
      />
      {rate.isError ? <View className="mt-3"><ErrorBanner error={rate.error} /></View> : null}
      <Button
        label="Submit rating"
        className="mt-3"
        disabled={stars === 0}
        loading={rate.isPending}
        onPress={() => rate.mutate({ id: b.id, body: { rating: stars, tags, note: note.trim() || null } })}
      />
    </Card>
  );
}
