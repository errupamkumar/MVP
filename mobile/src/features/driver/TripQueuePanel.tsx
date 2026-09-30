import { Ionicons } from '@expo/vector-icons';
import { useState } from 'react';
import { Linking, Text, TextInput, View } from 'react-native';

import type { DriverProfileDto, QueueRiderDto, QueueStopDto, TripQueueDto } from '@/api/types';
import { Banner, Button, Card, EmptyState, ErrorBanner, Eyebrow, Muted, Pill, Row, SectionTitle } from '@/components/ui';
import { useArrive, useBoardRider, useNoShow, useStops } from '@/hooks/queries';
import type { DriverLocationState } from '@/hooks/useDriverLocation';
import { useNow } from '@/hooks/useNow';
import { hoursMinutes, minutes } from '@/lib/format';
import { colors } from '@/lib/theme';

/** The screen a driver lives in all day: pickups in route order, OTP boarding, arrival, no-show. */
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
      </Card>

      {location.lastAck?.overspeed ? (
        <Banner tone="danger" icon="speedometer" title="Slow down" message={`The limit here is ${location.lastAck.speedLimitKmh} km/h. The desk has been alerted.`} />
      ) : null}
      {location.permission === 'denied' ? (
        <Banner tone="amber" icon="location" title="Location is off" message="Riders see your cab move only when location is allowed. Tap Arrived at each stop." />
      ) : null}

      {!trip ? (
        <EmptyState icon="hourglass-outline" title="No pickups yet" message={notice ?? 'New riders appear here automatically, in route order.'} />
      ) : (
        <QueueBody trip={trip} fetchedAt={fetchedAt} />
      )}
    </View>
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
