import { fmt } from '@ecoride/core';
import { Badge, Card, Empty, Header, Loading, Row, Screen, space, Stars, T, VehicleIcon } from '@ecoride/mobile';
import { router } from 'expo-router';
import { View } from 'react-native';

import { useHistory } from '@/lib/queries';

export default function Trips() {
  const { data, isLoading } = useHistory();
  return (
    <Screen>
      <Header title="Trips" onBack={() => router.back()} />
      {isLoading ? <Loading /> : null}
      {data && !data.length ? <Empty icon="car-outline" title="No trips yet" message="Go online to get your first request." /> : null}
      {(data ?? []).map((r) => (
        <Card key={r.id} style={{ marginBottom: space.md }}>
          <Row gap={space.md}>
            <VehicleIcon type={r.vehicleType} size={40} />
            <View style={{ flex: 1 }}>
              <T v="bodyStrong" numberOfLines={1}>
                {fmt.shortPlace(r.pickupAddress)} → {fmt.shortPlace(r.dropAddress)}
              </T>
              <T v="small">
                {fmt.dateTime(r.requestedAt)} · {r.counterpartName ?? 'Rider'} · {r.distanceKm} km
              </T>
            </View>
            <View style={{ alignItems: 'flex-end', gap: 4 }}>
              <T v="h2">{r.total ? fmt.rupees(r.total) : '—'}</T>
              {r.status === 'COMPLETED' ? (
                r.rating ? <Stars value={r.rating} size={11} /> : <Badge label="Unrated" />
              ) : (
                <Badge label={fmt.STATUS_LABEL[r.status]} tone="danger" />
              )}
            </View>
          </Row>
        </Card>
      ))}
    </Screen>
  );
}
