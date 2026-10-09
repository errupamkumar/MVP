import { Ionicons } from '@expo/vector-icons';
import { ComponentProps, useState } from 'react';
import { ActivityIndicator, Linking, Text, View } from 'react-native';

import type { AlternativeSpotDto, AvailabilityDto, NearbyCabDto, NearbyCabStatus, NextRideDto, SearchStatusDto } from '@/api/types';
import { Button, Card, Muted, Pill, ProgressBar, Row, SectionTitle } from '@/components/ui';
import { useNow } from '@/hooks/useNow';
import { km, minutes, time } from '@/lib/format';
import { colors } from '@/lib/theme';

type IconName = ComponentProps<typeof Ionicons>['name'];

/** "0:07", "12:40", "1:02:05" */
export function clock(totalSeconds: number): string {
  const s = Math.max(0, Math.floor(totalSeconds));
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const sec = String(s % 60).padStart(2, '0');
  return h > 0 ? `${h}:${String(m).padStart(2, '0')}:${sec}` : `${m}:${sec}`;
}

/** "now", "12 min", "5 h 30 min" */
function inMinutes(value: number): string {
  return minutes(value);
}

// ------------------------------------------------------------------ search in progress

/** Shown while the engine checks the fleet, with the time the search has taken so far. */
export function SearchingCard({ startedAt, pickupName }: { startedAt: number; pickupName: string }) {
  const now = useNow(true, 250);
  const elapsed = (now - startedAt) / 1000;
  return (
    <Card className="mt-3 border-accent" >
      <Row>
        <ActivityIndicator color={colors.accent} />
        <Text className="ml-3 flex-1 text-base font-bold text-ink">Searching for a ride to {pickupName}</Text>
        <Text className="text-base font-bold text-accent" accessibilityLabel={`${Math.floor(elapsed)} seconds`}>
          {clock(elapsed)}
        </Text>
      </Row>
      <View className="mt-3">
        {['Cabs on duty and where they are', 'Free seats within the occupancy cap', 'Arrival times at your pickup and nearby points'].map((step) => (
          <Row key={step} className="mt-1">
            <Ionicons name="ellipse" size={6} color={colors.ink3} />
            <Muted className="ml-2">{step}</Muted>
          </Row>
        ))}
      </View>
    </Card>
  );
}

/** One line after a search: when it ran, how much of the fleet it covered, and Search again. */
export function SearchedLine({
  receivedAt,
  vehiclesOnDuty,
  onSearchAgain,
  searching,
}: {
  receivedAt: number;
  vehiclesOnDuty: number;
  onSearchAgain: () => void;
  searching: boolean;
}) {
  const now = useNow(true, 1000);
  const ago = Math.max(0, Math.round((now - receivedAt) / 1000));
  return (
    <Row className="mt-3 justify-between">
      <Muted className="flex-1">
        Searched {ago < 3 ? 'just now' : `${clock(ago)} ago`} · {vehiclesOnDuty} {vehiclesOnDuty === 1 ? 'vehicle' : 'vehicles'} on duty checked
      </Muted>
      <Button label="Search again" icon="refresh" size="sm" variant="secondary" loading={searching} onPress={onSearchAgain} />
    </Row>
  );
}

/**
 * A waiting request: how long it has been searching, when the engine checks
 * again by itself, when the request lapses, and Search again now.
 */
export function SearchProgress({
  search,
  receivedAt,
  onSearchAgain,
  busy,
}: {
  search: SearchStatusDto;
  receivedAt: number;
  onSearchAgain: () => void;
  busy: boolean;
}) {
  const now = useNow(true, 1000);
  const since = Math.max(0, (now - receivedAt) / 1000);
  const searching = search.searchingForSeconds + since;
  const every = Math.max(1, search.checkEverySeconds);
  const next = Math.ceil((((search.nextCheckInSeconds - since) % every) + every) % every);
  const total = search.searchingForSeconds + search.expiresInSeconds;
  const pct = total > 0 ? (searching / total) * 100 : 0;
  return (
    <Card className="mt-4 border-accent">
      <Row>
        <ActivityIndicator color={colors.accent} />
        <View className="ml-3 flex-1">
          <Text className="text-base font-bold text-ink">Searching for a cab</Text>
          <Muted>Every cab is busy or full right now. You keep your place in the queue.</Muted>
        </View>
        <View className="ml-2 items-end">
          <Text className="text-xl font-bold text-accent" accessibilityLabel={`Searching for ${Math.floor(searching)} seconds`}>
            {clock(searching)}
          </Text>
          <Muted>searching</Muted>
        </View>
      </Row>
      <View className="mt-3">
        <ProgressBar pct={pct} tone={pct > 80 ? 'danger' : 'amber'} />
        <Row className="mt-1.5 justify-between">
          <Muted>Next automatic check in {next === 0 ? 'a moment' : `${next} s`}</Muted>
          <Muted>Open until {time(search.expiresAt)}</Muted>
        </Row>
      </View>
      {search.lastResult ? (
        <View className="mt-3 rounded-xl bg-mist p-3">
          <Text className="text-xs font-bold uppercase tracking-wider text-ink-3">Last check</Text>
          <Text className="mt-1 text-sm text-ink-2" numberOfLines={4}>{search.lastResult}</Text>
        </View>
      ) : null}
      <Button className="mt-3" label="Search again now" icon="refresh" size="md" loading={busy} onPress={onSearchAgain} />
    </Card>
  );
}

