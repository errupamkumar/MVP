import { fmt } from '@ecoride/core';
import { Avatar, Button, Card, colors, confirmAction, Divider, Header, Ionicons, ListRow, Row, Screen, SectionLabel, space, T } from '@ecoride/mobile';
import { useQueryClient } from '@tanstack/react-query';
import { router } from 'expo-router';
import { View } from 'react-native';

import { api, API_URL, auth } from '@/lib/api';
import { useBooking } from '@/lib/booking';
import { keys, useMe, usePlaces } from '@/lib/queries';

export default function Account() {
  const { signOut } = auth.useAuth();
  const queryClient = useQueryClient();
  const me = useMe().data;
  const places = usePlaces().data ?? [];
  const booking = useBooking();

  async function saveCurrentPickupAs(label: 'HOME' | 'WORK') {
    const p = booking.pickup;
    if (!p) return;
    await api.savePlace({ label, name: label === 'HOME' ? 'Home' : 'Work', address: p.address, lat: p.lat, lng: p.lng });
    void queryClient.invalidateQueries({ queryKey: keys.places });
  }

  return (
    <Screen>
      <Header title="Account" onBack={() => router.back()} />
      {me ? (
        <Card>
          <Row gap={space.md}>
            <Avatar name={me.name} size={58} color={colors.brand} />
            <View style={{ flex: 1 }}>
              <T v="h1">{me.name}</T>
              <T v="muted">{fmt.phone(me.phone)}{me.email ? ` · ${me.email}` : ''}</T>
              <Row gap={4} style={{ marginTop: 4 }}>
                <Ionicons name="star" size={13} color={colors.gold} />
                <T v="small" style={{ color: colors.ink2, fontWeight: '600' }}>
                  {fmt.rating(me.rating)} rider rating
                </T>
                <T v="small">· {me.completedRides} rides · since {fmt.date(me.memberSince)}</T>
              </Row>
            </View>
          </Row>
        </Card>
      ) : null}

      <Card style={{ marginTop: space.md, paddingVertical: space.xs }}>
        <ListRow icon="time-outline" title="Your rides" subtitle="Receipts and ratings" onPress={() => router.push('/rides')} right={<Ionicons name="chevron-forward" size={18} color={colors.ink4} />} />
        <Divider style={{ marginVertical: 0 }} />
        <ListRow icon="wallet-outline" title="Wallet" subtitle={me ? `Balance ${fmt.rupees(me.walletBalance)}` : undefined} onPress={() => router.push('/wallet')} right={<Ionicons name="chevron-forward" size={18} color={colors.ink4} />} />
      </Card>

      <SectionLabel>Saved places</SectionLabel>
      <Card style={{ paddingVertical: space.xs }}>
        {places.map((p, i) => (
          <View key={p.id}>
            {i > 0 ? <Divider style={{ marginVertical: 0 }} /> : null}
            <ListRow
              icon={p.label === 'HOME' ? 'home' : p.label === 'WORK' ? 'briefcase' : 'bookmark'}
              title={p.label === 'HOME' ? 'Home' : p.label === 'WORK' ? 'Work' : p.name}
              subtitle={p.address}
              right={
                <Ionicons
                  name="trash-outline"
                  size={18}
                  color={colors.ink3}
                  onPress={async () => {
                    await api.deletePlace(p.id);
                    void queryClient.invalidateQueries({ queryKey: keys.places });
                  }}
                />
              }
            />
          </View>
        ))}
        {!places.length ? <T v="muted" style={{ paddingVertical: space.md }}>No saved places yet.</T> : null}
      </Card>
      {booking.pickup ? (
        <Row style={{ marginTop: space.sm }}>
          <Button label="Set Home to my pickup" size="sm" variant="light" icon="home-outline" onPress={() => saveCurrentPickupAs('HOME')} />
          <Button label="Set Work to my pickup" size="sm" variant="light" icon="briefcase-outline" onPress={() => saveCurrentPickupAs('WORK')} />
        </Row>
      ) : null}

      <Button
        label="Sign out"
        variant="secondary"
        icon="log-out-outline"
        style={{ marginTop: space.xxl }}
        onPress={async () => {
          if (await confirmAction('Sign out?', 'You can sign in again any time.', 'Sign out')) {
            await signOut();
            router.replace('/welcome');
          }
        }}
      />
      <T v="small" style={{ textAlign: 'center', marginTop: space.lg }}>
        EcoRide rider app 0.1.0 · {API_URL}
      </T>
    </Screen>
  );
}
