import { fmt, toApiError, type DriverRide, type DriverState, type Offer } from '@ecoride/core';
import {
  Avatar,
  Badge,
  Banner,
  Button,
  callPhone,
  colors,
  confirmAction,
  Ionicons,
  openNavigation,
  Row,
  showMessage,
  space,
  Stars,
  T,
  VehicleIcon,
} from '@ecoride/mobile';
import { useEffect, useRef, useState } from 'react';
import { Animated, Easing, Pressable, Text, TextInput, View } from 'react-native';

import { api, API_URL } from '@/lib/api';

/** Runs an action, shows the server's message if it fails, then refreshes. */
async function act(fn: () => Promise<unknown>, refresh: () => void, title = 'Could not do that') {
  try {
    await fn();
  } catch (e) {
    showMessage(title, toApiError(e, API_URL).message);
  } finally {
    refresh();
  }
}

export function StatTile({ label, value, icon }: { label: string; value: string; icon: 'cash-outline' | 'car-outline' | 'star' | 'checkmark-done' | 'time-outline' }) {
  return (
    <View style={{ flex: 1, backgroundColor: colors.mist, borderRadius: 12, padding: space.md }}>
      <Row gap={4}>
        <Ionicons name={icon} size={13} color={icon === 'star' ? colors.gold : colors.ink3} />
        <T v="small">{label}</T>
      </Row>
      <T v="h2" style={{ marginTop: 3 }}>
        {value}
      </T>
    </View>
  );
}

// ------------------------------------------------------------------ idle / onboarding

export function IdlePanel({
  state,
  gpsMode,
  onGoOnline,
  onGoOffline,
  busy,
  refresh,
}: {
  state: DriverState;
  gpsMode: 'GPS' | 'SIMULATED';
  onGoOnline: () => void;
  onGoOffline: () => void;
  busy: boolean;
  refresh: () => void;
}) {
  const p = state.profile;
  if (p.status === 'PENDING_APPROVAL') {
    const steps: [string, 'done' | 'wait' | 'todo'][] = [
      ['Account created', 'done'],
      [`Vehicle submitted · ${p.vehicle.make} ${p.vehicle.model} ${p.vehicle.plate}`, 'done'],
      ['Licence and documents checked by EcoRide', 'wait'],
      ['Go online and start earning', 'todo'],
    ];
    return (
      <View testID="driver-pending">
        <Badge label="Under review" tone="warn" icon="hourglass-outline" />
        <T v="h1" style={{ marginTop: space.sm }}>
          Your application is being reviewed
        </T>
        <T v="muted" style={{ marginTop: 4 }}>
          We'll let you go online as soon as an EcoRide admin approves your account.
        </T>
        <View style={{ marginTop: space.lg, gap: space.md }}>
          {steps.map(([label, s]) => (
            <Row key={label} gap={space.md}>
              <Ionicons
                name={s === 'done' ? 'checkmark-circle' : s === 'wait' ? 'time' : 'ellipse-outline'}
                size={22}
                color={s === 'done' ? colors.brand : s === 'wait' ? colors.warn : colors.ink4}
              />
              <T v={s === 'wait' ? 'bodyStrong' : 'body'} style={{ flex: 1 }}>
                {label}
              </T>
            </Row>
          ))}
        </View>
        <Button label="Check status" variant="secondary" icon="refresh" style={{ marginTop: space.lg }} onPress={refresh} />
      </View>
    );
  }
  const online = p.availability !== 'OFFLINE';
  return (
    <View>
      {p.status === 'SUSPENDED' ? (
        <View style={{ marginBottom: space.md }}>
          <Banner tone="danger" title="Account suspended" message={p.statusReason ?? 'Contact EcoRide driver support.'} />
        </View>
      ) : null}
      <Row style={{ justifyContent: 'space-between' }}>
        <View style={{ flex: 1 }}>
          <T v="h1">{online ? "You're online" : "You're offline"}</T>
          <T v="muted" style={{ marginTop: 2 }}>
            {online ? 'Finding ride requests near you…' : 'Go online to get ride requests nearby.'}
          </T>
        </View>
        <Badge label={gpsMode === 'SIMULATED' ? 'Demo GPS' : 'GPS'} tone={gpsMode === 'SIMULATED' ? 'info' : 'brand'} icon="navigate" />
      </Row>
      {online ? <Pulse /> : <View style={{ height: space.md }} />}
      <Row gap={space.sm}>
        <StatTile label="Today" value={fmt.rupees(state.today.earnings)} icon="cash-outline" />
        <StatTile label="Trips" value={String(state.today.trips)} icon="car-outline" />
        <StatTile label="Rating" value={fmt.rating(p.rating)} icon="star" />
        <StatTile label="Accept" value={p.acceptanceRate == null ? '—' : `${p.acceptanceRate}%`} icon="checkmark-done" />
      </Row>
      {online ? (
        <Button label="Go offline" variant="secondary" icon="pause" style={{ marginTop: space.lg }} loading={busy} onPress={onGoOffline} testID="driver-go-offline" />
      ) : (
        <Button
          label="Go online"
          variant="brand"
          icon="power"
          style={{ marginTop: space.lg, height: 60 }}
          loading={busy}
          disabled={p.status !== 'APPROVED'}
          onPress={onGoOnline}
          testID="driver-go-online"
        />
      )}
    </View>
  );
}

