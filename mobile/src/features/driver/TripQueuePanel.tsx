import { Ionicons } from '@expo/vector-icons';
import { useState } from 'react';
import { Linking, Text, TextInput, View } from 'react-native';

import type { DriverProfileDto, QueueRiderDto, QueueStopDto, TripQueueDto } from '@/api/types';
import { LiveMap, type MapMarker, type MapStop } from '@/components/LiveMap';
import { Banner, Button, Card, EmptyState, ErrorBanner, Eyebrow, Muted, Pill, Row, SectionTitle } from '@/components/ui';
import { useArrive, useBoardRider, useNoShow, useStops } from '@/hooks/queries';
import type { DriverLocationState } from '@/hooks/useDriverLocation';
import { useNow } from '@/hooks/useNow';
import { ago, distance, hoursMinutes, minutes } from '@/lib/format';
import { colors } from '@/lib/theme';

/**
 * The screen a driver lives in all day: pickups in route order, OTP boarding,
 * arrival, no-show, and a live map of the cab and the riders waiting for it.
 */
export function TripQueuePanel({
  trip,
  notice,
  profile,
  location,
  fetchedAt,
}: {
  trip: TripQueueDto | null;
  notice: string | null;
  profile: DriverProfileDto;
  location: DriverLocationState;
  fetchedAt: number;
}) {
  const duty = profile.duty;
  return (
    <View>
      <Card className="bg-ink">
        <Row className="justify-between">
          <View className="flex-1">
            <Eyebrow className="text-line">{duty ? `On duty · ${duty.vehicle.code} · ${duty.vehicle.registrationNo}` : ''}</Eyebrow>
            <Text className="mt-1 text-2xl font-bold text-white">{trip ? trip.route.label : 'Waiting for pickups'}</Text>
          </View>
          {trip ? <Pill label={trip.status === 'IN_PROGRESS' ? 'On trip' : 'To pickup'} tone={trip.status === 'IN_PROGRESS' ? 'teal' : 'accent'} /> : null}
        </Row>
        {trip ? (
          <Row className="mt-3">
            <Stat label="On board" value={`${trip.onBoard} of ${trip.capacity}`} />
            <Stat label="Seats left" value={String(trip.seatsLeft)} />
            <Stat label="Speed limit" value={`${trip.speedLimitKmh} km/h`} />
          </Row>
        ) : duty ? (
          <Row className="mt-3">
            <Stat label="Trips" value={String(duty.tripsCompleted)} />
            <Stat label="Riders" value={String(duty.ridersCarried)} />
            <Stat label="Duty" value={`${hoursMinutes(duty.minutesOnDuty)} of ${hoursMinutes(duty.maxDutyMinutes)}`} />
          </Row>
        ) : null}
        <GpsStatus location={location} />
      </Card>

      {location.lastAck?.overspeed ? (
        <Banner tone="danger" icon="speedometer" title="Slow down" message={`The limit here is ${location.lastAck.speedLimitKmh} km/h. The desk has been alerted.`} />
      ) : null}
      {location.permission === 'denied' ? (
        <Banner tone="amber" icon="location" title="Location is off" message="Riders see your cab move only when location is allowed. Tap Arrived at each stop." />
      ) : null}
      {location.lastAck?.outsideServiceArea ? (
        <Banner
          tone="amber"
          icon="locate"
          title="You are outside the plant area"
          message="Your phone's position is far from every pickup point, so riders do not see it. Tap Arrived at each stop."
        />
      ) : null}

      {!trip ? (
        <EmptyState icon="hourglass-outline" title="No pickups yet" message={notice ?? 'New riders appear here automatically, in route order.'} />
      ) : (
        <QueueBody trip={trip} fetchedAt={fetchedAt} />
      )}
    </View>
  );
}

/** Is the cab's position reaching riders? One line under the header. */
function GpsStatus({ location }: { location: DriverLocationState }) {
  const now = useNow(location.lastSentAt != null, 5_000);
  const { permission, lastAck, lastSentAt } = location;
  let tone: 'live' | 'warn' | 'off' = 'warn';
  let text: string;
  if (permission === 'unsupported') {
    tone = 'off';
    text = 'This device cannot share its location';
  } else if (permission === 'denied') {
    tone = 'off';
    text = 'Location off · riders see only the last stop you reached';
  } else if (lastAck?.outsideServiceArea) {
    text = 'Outside the plant area · position not shared';
  } else if (lastSentAt == null) {
    text = 'Finding your location…';
  } else {
    const seconds = Math.max(0, Math.round((now - lastSentAt) / 1000));
    tone = seconds <= 120 ? 'live' : 'warn';
    text = `Sharing your location with riders · sent ${ago(seconds)}`;
  }
  const dot = { live: 'bg-teal', warn: 'bg-amber', off: 'bg-danger' }[tone];
  return (
    <Row className="mt-3 border-t border-ink-2 pt-3">
      <View className={`h-2.5 w-2.5 rounded-full ${dot}`} />
      <Text className="ml-2 flex-1 text-sm text-line">{text}</Text>
    </Row>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <View className="mr-5">
      <Text className="text-lg font-bold text-white">{value}</Text>
      <Text className="text-xs text-line">{label}</Text>
    </View>
  );
}