// ------------------------------------------------------------------ when a ride comes

const KIND_ICON: Record<NextRideDto['kind'], IconName> = { CAB: 'car', SHUTTLE: 'bus' };

/** "When a ride can reach Gate 2": the next few rides at the pickup, free or not. */
export function WhenRideComes({ a }: { a: AvailabilityDto }) {
  return (
    <View>
      <SectionTitle title={`When a ride can reach ${a.pickup.name}`} />
      <Card>
        <Row className="items-start">
          <Ionicons name={a.availableNow ? 'checkmark-circle' : 'time'} size={22} color={a.availableNow ? colors.teal : colors.amber} />
          <Text className="ml-2 flex-1 text-base font-semibold text-ink">{a.summary}</Text>
        </Row>
        {a.pickup.landmark ? (
          <Row className="mt-2">
            <Ionicons name="location" size={16} color={colors.accent} />
            <Muted className="ml-1.5 flex-1">Wait at: {a.pickup.landmark}</Muted>
          </Row>
        ) : null}
        {a.upcomingAtPickup.length === 0 ? (
          <Muted className="mt-3">No vehicle on duty can reach this pickup yet. The desk sees your request.</Muted>
        ) : (
          a.upcomingAtPickup.map((r, i) => (
            <Row key={`${r.kind}-${r.vehicleCode}`} className={`mt-3 pt-3 ${i === 0 ? 'border-t border-line' : 'border-t border-line'}`}>
              <View className={`h-10 w-10 items-center justify-center rounded-full ${r.availableNow ? 'bg-teal-soft' : 'bg-mist'}`}>
                <Ionicons name={KIND_ICON[r.kind]} size={20} color={r.availableNow ? colors.teal : colors.ink2} />
              </View>
              <View className="ml-3 flex-1">
                <Text className="text-base font-bold text-ink">
                  {r.kind === 'SHUTTLE' ? `Shuttle ${r.vehicleCode}` : `${r.vehicleCode} · ${r.registrationNo}`}
                </Text>
                {r.driverName ? <Muted>{r.driverName}</Muted> : null}
                {r.note ? <Muted className="text-xs">{r.note}</Muted> : null}
              </View>
              <View className="ml-2 items-end">
                <Text className="text-lg font-bold text-ink">{r.availableNow || r.kind === 'SHUTTLE' ? inMinutes(r.inMinutes) : `~${inMinutes(r.inMinutes)}`}</Text>
                <Muted className="text-xs">at {time(r.arrivesAt)}</Muted>
                {r.distanceKm != null ? <Muted className="text-xs">{km(r.distanceKm)} away</Muted> : null}
              </View>
            </Row>
          ))
        )}
      </Card>
    </View>
  );
}

// ------------------------------------------------------------------ nearby pickup points

/** Admin-configured pickup points within walking distance, soonest first. */
export function NearbySpots({
  a,
  actionLabel,
  onUse,
  busy,
}: {
  a: AvailabilityDto;
  actionLabel: string;
  onUse: (spot: AlternativeSpotDto) => void;
  busy?: boolean;
}) {
  if (a.alternativeSpots.length === 0) return null;
  return (
    <View>
      <SectionTitle title="Nearby pickup points" />
      {a.alternativeSpots.map((s) => (
        <Card key={s.stop.id} className={`mb-2 ${s.sooner ? 'border-teal' : ''}`}>
          <Row className="items-start justify-between">
            <View className="flex-1">
              <Text className="text-base font-bold text-ink">{s.stop.name}</Text>
              {s.stop.landmark ? <Muted>{s.stop.landmark}</Muted> : null}
            </View>
            {s.sooner ? <Pill label="Sooner" tone="teal" /> : null}
          </Row>
          <Row className="mt-2 flex-wrap">
            <Row className="mr-4">
              <Ionicons name="walk" size={16} color={colors.ink2} />
              <Muted className="ml-1">
                {s.walkMinutes} min walk · {s.walkMetres < 1000 ? `${s.walkMetres} m` : km(s.walkMetres / 1000)}
              </Muted>
            </Row>
            <Row>
              <Ionicons name="car" size={16} color={colors.ink2} />
              <Muted className="ml-1">
                {s.vehicleCode} there in {s.availableNow ? inMinutes(s.rideInMinutes) : `~${inMinutes(s.rideInMinutes)}`}
              </Muted>
            </Row>
          </Row>
          <Muted className="mt-1 text-xs">{s.route.label}</Muted>
          <Button className="mt-3" label={actionLabel} icon="navigate" size="sm" variant="secondary" loading={busy} onPress={() => onUse(s)} />
        </Card>
      ))}
    </View>
  );
}