function Pulse() {
  const x = useRef(new Animated.Value(0)).current;
  useEffect(() => {
    const loop = Animated.loop(Animated.timing(x, { toValue: 1, duration: 1600, easing: Easing.inOut(Easing.quad), useNativeDriver: false }));
    loop.start();
    return () => loop.stop();
  }, [x]);
  return (
    <View style={{ height: 4, borderRadius: 2, backgroundColor: colors.mist, overflow: 'hidden', marginVertical: space.md }}>
      <Animated.View
        style={{ position: 'absolute', height: 4, width: '30%', borderRadius: 2, backgroundColor: colors.brand, left: x.interpolate({ inputRange: [0, 1], outputRange: ['-30%', '100%'] }) }}
      />
    </View>
  );
}

// ------------------------------------------------------------------ the request card

export function OfferCard({ offer, secondsLeft, onAccept, onDecline, busy }: { offer: Offer; secondsLeft: number; onAccept: () => void; onDecline: () => void; busy: boolean }) {
  const fraction = Math.max(0, Math.min(1, secondsLeft / offer.timeoutS));
  return (
    <View testID="driver-offer" style={{ backgroundColor: colors.white, borderRadius: 24, overflow: 'hidden' }}>
      <View style={{ height: 6, backgroundColor: colors.mist }}>
        <View style={{ height: 6, width: `${fraction * 100}%`, backgroundColor: secondsLeft <= 5 ? colors.danger : colors.brand }} />
      </View>
      <View style={{ padding: space.lg }}>
        <Row style={{ justifyContent: 'space-between' }}>
          <Row>
            <VehicleIcon type={offer.vehicleType} size={36} />
            <T v="h2">New {offer.vehicleType === 'MINI' ? 'Mini' : offer.vehicleType.charAt(0) + offer.vehicleType.slice(1).toLowerCase()} request</T>
          </Row>
          <View style={{ width: 46, height: 46, borderRadius: 23, borderWidth: 3, borderColor: secondsLeft <= 5 ? colors.danger : colors.brand, alignItems: 'center', justifyContent: 'center' }}>
            <Text style={{ fontWeight: '800', fontSize: 17, color: colors.ink }}>{Math.ceil(secondsLeft)}</Text>
          </View>
        </Row>
        <Row style={{ marginTop: space.md, alignItems: 'flex-end' }} gap={space.md}>
          <T v="display" testID="offer-earning">
            {fmt.rupees(offer.estEarning)}
          </T>
          <View style={{ paddingBottom: 6 }}>
            <T v="small">you earn · fare {fmt.rupees(offer.fare)}</T>
            <Row gap={6}>
              <Badge label={fmt.PAYMENT_LABEL[offer.paymentMethod]} tone="neutral" />
              {offer.surgeMultiplier > 1 ? <Badge label={`${offer.surgeMultiplier}x surge`} tone="warn" icon="trending-up" /> : null}
            </Row>
          </View>
        </Row>
        <View style={{ marginTop: space.lg, gap: space.md }}>
          <Row gap={space.md} style={{ alignItems: 'flex-start' }}>
            <View style={{ width: 12, height: 12, borderRadius: 6, backgroundColor: colors.brand, marginTop: 4 }} />
            <View style={{ flex: 1 }}>
              <T v="bodyStrong">
                {fmt.distance(offer.pickupDistanceM)} · {fmt.minutes(offer.pickupEtaS)} to pickup
              </T>
              <T v="muted" numberOfLines={1}>
                {offer.pickup.address}
              </T>
            </View>
          </Row>
          <Row gap={space.md} style={{ alignItems: 'flex-start' }}>
            <View style={{ width: 12, height: 12, backgroundColor: colors.ink, marginTop: 4 }} />
            <View style={{ flex: 1 }}>
              <T v="bodyStrong">
                {fmt.distance(offer.tripDistanceM)} · {fmt.minutes(offer.tripDurationS)} trip
              </T>
              <T v="muted" numberOfLines={1}>
                {offer.drop.address}
              </T>
            </View>
          </Row>
        </View>
        <Row style={{ marginTop: space.md }} gap={6}>
          <Ionicons name="person-circle-outline" size={18} color={colors.ink3} />
          <T v="small">
            {offer.riderName} · {fmt.rating(offer.riderRating)} ★
          </T>
        </Row>
        <Row style={{ marginTop: space.lg }}>
          <Button label="Decline" variant="secondary" onPress={onDecline} style={{ flex: 1 }} disabled={busy} testID="offer-decline" />
          <Button label="Accept" variant="brand" onPress={onAccept} style={{ flex: 2 }} loading={busy} testID="offer-accept" />
        </Row>
      </View>
    </View>
  );
}

