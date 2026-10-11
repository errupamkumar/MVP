import { fmt } from '@ecoride/core';
import {
  Avatar,
  Badge,
  Button,
  Card,
  Chip,
  colors,
  confirmAction,
  Divider,
  Header,
  Ionicons,
  KeyValue,
  ListRow,
  Row,
  Screen,
  SectionLabel,
  space,
  T,
} from '@ecoride/mobile';
import { router } from 'expo-router';
import { Switch, View } from 'react-native';

import { API_URL, auth } from '@/lib/api';
import { useGpsPrefs } from '@/lib/gpsPrefs';
import { useDriverState } from '@/lib/queries';

const SPEEDS = [1, 4, 8, 15];
const STATUS_TONE = { APPROVED: 'brand', PENDING_APPROVAL: 'warn', SUSPENDED: 'danger', REJECTED: 'danger' } as const;

export default function Profile() {
  const { signOut, session } = auth.useAuth();
  const state = useDriverState().data;
  const gps = useGpsPrefs();
  const p = state?.profile ?? session?.profile;
  if (!p) return null;
  return (
    <Screen>
      <Header title="Profile" onBack={() => router.back()} />
      <Card>
        <Row gap={space.md}>
          <Avatar name={p.name} size={58} />
          <View style={{ flex: 1 }}>
            <T v="h1">{p.name}</T>
            <T v="muted">{fmt.phone(p.phone)}</T>
            <Row gap={6} style={{ marginTop: 4 }}>
              <Badge label={p.status.replace('_', ' ').toLowerCase()} tone={STATUS_TONE[p.status]} />
              <Ionicons name="star" size={13} color={colors.gold} />
              <T v="small" style={{ color: colors.ink2, fontWeight: '600' }}>
                {fmt.rating(p.rating)} ({p.ratingCount})
              </T>
            </Row>
          </View>
        </Row>
      </Card>

      <Card style={{ marginTop: space.md, paddingVertical: space.xs }}>
        <ListRow icon="wallet-outline" title="Earnings" subtitle="Today, this week, payouts" onPress={() => router.push('/earnings')} right={<Ionicons name="chevron-forward" size={18} color={colors.ink4} />} />
        <Divider style={{ marginVertical: 0 }} />
        <ListRow icon="time-outline" title="Trips" subtitle="Your completed and cancelled rides" onPress={() => router.push('/trips')} right={<Ionicons name="chevron-forward" size={18} color={colors.ink4} />} />
      </Card>

      <SectionLabel>Vehicle and documents</SectionLabel>
      <Card>
        <KeyValue label="Vehicle" value={`${p.vehicle.color} ${p.vehicle.make} ${p.vehicle.model}`} />
        <KeyValue label="Type" value={p.vehicle.typeName} />
        <KeyValue label="Registration" value={p.vehicle.plate} />
        <KeyValue label="Year" value={String(p.vehicle.year)} />
        <KeyValue label="Driving licence" value={p.licenseNo} />
        <KeyValue label="Acceptance rate" value={p.acceptanceRate == null ? '—' : `${p.acceptanceRate}%`} />
        <KeyValue label="Driving since" value={fmt.date(p.approvedAt ?? p.memberSince)} />
      </Card>

      <SectionLabel>Demo GPS</SectionLabel>
      <Card>
        <Row style={{ justifyContent: 'space-between' }}>
          <View style={{ flex: 1, paddingRight: space.md }}>
            <T v="bodyStrong">Simulate driving</T>
            <T v="small">
              Replays the drive along the road route instead of the phone's GPS, so a demo on a laptop moves the car. Turn off on a phone to use real GPS.
            </T>
          </View>
          <Switch value={gps.simulate} onValueChange={gps.setSimulate} trackColor={{ true: colors.brand, false: colors.line }} />
        </Row>
        {gps.simulate ? (
          <>
            <T v="small" style={{ marginTop: space.md, fontWeight: '600' }}>
              Replay speed (real time is 36 km/h)
            </T>
            <Row style={{ marginTop: space.sm }}>
              {SPEEDS.map((s) => (
                <Chip key={s} label={s === 1 ? 'Real time' : `${s}x`} selected={gps.speedX === s} onPress={() => gps.setSpeedX(s)} />
              ))}
            </Row>
          </>
        ) : null}
      </Card>

      <Button
        label="Sign out"
        variant="secondary"
        icon="log-out-outline"
        style={{ marginTop: space.xxl }}
        onPress={async () => {
          if (p.availability !== 'OFFLINE') {
            await confirmAction('Go offline first', 'Go offline (and finish any trip) before signing out.', 'OK');
            return;
          }
          if (await confirmAction('Sign out?', 'You can sign in again any time.', 'Sign out')) {
            await signOut();
            router.replace('/welcome');
          }
        }}
      />
      <T v="small" style={{ textAlign: 'center', marginTop: space.lg }}>
        EcoRide Driver 0.1.0 · {API_URL}
      </T>
    </Screen>
  );
}