function QueueBody({ trip, fetchedAt }: { trip: TripQueueDto; fetchedAt: number }) {
  const arrive = useArrive();
  const stops = useStops();
  const current = trip.stops.find((s) => s.state === 'CURRENT');
  const next = trip.nextStop;
  const nextCoords = stops.data?.find((s) => s.id === next?.stopId);

  return (
    <View>
      {current ? <CurrentStop trip={trip} stop={current} fetchedAt={fetchedAt} /> : null}

      {next ? (
        <View className="mt-3">
          {arrive.isError ? <ErrorBanner error={arrive.error} /> : null}
          <Button
            label={`Arrived at ${next.stopName}`}
            icon="flag"
            variant="dark"
            disabled={!trip.canArriveNext}
            loading={arrive.isPending}
            onPress={() => arrive.mutate({ tripId: trip.tripId, stopSeq: next.seq })}
            className="min-h-[64px]"
          />
          {!trip.canArriveNext && trip.arriveBlockedReason ? (
            <Muted className="mt-1.5 text-center">{trip.arriveBlockedReason}</Muted>
          ) : (
            <Muted className="mt-1.5 text-center">
              {next.summary} · {next.etaMinutes < 1 ? 'arriving now' : `about ${minutes(next.etaMinutes)} away`}
            </Muted>
          )}
          {nextCoords ? (
            <Button
              label="Navigate"
              icon="navigate"
              variant="secondary"
              size="md"
              className="mt-2"
              onPress={() =>
                Linking.openURL(
                  `https://www.google.com/maps/dir/?api=1&destination=${nextCoords.latitude},${nextCoords.longitude}&travelmode=driving`,
                )
              }
            />
          ) : null}
        </View>
      ) : null}

      <TripMap trip={trip} />
      <WaitingRiders trip={trip} />

      <SectionTitle title="Stops in route order" />
      <Card className="p-2">
        {trip.stops.map((s, i) => (
          <Row key={s.seq} className={`px-2 py-3 ${i < trip.stops.length - 1 ? 'border-b border-line' : ''}`}>
            <View
              className={`h-9 w-9 items-center justify-center rounded-full ${
                s.state === 'DONE' ? 'bg-mist' : s.state === 'CURRENT' ? 'bg-accent' : s.state === 'NEXT' ? 'bg-ink' : 'bg-mist'
              }`}
            >
              {s.state === 'DONE' ? (
                <Ionicons name="checkmark" size={18} color={colors.ink3} />
              ) : (
                <Text className={`text-base font-bold ${s.state === 'CURRENT' || s.state === 'NEXT' ? 'text-white' : 'text-ink'}`}>
                  {i + 1}
                </Text>
              )}
            </View>
            <View className="ml-3 flex-1">
              <Text className={`text-lg font-semibold ${s.state === 'DONE' ? 'text-ink-3' : 'text-ink'}`}>{s.stopName}</Text>
              <Muted>{s.summary}</Muted>
            </View>
            <Text className="text-base font-semibold text-ink-2">
              {s.state === 'DONE' ? 'done' : s.state === 'CURRENT' ? 'here' : minutes(s.etaMinutes)}
            </Text>
          </Row>
        ))}
      </Card>
      <Muted className="mt-2 text-center">The sequence comes from the route master, not the driver.</Muted>
    </View>
  );
}