// ------------------------------------------------------------------ the ride

export function RidePanel({ ride, elapsed, refresh }: { ride: DriverRide; elapsed: number; refresh: () => void }) {
  const [busy, setBusy] = useState<string | null>(null);
  const [otp, setOtp] = useState('');
  const first = ride.rider.name.split(' ')[0];
  const run = (key: string, fn: () => Promise<unknown>, title?: string) => {
    setBusy(key);
    void act(fn, refresh, title).finally(() => setBusy(null));
  };

  const header = (
    <Row gap={space.md}>
      <Avatar name={ride.rider.name} size={46} color={colors.info} />
      <View style={{ flex: 1 }}>
        <T v="h2">{ride.rider.name}</T>
        <T v="small">
          {fmt.rating(ride.rider.rating)} ★ · {fmt.PAYMENT_LABEL[ride.fare.method]} · {fmt.rupees(ride.fare.total)}
        </T>
      </View>
      <Pressable onPress={() => callPhone(ride.rider.phone)} accessibilityLabel="Call rider" style={{ width: 44, height: 44, borderRadius: 22, backgroundColor: colors.mist, alignItems: 'center', justifyContent: 'center' }}>
        <Ionicons name="call" size={20} color={colors.ink} />
      </Pressable>
    </Row>
  );

  if (ride.status === 'ACCEPTED') {
    return (
      <View testID="driver-to-pickup">
        <T v="label">Picking up {first}</T>
        <Row style={{ alignItems: 'flex-end', marginTop: 4 }} gap={space.sm}>
          <T v="title">{ride.live.toPickupEtaS != null ? fmt.minutes(ride.live.toPickupEtaS) : '…'}</T>
          <T v="h2" style={{ color: colors.ink3, paddingBottom: 3 }}>
            {ride.live.toPickupM != null ? fmt.distance(ride.live.toPickupM) : ''}
          </T>
        </Row>
        <T v="muted" numberOfLines={2} style={{ marginBottom: space.md }}>
          {ride.pickup.address}
        </T>
        {ride.live.riderToPickupM != null ? (
          <View style={{ marginBottom: space.md }}>
            <Banner tone="info" icon="person" message={`${first} is sharing their location: ${fmt.distance(ride.live.riderToPickupM)} from the pickup.`} />
          </View>
        ) : null}
        {header}
        <Row style={{ marginTop: space.lg }}>
          <Button label="Navigate" icon="navigate" variant="secondary" style={{ flex: 1 }} onPress={() => openNavigation(ride.pickup.lat, ride.pickup.lng)} />
          <Button label="Arrived" icon="flag" variant="brand" style={{ flex: 1.4 }} loading={busy === 'arrived'} onPress={() => run('arrived', () => api.arrived(ride.id))} testID="driver-arrived" />
        </Row>
        <CancelLink ride={ride} refresh={refresh} />
      </View>
    );
  }

  if (ride.status === 'ARRIVED') {
    const waited = (ride.live.waitingS ?? 0) + elapsed;
    const noShowIn = ride.noShowInS != null ? Math.max(0, ride.noShowInS - elapsed) : null;
    return (
      <View testID="driver-waiting">
        <Row style={{ justifyContent: 'space-between' }}>
          <T v="h1">Waiting for {first}</T>
          <Badge label={fmt.clock(waited)} tone={ride.live.freeWaitLeftS && ride.live.freeWaitLeftS - elapsed > 0 ? 'brand' : 'warn'} icon="time-outline" />
        </Row>
        <T v="muted" style={{ marginTop: 2, marginBottom: space.md }}>
          Ask for the 4-digit PIN shown in the rider's app.
        </T>
        <View style={{ gap: space.sm }}>
          <TextInput
            value={otp}
            onChangeText={(t) => setOtp(t.replace(/\D/g, '').slice(0, 4))}
            keyboardType="number-pad"
            maxLength={4}
            placeholder="PIN"
            placeholderTextColor={colors.ink4}
            style={{ height: 58, borderRadius: 12, backgroundColor: colors.mist, textAlign: 'center', fontSize: 28, fontWeight: '800', letterSpacing: 14, color: colors.ink }}
            testID="driver-otp"
          />
          <Button label="Start trip" variant="brand" icon="play" disabled={otp.length !== 4} loading={busy === 'start'} onPress={() => run('start', () => api.start(ride.id, otp), 'Could not start')} testID="driver-start" />
        </View>
        <View style={{ marginTop: space.md }}>{header}</View>
        {ride.canCancelAsNoShow || noShowIn === 0 ? (
          <Button
            label="Rider didn't come (no-show)"
            variant="ghost"
            style={{ marginTop: space.sm }}
            onPress={async () => {
              if (await confirmAction('Cancel as a no-show?', `${first} will pay the cancellation fee, and you get your share.`, 'Cancel as no-show', true)) {
                run('noshow', () => api.cancel(ride.id, 'Rider did not show up'));
              }
            }}
          />
        ) : (
          <T v="small" style={{ textAlign: 'center', marginTop: space.md }}>
            No-show option in {fmt.clock(noShowIn ?? 0)}
          </T>
        )}
      </View>
    );
  }

  // IN_PROGRESS
  return (
    <View testID="driver-on-trip">
      <T v="label">Dropping {first}</T>
      <Row style={{ alignItems: 'flex-end', marginTop: 4 }} gap={space.sm}>
        <T v="title">{ride.live.remainingEtaS != null ? fmt.minutes(ride.live.remainingEtaS) : '…'}</T>
        <T v="h2" style={{ color: colors.ink3, paddingBottom: 3 }}>
          {ride.live.remainingM != null ? `${fmt.distance(ride.live.remainingM)} left` : ''}
        </T>
      </Row>
      <T v="muted" numberOfLines={2}>
        {ride.drop.address}
      </T>
      <Row gap={space.sm} style={{ marginTop: space.md }}>
        <StatTile label="Driven (GPS)" value={fmt.distance(ride.live.travelledM)} icon="car-outline" />
        <StatTile label="Fare" value={fmt.rupees(ride.fare.total)} icon="cash-outline" />
        <StatTile label="You earn" value={fmt.rupees(ride.estEarning)} icon="cash-outline" />
      </Row>
      <Row style={{ marginTop: space.lg }}>
        <Button label="Navigate" icon="navigate" variant="secondary" style={{ flex: 1 }} onPress={() => openNavigation(ride.drop.lat, ride.drop.lng)} />
        <Button
          label="Complete trip"
          icon="checkmark-circle"
          variant="primary"
          style={{ flex: 1.4 }}
          loading={busy === 'complete'}
          testID="driver-complete"
          onPress={async () => {
            const far = ride.live.remainingM != null && ride.live.remainingM > 1000;
            if (far && !(await confirmAction('End the trip here?', `You are ${fmt.distance(ride.live.remainingM)} from the drop. The fare is checked against the distance actually driven.`, 'End trip'))) return;
            run('complete', () => api.complete(ride.id));
          }}
        />
      </Row>
      <Pressable
        style={{ alignSelf: 'center', marginTop: space.md, padding: space.sm }}
        onPress={async () => {
          if (await confirmAction('Emergency SOS', 'Alert the EcoRide safety team with this trip and your location?', 'Send SOS', true)) {
            run('sos', () => api.sos(ride.id, { note: 'Raised from the driver app' }).then((r) => showMessage('Help is on the way', r.message)));
          }
        }}
      >
        <T v="small" style={{ color: colors.danger, fontWeight: '700' }}>
          Emergency SOS
        </T>
      </Pressable>
    </View>
  );
}