// ------------------------------------------------------------------ cabs on duty

const STATUS_TONE: Record<NearbyCabStatus, 'teal' | 'amber' | 'danger' | 'neutral' | 'accent'> = {
  FREE: 'teal',
  ON_TRIP: 'accent',
  FULL: 'amber',
  SHUTTLE: 'neutral',
  OFF_ROAD: 'danger',
  BLOCKED: 'danger',
  UNAVAILABLE: 'neutral',
};

/** Every vehicle on duty, nearest first: driver, seats, distance and when it can come. */
export function NearbyCabs({ a }: { a: AvailabilityDto }) {
  const [all, setAll] = useState(false);
  if (a.nearbyCabs.length === 0) return null;
  const shown = all ? a.nearbyCabs : a.nearbyCabs.slice(0, 4);
  return (
    <View>
      <SectionTitle
        title={`Cabs on duty near ${a.pickup.name} · ${a.nearbyCabs.length}`}
        action={
          a.nearbyCabs.length > 4 ? (
            <Button label={all ? 'Show fewer' : 'Show all'} size="sm" variant="ghost" onPress={() => setAll(!all)} />
          ) : undefined
        }
      />
      {shown.map((c) => (
        <CabCard key={c.vehicleId} c={c} />
      ))}
    </View>
  );
}

function CabCard({ c }: { c: NearbyCabDto }) {
  const when =
    c.canTakeYou && c.etaMinutes != null
      ? c.etaMinutes < 1
        ? 'Can come now'
        : `Can come in ${inMinutes(c.etaMinutes)}`
      : c.availableInMinutes != null
        ? c.status === 'SHUTTLE'
          ? `At your stop in ${inMinutes(c.availableInMinutes)}`
          : `Free in about ${inMinutes(c.availableInMinutes)}`
        : 'Not coming';
  return (
    <Card className={`mb-2 ${c.canTakeYou ? 'border-teal' : ''}`}>
      <Row className="items-start justify-between">
        <View className="flex-1">
          <Text className="text-base font-bold text-ink">
            {c.vehicleCode} · {c.registrationNo}
          </Text>
          <Muted>
            {c.vehicleType === 'SHUTTLE' ? 'Shuttle' : 'Cab'} · {c.fleet}
            {c.at ? ` · at ${c.at}` : ''}
          </Muted>
        </View>
        <Pill label={c.statusLabel} tone={STATUS_TONE[c.status]} />
      </Row>
      <Row className="mt-3">
        <Stat label="Seats taken" value={`${c.seatsTaken} of ${c.capacity}`} />
        <Stat label="Distance" value={c.distanceKm == null ? '—' : km(c.distanceKm)} />
        <Stat label="Availability" value={when} strong={c.canTakeYou} />
      </Row>
      <Row className="mt-3 justify-between border-t border-line pt-3">
        <View className="flex-1">
          <Text className="text-sm font-semibold text-ink">{c.driverName}</Text>
          <Muted>{c.driverPhone}</Muted>
        </View>
        <Button
          label="Call"
          icon="call"
          size="sm"
          variant="secondary"
          accessibilityLabel={`Call ${c.driverName}, driver of ${c.vehicleCode}`}
          onPress={() => Linking.openURL(`tel:${c.driverPhone.replace(/\s/g, '')}`)}
        />
      </Row>
      {c.note ? <Muted className="mt-2 text-xs">{c.note}</Muted> : null}
    </Card>
  );
}

function Stat({ label, value, strong = false }: { label: string; value: string; strong?: boolean }) {
  return (
    <View className="mr-2 flex-1">
      <Text className="text-xs text-ink-3">{label}</Text>
      <Text className={`text-sm font-bold ${strong ? 'text-teal-deep' : 'text-ink'}`}>{value}</Text>
    </View>
  );
}
