import { decodePolyline, distanceM, fmt, toApiError, type LatLng, type RiderRide } from '@ecoride/core';
import {
  Banner,
  Button,
  callPhone,
  colors,
  confirmAction,
  IconButton,
  Ionicons,
  Loading,
  MapMarker,
  MapRoute,
  RideMap,
  RouteSummary,
  Row,
  Sheet,
  showMessage,
  space,
  T,
  useDeviceLocation,
  useNow,
  useReceivedAt,
  VehicleIcon,
} from '@ecoride/mobile';
import { useQueryClient } from '@tanstack/react-query';
import { router, useLocalSearchParams } from 'expo-router';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Pressable, Share, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { DriverRow, PinBox, ProgressBar, SearchingBar, Stat } from '@/components/RideParts';
import { api, API_URL } from '@/lib/api';
import { useBooking } from '@/lib/booking';
import { keys, useRide } from '@/lib/queries';

/** The rider shares their position with the driver only while near the pickup (and waiting). */
const SHARE_WITHIN_M = 3000;

export default function RideScreen() {
  const insets = useSafeAreaInsets();
  const { id } = useLocalSearchParams<{ id: string }>();
  const rideId = Number(id);
  const queryClient = useQueryClient();
  const { data: ride, error } = useRide(rideId);
  const receivedAt = useReceivedAt(ride);
  const now = useNow(1000);

  useEffect(() => {
    if (ride?.status === 'COMPLETED') router.replace(`/trip/${ride.id}`);
  }, [ride?.status, ride?.id]);

  // Share the rider's own position with the driver while the cab is coming.
  const waiting = ride?.status === 'ACCEPTED' || ride?.status === 'ARRIVED';
  const lastShare = useRef(0);
  const onFix = useCallback(
    (fix: { lat: number; lng: number; accuracyM: number | null }) => {
      if (!ride || Date.now() - lastShare.current < 8000) return;
      if (distanceM(fix, ride.pickup) > SHARE_WITHIN_M) return;
      lastShare.current = Date.now();
      void api.shareLocation(ride.id, fix).catch(() => undefined);
    },
    [ride],
  );
  const { fix } = useDeviceLocation(waiting, onFix);

  const refresh = () => queryClient.invalidateQueries({ queryKey: keys.ride(rideId) });

  if (error && !ride) {
    return (
      <View style={{ flex: 1, padding: space.xl, paddingTop: insets.top + space.xl }}>
        <Banner tone="danger" message={toApiError(error, API_URL).message} />
        <Button label="Back to home" variant="secondary" style={{ marginTop: space.lg }} onPress={() => router.replace('/home')} />
      </View>
    );
  }
  if (!ride) return <Loading label="Loading your ride…" />;

  const elapsed = (now - receivedAt) / 1000;
  return (
    <View style={{ flex: 1 }}>
      <RideLiveMap ride={ride} me={waiting && fix && distanceM(fix, ride.pickup) < SHARE_WITHIN_M ? fix : null} topInset={insets.top} />
      <View style={{ position: 'absolute', top: insets.top + space.md, left: space.lg, right: space.lg, flexDirection: 'row', justifyContent: 'space-between' }}>
        <IconButton icon="arrow-back" label="Home" onPress={() => router.replace('/home')} size={44} />
        {ride.status === 'IN_PROGRESS' || waiting ? (
          <Pressable onPress={() => sos(ride)} accessibilityRole="button" accessibilityLabel="Emergency SOS">
            <View style={{ backgroundColor: colors.danger, borderRadius: 22, height: 44, paddingHorizontal: 16, flexDirection: 'row', alignItems: 'center', gap: 6 }}>
              <Ionicons name="shield-checkmark" size={18} color={colors.white} />
              <T v="bodyStrong" style={{ color: colors.white }}>
                SOS
              </T>
            </View>
          </Pressable>
        ) : null}
      </View>
      <View style={{ position: 'absolute', left: 0, right: 0, bottom: 0 }}>
        <View style={{ maxWidth: 560, width: '100%', alignSelf: 'center' }}>
          <Sheet>
            {ride.status === 'SEARCHING' ? <Searching ride={ride} elapsed={elapsed} onChanged={refresh} /> : null}
            {ride.status === 'ACCEPTED' ? <OnTheWay ride={ride} elapsed={elapsed} onChanged={refresh} /> : null}
            {ride.status === 'ARRIVED' ? <Arrived ride={ride} elapsed={elapsed} onChanged={refresh} /> : null}
            {ride.status === 'IN_PROGRESS' ? <OnTrip ride={ride} /> : null}
            {ride.status === 'NO_DRIVERS' || ride.status === 'CANCELLED' ? <Ended ride={ride} /> : null}
            {ride.status === 'COMPLETED' ? <Loading label="Trip complete" /> : null}
          </Sheet>
        </View>
      </View>
    </View>
  );
}