/** The cab against this trip's stops, with every waiting rider who shares their location. */
function TripMap({ trip }: { trip: TripQueueDto }) {
  const waiting = trip.stops.flatMap((s) => s.pickups).filter((r) => r.status === 'ASSIGNED');
  const cab = trip.cab;
  const stops: MapStop[] = trip.stops.map((s) => ({
    key: String(s.seq),
    name: s.stopName,
    latitude: s.latitude,
    longitude: s.longitude,
    tone: s.state === 'DONE' ? 'passed' : s.state === 'NEXT' ? 'highlight' : s.state === 'CURRENT' ? 'target' : 'normal',
  }));
  const markers: MapMarker[] = [];
  for (const r of waiting) {
    if (r.location) {
      markers.push({
        key: `rider-${r.bookingId}`,
        kind: 'rider',
        label: r.riderName,
        latitude: r.location.latitude,
        longitude: r.location.longitude,
        live: r.location.live,
        accuracyM: r.location.accuracyM,
      });
    }
  }
  const sharing = markers.length;
  if (cab) {
    markers.push({ key: 'cab', kind: 'cab', label: cab.vehicleCode, latitude: cab.latitude, longitude: cab.longitude, live: cab.live, accuracyM: cab.accuracyM });
  }
  // Fit the view to what the driver acts on next: the cab, the riders and the next stop.
  const focus = [
    ...markers.map((m) => m.key),
    ...trip.stops.filter((s) => s.state === 'NEXT' || s.state === 'CURRENT').map((s) => String(s.seq)),
  ];
  const cabLine = !cab
    ? 'Waiting for your first GPS fix.'
    : cab.live
      ? `${cab.vehicleCode}${cab.towardsStopName && cab.metresToStop != null ? ` · ${distance(cab.metresToStop)} to ${cab.towardsStopName}` : ''} · updated ${ago(cab.secondsAgo)}`
      : `GPS not live · shown at the last known position${cab.nearStopName ? ` near ${cab.nearStopName}` : ''}`;
  return (
    <>
      <SectionTitle title="Live map" />
      <Card className="p-2">
        <LiveMap stops={stops} markers={markers} focus={focus} />
        <Muted className="mx-1 mt-2">{cabLine}</Muted>
        {waiting.length > 0 ? (
          <Muted className="mx-1">
            {sharing} of {waiting.length} waiting {waiting.length === 1 ? 'rider shares' : 'riders share'} their location
          </Muted>
        ) : null}
      </Card>
    </>
  );
}

/** Riders the cab is on its way to (not yet at their stop): who they are, where they wait, and where they are now. */
function WaitingRiders({ trip }: { trip: TripQueueDto }) {
  const waiting = trip.stops
    .filter((s) => s.state === 'NEXT' || s.state === 'UPCOMING')
    .flatMap((s) => s.pickups.filter((p) => p.status === 'ASSIGNED'));
  if (waiting.length === 0) return null;
  return (
    <>
      <SectionTitle title="Riders to pick up" />
      {waiting.map((r) => (
        <Card key={r.bookingId} className="mb-2">
          <Row className="justify-between">
            <View className="flex-1 pr-2">
              <Text className="text-lg font-bold text-ink">{r.riderName}</Text>
              <Muted>
                {r.personnelNo} · {r.fromStopName} → {r.toStopName}
                {r.seats > 1 ? ` · ${r.seats} seats` : ''}
              </Muted>
            </View>
            <Button
              label="Call"
              icon="call"
              size="sm"
              variant="secondary"
              onPress={() => Linking.openURL(`tel:${r.riderPhone.replace(/\s/g, '')}`)}
            />
          </Row>
          <RiderWhere rider={r} />
        </Card>
      ))}
    </>
  );
}

/** Where a waiting rider is, as their phone last reported it. */
function RiderWhere({ rider }: { rider: QueueRiderDto }) {
  const l = rider.location;
  if (!l) {
    return (
      <Row className="mt-2">
        <Ionicons name="location-outline" size={16} color={colors.ink3} />
        <Muted className="ml-1.5 flex-1">Location not shared · waits at {rider.fromStopName}</Muted>
      </Row>
    );
  }
  const where = l.atPickup ? `At the ${l.pickupName} pickup point` : `${distance(l.metresToPickup)} from ${l.pickupName}`;
  const details = [
    l.metresFromCab != null ? `${distance(l.metresFromCab)} from you` : null,
    `${l.live ? 'updated' : 'last seen'} ${ago(l.secondsAgo)}`,
  ]
    .filter(Boolean)
    .join(' · ');
  return (
    <Row className="mt-2">
      <Ionicons name={l.atPickup ? 'checkmark-circle' : 'location'} size={18} color={l.live ? colors.teal : colors.ink3} />
      <View className="ml-1.5 flex-1">
        <Text className={`text-base font-semibold ${l.live ? 'text-teal-deep' : 'text-ink-2'}`}>{where}</Text>
        <Muted>{details}</Muted>
      </View>
      {!l.atPickup ? (
        <Button
          label="Directions"
          icon="navigate"
          size="sm"
          variant="ghost"
          onPress={() =>
            Linking.openURL(`https://www.google.com/maps/dir/?api=1&destination=${l.latitude},${l.longitude}&travelmode=driving`)
          }
        />
      ) : null}
    </Row>
  );
}

