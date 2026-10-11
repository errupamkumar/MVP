import { fmt, toApiError, type RiderRide } from '@ecoride/core';
import {
  Badge,
  Banner,
  Button,
  Card,
  Chip,
  colors,
  Divider,
  Header,
  Ionicons,
  KeyValue,
  Loading,
  RouteSummary,
  Row,
  Screen,
  SectionLabel,
  space,
  Stars,
  T,
} from '@ecoride/mobile';
import { useQueryClient } from '@tanstack/react-query';
import { router, useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import { TextInput, View } from 'react-native';

import { FareLines } from '@/components/FareLines';
import { DriverRow } from '@/components/RideParts';
import { api, API_URL } from '@/lib/api';
import { keys, useRide } from '@/lib/queries';

const GOOD_TAGS = ['Smooth ride', 'Clean car', 'Polite driver', 'On time', 'Safe driving'];
const BAD_TAGS = ['Late pickup', 'Rough driving', 'Took a long route', 'Car not clean', 'Rude behaviour'];
const TIPS = [0, 10, 20, 50];

export default function TripReceipt() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const { data: ride } = useRide(Number(id));
  if (!ride) return <Loading label="Loading your receipt…" />;
  const fare = ride.fare.fare ?? ride.fare.quote;
  const completed = ride.status === 'COMPLETED';
  const pendingCash = ride.fare.method === 'CASH' && ride.fare.paymentStatus === 'PENDING';

  return (
    <Screen footer={<Button label="Done" onPress={() => router.replace('/home')} testID="receipt-done" />}>
      <Header title={completed ? 'Trip receipt' : 'Ride details'} onBack={() => (router.canGoBack() ? router.back() : router.replace('/home'))} />
      {completed ? (
        <View style={{ alignItems: 'center', marginBottom: space.lg }}>
          <View style={{ width: 64, height: 64, borderRadius: 32, backgroundColor: colors.brandSoft, alignItems: 'center', justifyContent: 'center' }}>
            <Ionicons name="checkmark" size={36} color={colors.brand} />
          </View>
          <T v="title" style={{ marginTop: space.md }}>
            You've arrived
          </T>
          <T v="muted">
            {fmt.shortPlace(ride.drop.address)} · {fmt.time(ride.completedAt)}
          </T>
        </View>
      ) : null}

      <Card>
        <Row style={{ justifyContent: 'space-between' }}>
          <View>
            <T v="label">{completed ? 'Total fare' : 'Fare'}</T>
            <T v="display" style={{ marginTop: 2 }} testID="receipt-total">
              {fmt.rupees(ride.fare.total)}
            </T>
          </View>
          <View style={{ alignItems: 'flex-end', gap: 6 }}>
            <Badge
              label={ride.fare.paymentStatus === 'PAID' ? `Paid · ${fmt.PAYMENT_LABEL[ride.fare.method]}` : `${fmt.PAYMENT_LABEL[ride.fare.method]} · due`}
              tone={ride.fare.paymentStatus === 'PAID' ? 'brand' : 'warn'}
              icon={ride.fare.paymentStatus === 'PAID' ? 'checkmark-circle' : 'cash-outline'}
            />
            {ride.fare.tip > 0 ? <Badge label={`+ ${fmt.rupees(ride.fare.tip)} tip`} tone="info" /> : null}
          </View>
        </Row>
        {pendingCash ? (
          <View style={{ marginTop: space.md }}>
            <Banner tone="warn" message={`Please pay ${fmt.rupees(ride.fare.total)} in cash to your driver.`} icon="cash-outline" />
          </View>
        ) : null}
      </Card>

      {completed ? <RateCard ride={ride} /> : null}

      {completed ? <GpsCheck ride={ride} /> : null}

      {fare ? (
        <>
          <SectionLabel>Fare breakdown</SectionLabel>
          <Card>
            <FareLines fare={fare} />
          </Card>
        </>
      ) : null}

      <SectionLabel>Trip</SectionLabel>
      <Card>
        <RouteSummary pickup={ride.pickup.address} drop={ride.drop.address} />
        <Divider />
        <KeyValue label="Ride" value={`${ride.vehicleTypeName} · ${ride.code}`} />
        <KeyValue label="Requested" value={fmt.dateTime(ride.requestedAt)} />
        {ride.startedAt ? <KeyValue label="Picked up" value={fmt.time(ride.startedAt)} /> : null}
        {ride.completedAt ? <KeyValue label="Dropped" value={fmt.time(ride.completedAt)} /> : null}
        {ride.startedAt && ride.completedAt ? (
          <KeyValue label="Trip time" value={fmt.minutes((new Date(ride.completedAt).getTime() - new Date(ride.startedAt).getTime()) / 1000)} />
        ) : null}
        {ride.driver ? (
          <>
            <Divider />
            <DriverRow driver={ride.driver} />
          </>
        ) : null}
      </Card>
    </Screen>
  );
}