function RideLiveMap({ ride, me, topInset }: { ride: RiderRide; me: LatLng | null; topInset: number }) {
  const route = useMemo(() => decodePolyline(ride.routePolyline), [ride.routePolyline]);
  const toPickup = useMemo(() => decodePolyline(ride.pickupPolyline), [ride.pickupPolyline]);
  const car = ride.driver?.position ?? null;

  const routes: MapRoute[] = [];
  if (ride.status === 'ACCEPTED' && toPickup.length) {
    routes.push({ id: 'trip', points: route, color: '#9CA3AF', width: 4 });
    routes.push({ id: 'to-pickup', points: toPickup, color: colors.pickupRoute, width: 5 });
  } else {
    routes.push({ id: 'trip', points: route, color: colors.route, width: 5 });
  }

  const markers: MapMarker[] = [
    { id: 'pickup', kind: 'pickup', lat: ride.pickup.lat, lng: ride.pickup.lng, label: ride.status === 'ARRIVED' ? 'Your driver is here' : null },
    { id: 'drop', kind: 'drop', lat: ride.drop.lat, lng: ride.drop.lng },
  ];
  if (car && ride.status !== 'SEARCHING') {
    const label =
      ride.status === 'ACCEPTED' && ride.live.toPickupEtaS != null
        ? fmt.minutes(ride.live.toPickupEtaS)
        : ride.status === 'IN_PROGRESS' && ride.live.remainingEtaS != null
          ? fmt.minutes(ride.live.remainingEtaS)
          : null;
    markers.push({ id: 'cab', kind: 'car', lat: car.lat, lng: car.lng, heading: car.heading, vehicleType: ride.vehicleType, highlight: true, label });
  }
  if (me) markers.push({ id: 'me', kind: 'me', lat: me.lat, lng: me.lng });

  let fitPoints: LatLng[] = route;
  if (ride.status === 'ACCEPTED' && car) fitPoints = [car, ride.pickup, ...toPickup];
  if (ride.status === 'ARRIVED') fitPoints = car ? [car, ride.pickup] : [ride.pickup];
  if (ride.status === 'IN_PROGRESS' && car) fitPoints = [car, ride.drop];
  // Re-fit when the stage changes, and every ~30 s while the cab moves.
  const bucket = Math.floor(Date.now() / 30000);

  return (
    <RideMap
      initialCenter={ride.pickup}
      initialZoom={14}
      routes={routes}
      markers={markers}
      fit={{ key: `${ride.status}-${ride.status === 'SEARCHING' ? 0 : bucket}`, points: fitPoints }}
      padding={{ top: topInset + 80, right: 50, bottom: 420, left: 50 }}
    />
  );
}

function Searching({ ride, elapsed, onChanged }: { ride: RiderRide; elapsed: number; onChanged: () => void }) {
  const [busy, setBusy] = useState(false);
  const s = ride.search;
  const driverCancelled = ride.timeline.some((e) => e.type === 'DRIVER_CANCELLED');
  return (
    <View testID="ride-searching">
      {driverCancelled ? (
        <View style={{ marginBottom: space.md }}>
          <Banner tone="warn" message="Your driver had to cancel. We're finding you another driver at the same price." />
        </View>
      ) : null}
      <T v="h1">Finding your ride</T>
      <SearchingBar />
      <T v="muted">
        {s && s.driversContacted > 0
          ? `Asking drivers near you · ${s.driversContacted} contacted`
          : 'Looking for drivers near your pickup'}
        {s ? ` · ${fmt.clock(s.elapsedS + elapsed)}` : ''}
      </T>
      <View style={{ marginTop: space.lg }}>
        <RouteSummary pickup={ride.pickup.address} drop={ride.drop.address} compact />
      </View>
      <Row style={{ marginTop: space.lg }} gap={space.md}>
        <VehicleIcon type={ride.vehicleType} size={40} />
        <T v="bodyStrong" style={{ flex: 1 }}>
          {ride.vehicleTypeName} · {fmt.PAYMENT_LABEL[ride.fare.method]}
        </T>
        <T v="h2">{fmt.rupees(ride.fare.total)}</T>
      </Row>
      <Button
        label="Cancel request"
        variant="secondary"
        style={{ marginTop: space.lg }}
        loading={busy}
        onPress={async () => {
          setBusy(true);
          try {
            await api.cancel(ride.id, 'Changed my plans');
            onChanged();
          } catch (e) {
            showMessage('Could not cancel', toApiError(e, API_URL).message);
          } finally {
            setBusy(false);
          }
        }}
      />
    </View>
  );
}

