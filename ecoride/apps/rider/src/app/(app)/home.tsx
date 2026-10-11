import { fmt, type PlaceResult } from '@ecoride/core';
import {
  Badge,
  Banner,
  Button,
  Chip,
  colors,
  IconButton,
  Ionicons,
  MapMarker,
  RideMap,
  Row,
  Sheet,
  shadow,
  space,
  T,
} from '@ecoride/mobile';
import { useQuery } from '@tanstack/react-query';
import { router, useLocalSearchParams } from 'expo-router';
import { useEffect, useMemo } from 'react';
import { Pressable, ScrollView, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { api } from '@/lib/api';
import { useBooking } from '@/lib/booking';
import { placeLabel } from '@/lib/places';
import { useRiderPosition } from '@/lib/position';
import { useCurrentRide, useMe, useNearby, usePlaces, useWallet } from '@/lib/queries';

function greeting(): string {
  const h = new Date().getHours();
  return h < 12 ? 'Good morning' : h < 17 ? 'Good afternoon' : 'Good evening';
}

export default function Home() {
  const insets = useSafeAreaInsets();
  const params = useLocalSearchParams<{ welcome?: string }>();
  const { position, source } = useRiderPosition();
  const me = useMe().data;
  const wallet = useWallet().data;
  const nearby = useNearby(source === 'LOCATING' ? null : position).data;
  const current = useCurrentRide().data?.ride ?? null;
  const places = usePlaces().data ?? [];
  const booking = useBooking();

  const rounded = `${position.lat.toFixed(4)},${position.lng.toFixed(4)}`;
  const here = useQuery({
    queryKey: ['reverse', rounded],
    queryFn: () => api.reverse(position),
    enabled: source !== 'LOCATING',
    staleTime: 60_000,
  }).data;
  const suggestions = useQuery({
    queryKey: ['suggestions', rounded],
    queryFn: () => api.search('', position),
    enabled: source !== 'LOCATING',
    staleTime: 300_000,
  }).data;

  // The pickup follows the rider until they choose another one.
  const { pickupIsCurrent, setPickup } = booking;
  useEffect(() => {
    if (pickupIsCurrent && here && source !== 'LOCATING') {
      setPickup({ lat: position.lat, lng: position.lng, address: placeLabel(here) }, true);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [here, source, pickupIsCurrent]);

  const markers = useMemo<MapMarker[]>(() => {
    const list: MapMarker[] = (nearby?.cars ?? []).map((c, i) => ({
      id: `car-${i}`,
      kind: 'car',
      lat: c.lat,
      lng: c.lng,
      heading: c.heading,
      vehicleType: c.vehicleType,
    }));
    list.push({ id: 'me', kind: 'me', lat: position.lat, lng: position.lng });
    return list;
  }, [nearby, position.lat, position.lng]);

  // Show the rider with the few nearest cars around them.
  const nearestCars = useMemo(
    () =>
      (nearby?.cars ?? [])
        .map((c) => ({ lat: c.lat, lng: c.lng, d: Math.hypot(c.lat - position.lat, c.lng - position.lng) }))
        .sort((a, b) => a.d - b.d)
        .slice(0, 3)
        .map(({ lat, lng }) => ({ lat, lng })),
    // Only when the cars first load: later refreshes move them without re-zooming the map.
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [!!nearby, source],
  );

  const goTo = (place: { lat: number; lng: number; name: string; address: string }) => {
    booking.setDrop({ lat: place.lat, lng: place.lng, address: place.name === place.address ? place.name : `${place.name}, ${place.address}` });
    router.push('/choose-ride');
  };

  const firstName = me?.name.split(' ')[0] ?? '';
  const eta = nearby?.etaByType ? Math.min(...Object.values(nearby.etaByType)) : null;

  return (
    <View style={{ flex: 1 }}>
      <RideMap
        initialCenter={position}
        initialZoom={15}
        markers={markers}
        fit={{ key: `${source}-${nearby ? 'cars' : 'none'}`, points: [position, ...nearestCars] }}
        padding={{ top: 80, right: 20, bottom: 380, left: 20 }}
      />

      <View style={{ position: 'absolute', top: insets.top + space.md, left: space.lg, right: space.lg }}>
        <Row style={{ justifyContent: 'space-between' }}>
          <IconButton icon="menu" label="Account" onPress={() => router.push('/account')} size={44} />
          <Pressable onPress={() => router.push('/wallet')} accessibilityRole="button" accessibilityLabel="Wallet">
            <View style={[{ backgroundColor: colors.white, borderRadius: 999, paddingHorizontal: 14, height: 40, flexDirection: 'row', alignItems: 'center', gap: 6 }, shadow(1)]}>
              <Ionicons name="wallet-outline" size={17} color={colors.ink} />
              <T v="bodyStrong">{wallet ? fmt.rupees(wallet.balance) : '…'}</T>
            </View>
          </Pressable>
        </Row>
      </View>

      <View style={{ position: 'absolute', left: 0, right: 0, bottom: 0 }}>
        <View style={{ maxWidth: 560, width: '100%', alignSelf: 'center' }}>
          <Sheet>
            {params.welcome ? (
              <View style={{ marginBottom: space.md }}>
                <Banner tone="brand" title="Welcome to EcoRide" message="₹200 has been added to your wallet. Your first ride is 50% off with code FIRST50." />
              </View>
            ) : null}
            {current ? (
              <Pressable onPress={() => router.push(current.status === 'COMPLETED' ? `/trip/${current.id}` : `/ride/${current.id}`)}>
                <View style={{ backgroundColor: colors.ink, borderRadius: 14, padding: space.md, marginBottom: space.md, flexDirection: 'row', alignItems: 'center', gap: space.md }}>
                  <Ionicons name="car-sport" size={22} color={colors.white} />
                  <View style={{ flex: 1 }}>
                    <T v="bodyStrong" style={{ color: colors.white }}>
                      {current.status === 'COMPLETED' ? 'Rate your last trip' : fmt.STATUS_LABEL[current.status]}
                    </T>
                    <T v="small" style={{ color: '#D1D5DB' }} numberOfLines={1}>
                      {fmt.shortPlace(current.drop.address)}
                    </T>
                  </View>
                  <Ionicons name="chevron-forward" size={20} color={colors.white} />
                </View>
              </Pressable>
            ) : null}

            <T v="h1">
              {greeting()}
              {firstName ? `, ${firstName}` : ''}
            </T>
            <Pressable onPress={() => router.push('/where-to')} accessibilityRole="search" testID="home-where-to">
              <View style={{ marginTop: space.md, backgroundColor: colors.mist, borderRadius: 14, height: 54, flexDirection: 'row', alignItems: 'center', paddingHorizontal: space.lg, gap: space.md }}>
                <Ionicons name="search" size={20} color={colors.ink} />
                <T v="h2" style={{ flex: 1, color: colors.ink2 }}>
                  Where to?
                </T>
                <View style={{ backgroundColor: colors.white, borderRadius: 999, paddingHorizontal: 10, paddingVertical: 6, flexDirection: 'row', gap: 4, alignItems: 'center' }}>
                  <Ionicons name="time" size={14} color={colors.ink} />
                  <T v="small" style={{ color: colors.ink, fontWeight: '600' }}>
                    Now
                  </T>
                </View>
              </View>
            </Pressable>

            <ScrollView horizontal showsHorizontalScrollIndicator={false} style={{ marginTop: space.md }} contentContainerStyle={{ gap: space.sm }}>
              {places.map((p) => (
                <Chip
                  key={p.id}
                  label={p.label === 'HOME' ? 'Home' : p.label === 'WORK' ? 'Work' : p.name}
                  icon={p.label === 'HOME' ? 'home' : p.label === 'WORK' ? 'briefcase' : 'bookmark'}
                  onPress={() => goTo({ ...p, name: p.label === 'HOME' ? 'Home' : p.label === 'WORK' ? 'Work' : p.name })}
                />
              ))}
              {(suggestions ?? []).slice(0, 4).map((s: PlaceResult) => (
                <Chip key={s.name} label={s.name} icon="location-outline" onPress={() => goTo(s)} />
              ))}
            </ScrollView>

            <View style={{ marginTop: space.lg, flexDirection: 'row', alignItems: 'center', gap: space.sm }}>
              <View style={{ width: 10, height: 10, borderRadius: 5, backgroundColor: colors.brand }} />
              <T v="muted" style={{ flex: 1 }} numberOfLines={1}>
                {source === 'LOCATING' ? 'Finding your location…' : `Pickup: ${booking.pickup?.address ?? (here ? placeLabel(here) : '…')}`}
              </T>
              {eta != null ? <Badge label={`Cars ${fmt.minutes(eta)} away`} tone="brand" icon="car" /> : null}
            </View>
            {source === 'DEMO' ? (
              <View style={{ marginTop: space.md }}>
                <Banner
                  tone="info"
                  message="Your phone's location is outside Pune (or unavailable), so the demo puts you at Balewadi High Street. Search any pickup in Pune to change it."
                />
              </View>
            ) : null}
            {nearby && nearby.cars.length === 0 && source !== 'LOCATING' ? (
              <Button label="No cars nearby right now" variant="secondary" disabled style={{ marginTop: space.md }} />
            ) : null}
          </Sheet>
        </View>
      </View>
    </View>
  );
}