function CancelLink({ ride, refresh }: { ride: DriverRide; refresh: () => void }) {
  return (
    <Pressable
      style={{ alignSelf: 'center', marginTop: space.md, padding: space.sm }}
      onPress={async () => {
        const penalty = Math.min(100, Math.round(ride.fare.total * 0.1));
        if (await confirmAction('Cancel this ride?', `Cancelling an accepted ride without cause costs ₹${penalty} and the rider is sent another driver.`, 'Cancel ride', true)) {
          await act(() => api.cancel(ride.id, 'Driver could not reach the pickup'), refresh);
        }
      }}
    >
      <T v="small" style={{ color: colors.danger, fontWeight: '700' }}>
        Cancel ride
      </T>
    </Pressable>
  );
}

// ------------------------------------------------------------------ after the trip

export function LastTripPanel({ ride, refresh }: { ride: DriverRide; refresh: () => void }) {
  const [stars, setStars] = useState(0);
  const [busy, setBusy] = useState(false);
  const cashDue = ride.fare.method === 'CASH' && ride.fare.paymentStatus === 'PENDING';
  const first = ride.rider.name.split(' ')[0];
  const fare = ride.fare.fare;
  return (
    <View testID="driver-trip-done">
      <Row style={{ justifyContent: 'space-between' }}>
        <View>
          <T v="label">Trip complete</T>
          <T v="display">{fmt.rupees(ride.fare.total)}</T>
        </View>
        <View style={{ alignItems: 'flex-end' }}>
          <T v="small">You earn</T>
          <T v="h1" style={{ color: colors.brandDark }}>
            {fmt.rupees(ride.estEarning)}
          </T>
        </View>
      </Row>
      <T v="muted" style={{ marginTop: 4 }}>
        {(ride.actualDistanceM / 1000).toFixed(2)} km by GPS (planned {(ride.estDistanceM / 1000).toFixed(2)} km)
        {ride.fare.policy ? ` · ${ride.fare.policy === 'UPFRONT' ? 'upfront fare' : 'fare recalculated'}` : ''}
        {fare && fare.waitingFare > 0 ? ` · waiting ${fmt.rupees(fare.waitingFare)}` : ''}
      </T>
      <View style={{ marginTop: space.md }}>
        {cashDue ? (
          <Banner
            tone="warn"
            icon="cash-outline"
            title={`Collect ${fmt.rupees(ride.fare.total)} in cash`}
            message={`${first} pays cash. Confirm once you have it.`}
            action={
              <Button
                label="Cash collected"
                size="md"
                variant="primary"
                loading={busy}
                testID="driver-cash"
                onPress={() => {
                  setBusy(true);
                  void act(() => api.cashCollected(ride.id), refresh).finally(() => setBusy(false));
                }}
              />
            }
          />
        ) : (
          <Banner tone="brand" icon="checkmark-circle" message={`Paid by ${fmt.PAYMENT_LABEL[ride.fare.method]}. Your share is added to this week's payout.`} />
        )}
      </View>
      {ride.riderRating == null ? (
        <View style={{ marginTop: space.lg, alignItems: 'center' }}>
          <T v="h2">Rate {first}</T>
          <View style={{ marginTop: space.sm }} testID="driver-rate">
            <Stars
              value={stars}
              size={34}
              onChange={(n) => {
                setStars(n);
                void act(() => api.rateRider(ride.id, n), refresh);
              }}
            />
          </View>
        </View>
      ) : (
        <Row style={{ marginTop: space.md, justifyContent: 'center' }}>
          <T v="muted">You rated {first}</T>
          <Stars value={ride.riderRating} size={14} />
        </Row>
      )}
    </View>
  );
}