function OnTheWay({ ride, elapsed, onChanged }: { ride: RiderRide; elapsed: number; onChanged: () => void }) {
  const live = ride.live;
  const eta = live.toPickupEtaS;
  return (
    <View testID="ride-accepted">
      <Row style={{ justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <View style={{ flex: 1 }}>
          <T v="h1">{eta == null ? 'Driver on the way' : eta < 45 ? 'Arriving now' : `Arriving in ${fmt.minutes(eta)}`}</T>
          <T v="muted" style={{ marginTop: 2 }}>
            {live.toPickupM != null ? `${fmt.distance(live.toPickupM)} away` : 'Locating the cab'}
            {live.offRoute ? ' · taking another road' : ''}
          </T>
        </View>
        {ride.otp ? <PinBox pin={ride.otp} /> : null}
      </Row>
      <ProgressBar value={live.progress} />
      {ride.driver ? <DriverRow driver={ride.driver} /> : null}
      <Actions ride={ride} />
      <CancelLink ride={ride} elapsed={elapsed} onChanged={onChanged} />
    </View>
  );
}

function Arrived({ ride, elapsed, onChanged }: { ride: RiderRide; elapsed: number; onChanged: () => void }) {
  const free = ride.live.freeWaitLeftS != null ? Math.max(0, ride.live.freeWaitLeftS - elapsed) : null;
  return (
    <View testID="ride-arrived">
      <Row style={{ justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <View style={{ flex: 1 }}>
          <T v="h1">Your driver has arrived</T>
          <T v="muted" style={{ marginTop: 2 }}>
            Share your PIN to start the trip
          </T>
        </View>
        {ride.otp ? <PinBox pin={ride.otp} /> : null}
      </Row>
      <View style={{ marginTop: space.md }}>
        <Banner
          tone={free && free > 0 ? 'brand' : 'warn'}
          icon="time-outline"
          message={free && free > 0 ? `Free waiting: ${fmt.clock(free)} left. Waiting charges apply after that.` : 'Free waiting is over: waiting charges now apply to your fare.'}
        />
      </View>
      {ride.driver ? <DriverRow driver={ride.driver} /> : null}
      <Actions ride={ride} />
      <CancelLink ride={ride} elapsed={elapsed} onChanged={onChanged} />
    </View>
  );
}

function OnTrip({ ride }: { ride: RiderRide }) {
  const live = ride.live;
  return (
    <View testID="ride-on-trip">
      <T v="h1" numberOfLines={1}>
        Heading to {fmt.shortPlace(ride.drop.address)}
      </T>
      <T v="muted" style={{ marginTop: 2 }}>
        {live.remainingEtaS != null ? `Arriving by ${fmt.timeIn(live.remainingEtaS)}` : 'On the way'}
        {live.remainingM != null ? ` · ${fmt.distance(live.remainingM)} left` : ''}
      </T>
      <ProgressBar value={live.progress} color={colors.ink} />
      <Row gap={space.sm}>
        <Stat label="Driven (GPS)" value={fmt.distance(live.travelledM)} />
        <Stat label="Planned route" value={fmt.distance(ride.estDistanceM)} />
        <Stat label={`Fare · ${fmt.PAYMENT_LABEL[ride.fare.method]}`} value={fmt.rupees(ride.fare.total)} />
      </Row>
      {ride.driver ? <DriverRow driver={ride.driver} /> : null}
      <Actions ride={ride} />
    </View>
  );
}

function Ended({ ride }: { ride: RiderRide }) {
  const booking = useBooking();
  const noDrivers = ride.status === 'NO_DRIVERS';
  return (
    <View testID="ride-ended">
      <Row gap={space.md}>
        <View style={{ width: 44, height: 44, borderRadius: 22, backgroundColor: colors.mist, alignItems: 'center', justifyContent: 'center' }}>
          <Ionicons name={noDrivers ? 'car-outline' : 'close'} size={22} color={colors.ink} />
        </View>
        <View style={{ flex: 1 }}>
          <T v="h1">{noDrivers ? 'No cars available' : 'Ride cancelled'}</T>
          <T v="muted">
            {noDrivers
              ? 'Every driver nearby is busy right now. Try again in a minute or choose another ride type.'
              : `${ride.cancelledBy === 'RIDER' ? 'You cancelled' : ride.cancelledBy === 'DRIVER' ? 'The driver cancelled' : 'Cancelled by EcoRide'}${ride.cancelReason ? `: ${ride.cancelReason}` : ''}`}
          </T>
        </View>
      </Row>
      {ride.fare.cancellationFee > 0 ? (
        <View style={{ marginTop: space.md }}>
          <Banner tone="warn" message={`A cancellation fee of ${fmt.rupees(ride.fare.cancellationFee)} was charged.`} />
        </View>
      ) : null}
      <Button
        label="Book again"
        style={{ marginTop: space.lg }}
        onPress={() => {
          booking.setPickup(ride.pickup, false);
          booking.setDrop(ride.drop);
          router.replace('/choose-ride');
        }}
      />
      <Button label="Back to home" variant="ghost" style={{ marginTop: space.sm }} onPress={() => router.replace('/home')} />
    </View>
  );
}

function Actions({ ride }: { ride: RiderRide }) {
  return (
    <Row style={{ marginTop: space.lg }} gap={space.sm}>
      <Button label="Call" icon="call" variant="secondary" size="md" style={{ flex: 1 }} onPress={() => ride.driver && callPhone(ride.driver.phone)} />
      <Button label="Share trip" icon="share-social" variant="secondary" size="md" style={{ flex: 1 }} onPress={() => shareTrip(ride)} />
    </Row>
  );
}

function CancelLink({ ride, elapsed, onChanged }: { ride: RiderRide; elapsed: number; onChanged: () => void }) {
  const freeLeft = ride.freeCancelLeftS != null ? Math.max(0, ride.freeCancelLeftS - elapsed) : null;
  const charged = ride.status === 'ARRIVED' || (freeLeft != null && freeLeft <= 0);
  return (
    <Pressable
      accessibilityRole="button"
      style={{ alignSelf: 'center', marginTop: space.md, padding: space.sm }}
      onPress={async () => {
        const ok = await confirmAction(
          'Cancel this ride?',
          charged
            ? `Your driver is already on the way, so a cancellation fee of ${fmt.rupees(ride.cancelFee)} applies.`
            : 'It is free to cancel now.',
          'Cancel ride',
          true,
        );
        if (!ok) return;
        try {
          await api.cancel(ride.id, charged ? 'Cancelled after the free window' : 'Changed my plans');
          onChanged();
        } catch (e) {
          showMessage('Could not cancel', toApiError(e, API_URL).message);
        }
      }}
    >
      <T v="small" style={{ color: colors.danger, fontWeight: '700' }}>
        {charged ? `Cancel ride (${fmt.rupees(ride.cancelFee)} fee)` : freeLeft != null ? `Cancel free for ${fmt.clock(freeLeft)}` : 'Cancel ride'}
      </T>
    </Pressable>
  );
}

function shareTrip(ride: RiderRide) {
  const d = ride.driver;
  const message = `I'm on an EcoRide trip (${ride.code}) from ${fmt.shortPlace(ride.pickup.address)} to ${fmt.shortPlace(ride.drop.address)}.${
    d ? ` Driver ${d.name}, ${d.vehicle.color} ${d.vehicle.make} ${d.vehicle.model} ${d.vehicle.plate}.` : ''
  }`;
  void Share.share({ message }).catch(() => showMessage('Share trip', message));
}

async function sos(ride: RiderRide) {
  const ok = await confirmAction(
    'Emergency SOS',
    'This alerts the EcoRide safety team with your ride and live location. For immediate danger, also call 112.',
    'Send SOS',
    true,
  );
  if (!ok) return;
  try {
    const res = await api.sos(ride.id, { lat: ride.driver?.position?.lat, lng: ride.driver?.position?.lng, note: 'Raised from the rider app' });
    showMessage('Help is on the way', res.message);
  } catch (e) {
    showMessage('SOS failed', `${toApiError(e, API_URL).message} Call 112.`);
  }
}
