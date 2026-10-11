import { fmt } from '@ecoride/core';
import { Badge, Card, colors, Empty, Header, Loading, Row, Screen, space, Stars, T, VehicleIcon } from '@ecoride/mobile';
import { router } from 'expo-router';
import { View } from 'react-native';

import { useHistory } from '@/lib/queries';

export default function Rides() {
  const { data, isLoading } = useHistory();
  return (
    <Screen>
      <Header title="Your rides" onBack={() => router.back()} />
      {isLoading ? <Loading /> : null}
      {data && !data.length ? <Empty icon="car-outline" title="No rides yet" message="Your trips will appear here." /> : null}
      {(data ?? []).map((r) => (
        <Card key={r.id} style={{ marginBottom: space.md }} onPress={() => router.push(r.status === 'COMPLETED' ? `/trip/${r.id}` : `/ride/${r.id}`)}>
          <Row gap={space.md}>
            <VehicleIcon type={r.vehicleType} size={44} />
            <View style={{ flex: 1 }}>
              <T v="bodyStrong" numberOfLines={1}>
                {fmt.shortPlace(r.dropAddress)}
              </T>
              <T v="small" numberOfLines={1}>
                {fmt.dateTime(r.requestedAt)} · {r.vehicleTypeName}
                {r.counterpartName ? ` · ${r.counterpartName}` : ''}
              </T>
            </View>
            <View style={{ alignItems: 'flex-end', gap: 4 }}>
              <T v="h2">{r.total != null && r.total > 0 ? fmt.rupees(r.total) : '—'}</T>
              {r.status === 'COMPLETED' ? (
                r.rating ? <Stars value={r.rating} size={11} /> : <Badge label="Rate" tone="info" />
              ) : (
                <Badge label={fmt.STATUS_LABEL[r.status]} tone={r.status === 'CANCELLED' || r.status === 'NO_DRIVERS' ? 'danger' : 'brand'} />
              )}
            </View>
          </Row>
          <T v="small" style={{ marginTop: space.sm, color: colors.ink3 }} numberOfLines={1}>
            From {fmt.shortPlace(r.pickupAddress)} · {r.distanceKm} km
          </T>
        </Card>
      ))}
    </Screen>
  );
}