function CurrentStop({ trip, stop, fetchedAt }: { trip: TripQueueDto; stop: QueueStopDto; fetchedAt: number }) {
  const waiting = stop.pickups.filter((p) => p.status === 'ASSIGNED');
  const boarded = stop.pickups.filter((p) => p.status === 'ONBOARD' || p.status === 'COMPLETED');
  const dropped = stop.drops.filter((d) => d.status === 'COMPLETED');
  return (
    <View className="mt-3">
      <SectionTitle title={`At ${stop.stopName}`} />
      {waiting.map((r) => (
        <RiderPickup key={r.bookingId} tripId={trip.tripId} rider={r} fetchedAt={fetchedAt} />
      ))}
      {boarded.length > 0 ? (
        <Card className="mb-2 bg-teal-soft">
          {boarded.map((r) => (
            <Row key={r.bookingId} className="py-1">
              <Ionicons name="checkmark-circle" size={20} color={colors.teal} />
              <Text className="ml-2 flex-1 text-base text-ink">
                {r.riderName} <Text className="text-ink-3">· to {r.toStopName}</Text>
              </Text>
              <Text className="text-sm font-bold text-teal-deep">OTP OK</Text>
            </Row>
          ))}
        </Card>
      ) : null}
      {dropped.length > 0 ? (
        <Card className="mb-2">
          {dropped.map((r) => (
            <Row key={r.bookingId} className="py-1">
              <Ionicons name="exit" size={20} color={colors.ink3} />
              <Text className="ml-2 flex-1 text-base text-ink-2">{r.riderName} dropped</Text>
            </Row>
          ))}
        </Card>
      ) : null}
    </View>
  );
}

function RiderPickup({ tripId, rider, fetchedAt }: { tripId: number; rider: QueueRiderDto; fetchedAt: number }) {
  const [otp, setOtp] = useState('');
  const board = useBoardRider();
  const noShow = useNoShow();
  const counting = rider.noShowAllowedInSeconds != null && rider.noShowAllowedInSeconds > 0;
  const now = useNow(counting);
  const secondsLeft =
    rider.noShowAllowedInSeconds == null ? null : Math.max(0, rider.noShowAllowedInSeconds - Math.floor((now - fetchedAt) / 1000));

  return (
    <Card className="mb-2">
      <Row className="justify-between">
        <View className="flex-1">
          <Text className="text-lg font-bold text-ink">{rider.riderName}</Text>
          <Muted>
            {rider.personnelNo} · to {rider.toStopName}
            {rider.seats > 1 ? ` · ${rider.seats} seats` : ''}
          </Muted>
        </View>
        <Button
          label="Call"
          icon="call"
          size="sm"
          variant="secondary"
          onPress={() => Linking.openURL(`tel:${rider.riderPhone.replace(/\s/g, '')}`)}
        />
      </Row>
      <RiderWhere rider={rider} />

      {rider.otpLocked ? (
        <Banner tone="danger" icon="lock-closed" title="Boarding locked" message="Too many wrong codes. Call the transport desk to verify the rider." />
      ) : (
        <Row className="mt-3">
          <TextInput
            value={otp}
            onChangeText={(t) => setOtp(t.replace(/\D/g, '').slice(0, 4))}
            keyboardType="number-pad"
            maxLength={4}
            placeholder="OTP"
            placeholderTextColor={colors.ink3}
            accessibilityLabel={`Enter ${rider.riderName}'s OTP`}
            className="mr-3 h-[60px] min-w-0 flex-1 rounded-xl border-2 border-line bg-white text-center text-3xl font-bold tracking-[8px] text-ink"
          />
          <Button
            label="Board"
            icon="log-in"
            disabled={otp.length !== 4}
            loading={board.isPending}
            onPress={() => board.mutate({ tripId, bookingId: rider.bookingId, otp }, { onSettled: () => setOtp('') })}
            className="min-h-[60px] px-6"
          />
        </Row>
      )}
      {board.isError ? <View className="mt-2"><ErrorBanner error={board.error} /></View> : null}
      {noShow.isError ? <View className="mt-2"><ErrorBanner error={noShow.error} /></View> : null}

      {secondsLeft != null ? (
        <Button
          label={secondsLeft > 0 ? `No-show in ${Math.floor(secondsLeft / 60)}:${String(secondsLeft % 60).padStart(2, '0')}` : 'Mark no-show'}
          variant="ghost"
          size="md"
          icon="person-remove"
          disabled={secondsLeft > 0}
          loading={noShow.isPending}
          onPress={() => noShow.mutate({ tripId, bookingId: rider.bookingId })}
          className="mt-1"
        />
      ) : null}
    </Card>
  );
}