function RateCard({ ride }: { ride: RiderRide }) {
  const queryClient = useQueryClient();
  const [stars, setStars] = useState(0);
  const [tags, setTags] = useState<string[]>([]);
  const [tip, setTip] = useState(0);
  const [comment, setComment] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const name = ride.driver?.name.split(' ')[0] ?? 'your driver';

  if (ride.myRating != null) {
    return (
      <Card style={{ marginTop: space.md }}>
        <Row>
          <Stars value={ride.myRating} size={18} />
          <T v="bodyStrong">You rated {name}</T>
        </Row>
        {ride.fare.tip > 0 ? (
          <T v="muted" style={{ marginTop: 4 }}>
            Thanks for the {fmt.rupees(ride.fare.tip)} tip. It goes to {name} in full.
          </T>
        ) : null}
      </Card>
    );
  }

  const options = stars >= 4 ? GOOD_TAGS : BAD_TAGS;
  return (
    <Card style={{ marginTop: space.md }}>
      <T v="h2" style={{ textAlign: 'center' }}>
        How was your ride with {name}?
      </T>
      <View style={{ alignItems: 'center', marginTop: space.md }} testID="rate-stars">
        <Stars value={stars} size={36} onChange={setStars} />
      </View>
      {stars > 0 ? (
        <>
          <Row style={{ flexWrap: 'wrap', justifyContent: 'center', marginTop: space.lg }}>
            {options.map((t) => (
              <Chip key={t} label={t} selected={tags.includes(t)} onPress={() => setTags((cur) => (cur.includes(t) ? cur.filter((x) => x !== t) : [...cur, t]))} />
            ))}
          </Row>
          <T v="label" style={{ marginTop: space.lg, textAlign: 'center' }}>
            Add a tip for {name}
          </T>
          <Row style={{ justifyContent: 'center', marginTop: space.sm }}>
            {TIPS.map((t) => (
              <Chip key={t} label={t === 0 ? 'No tip' : fmt.rupees(t)} selected={tip === t} onPress={() => setTip(t)} />
            ))}
          </Row>
          <TextInput
            value={comment}
            onChangeText={setComment}
            placeholder="Anything else? (optional)"
            placeholderTextColor={colors.ink4}
            style={{ marginTop: space.lg, backgroundColor: colors.mist, borderRadius: 10, padding: 12, fontSize: 15, color: colors.ink, minHeight: 44 }}
          />
          {error ? (
            <T v="small" style={{ color: colors.danger, marginTop: space.sm }}>
              {error}
            </T>
          ) : null}
          <Button
            label={tip > 0 ? `Submit and tip ${fmt.rupees(tip)}` : 'Submit rating'}
            variant="brand"
            style={{ marginTop: space.md }}
            loading={busy}
            testID="rate-submit"
            onPress={async () => {
              setBusy(true);
              setError(null);
              try {
                await api.rate(ride.id, { stars, tags, comment: comment.trim() || undefined, tip });
                await queryClient.invalidateQueries({ queryKey: keys.ride(ride.id) });
                void queryClient.invalidateQueries({ queryKey: keys.current });
                void queryClient.invalidateQueries({ queryKey: keys.wallet });
              } catch (e) {
                setError(toApiError(e, API_URL).message);
              } finally {
                setBusy(false);
              }
            }}
          />
        </>
      ) : null}
    </Card>
  );
}

/** How the GPS distance decided the fare: the "dynamic distance" part of the receipt. */
function GpsCheck({ ride }: { ride: RiderRide }) {
  const planned = ride.estDistanceM;
  const actual = ride.actualDistanceM;
  const diffPct = planned ? Math.round((Math.abs(actual - planned) / planned) * 100) : 0;
  const upfront = ride.fare.policy !== 'RECALCULATED';
  const max = Math.max(planned, actual, 1);
  return (
    <>
      <SectionLabel>Distance check</SectionLabel>
      <Card>
        <Row style={{ justifyContent: 'space-between' }}>
          <T v="bodyStrong">Measured by GPS</T>
          <T v="h2">{(actual / 1000).toFixed(2)} km</T>
        </Row>
        <View style={{ height: 8, borderRadius: 4, backgroundColor: colors.mist, marginVertical: 6 }}>
          <View style={{ width: `${(actual / max) * 100}%`, height: 8, borderRadius: 4, backgroundColor: colors.brand }} />
        </View>
        <Row style={{ justifyContent: 'space-between', marginTop: space.sm }}>
          <T v="bodyStrong">Planned route</T>
          <T v="h2">{(planned / 1000).toFixed(2)} km</T>
        </Row>
        <View style={{ height: 8, borderRadius: 4, backgroundColor: colors.mist, marginVertical: 6 }}>
          <View style={{ width: `${(planned / max) * 100}%`, height: 8, borderRadius: 4, backgroundColor: colors.ink3 }} />
        </View>
        <T v="muted" style={{ marginTop: space.sm }}>
          {upfront
            ? `The trip was within ${diffPct}% of the planned route, so your upfront price stands${ride.fare.fare && ride.fare.fare.waitingFare > 0 ? ' (plus waiting time)' : ''}.`
            : `The trip differed from the planned route by ${diffPct}%, so the fare was recalculated on the GPS distance and actual time.`}
        </T>
      </Card>
    </>
  );
}
