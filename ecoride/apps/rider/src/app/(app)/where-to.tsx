import { fmt, type PlaceResult } from '@ecoride/core';
import { colors, Header, IconName, Ionicons, ListRow, Loading, Screen, SectionLabel, space, T, useDebounced } from '@ecoride/mobile';
import { useQuery } from '@tanstack/react-query';
import { router } from 'expo-router';
import { useRef, useState } from 'react';
import { TextInput, View } from 'react-native';

import { api } from '@/lib/api';
import { useBooking } from '@/lib/booking';
import { placeLabel } from '@/lib/places';
import { useRiderPosition } from '@/lib/position';
import { usePlaces } from '@/lib/queries';

const CATEGORY_ICON: Record<string, IconName> = {
  AIRPORT: 'airplane',
  STATION: 'train',
  MALL: 'bag-handle',
  WORK: 'briefcase',
  EDU: 'school',
  HOSPITAL: 'medkit',
  LANDMARK: 'flag',
  AREA: 'location',
  ADDRESS: 'location-outline',
};

type Field = 'pickup' | 'drop';

export default function WhereTo() {
  const booking = useBooking();
  const { position, source } = useRiderPosition();
  const saved = usePlaces().data ?? [];
  const [field, setField] = useState<Field>('drop');
  const [pickupText, setPickupText] = useState('');
  const [dropText, setDropText] = useState('');
  const dropRef = useRef<TextInput>(null);
  const text = field === 'pickup' ? pickupText : dropText;
  const q = useDebounced(text.trim(), 300);
  const near = booking.pickup ?? position;
  const results = useQuery({
    queryKey: ['search', q, near.lat.toFixed(3), near.lng.toFixed(3)],
    queryFn: () => api.search(q, near),
    placeholderData: (prev) => prev,
  });

  function choose(place: { lat: number; lng: number; name: string; address: string }) {
    const chosen = { lat: place.lat, lng: place.lng, address: placeLabel(place) };
    if (field === 'pickup') {
      booking.setPickup(chosen, false);
      setPickupText('');
      setField('drop');
      dropRef.current?.focus();
      if (booking.drop) router.replace('/choose-ride');
      return;
    }
    booking.setDrop(chosen);
    router.replace('/choose-ride');
  }

  async function useCurrent() {
    const here = await api.reverse(position);
    booking.setPickup({ lat: position.lat, lng: position.lng, address: placeLabel(here) }, true);
    setPickupText('');
    setField('drop');
    dropRef.current?.focus();
  }

  return (
    <Screen>
      <Header title="Plan your ride" onBack={() => router.back()} />
      <View style={{ backgroundColor: colors.white, borderRadius: 16, padding: space.md, flexDirection: 'row', gap: space.md }}>
        <View style={{ alignItems: 'center', paddingTop: 17 }}>
          <View style={{ width: 10, height: 10, borderRadius: 5, backgroundColor: colors.brand }} />
          <View style={{ width: 2, height: 36, backgroundColor: colors.line, marginVertical: 3 }} />
          <View style={{ width: 10, height: 10, backgroundColor: colors.ink }} />
        </View>
        <View style={{ flex: 1, gap: space.sm }}>
          <TextInput
            value={field === 'pickup' ? pickupText : booking.pickup?.address ?? ''}
            onChangeText={setPickupText}
            onFocus={() => setField('pickup')}
            placeholder={source === 'LOCATING' ? 'Finding your location…' : 'Pickup location'}
            placeholderTextColor={colors.ink4}
            style={[inputStyle, field === 'pickup' && { backgroundColor: colors.white, borderColor: colors.ink }]}
            testID="where-pickup"
          />
          <TextInput
            ref={dropRef}
            autoFocus
            value={dropText}
            onChangeText={setDropText}
            onFocus={() => setField('drop')}
            placeholder="Where to?"
            placeholderTextColor={colors.ink4}
            style={[inputStyle, field === 'drop' && { backgroundColor: colors.white, borderColor: colors.ink }]}
            testID="where-drop"
          />
        </View>
      </View>

      {field === 'pickup' && !pickupText ? (
        <ListRow icon="navigate" iconBg={colors.infoSoft} iconColor={colors.info} title="Use my current location" subtitle={source === 'GPS' ? 'From your phone\'s GPS' : 'Demo location: Balewadi High Street'} onPress={useCurrent} />
      ) : null}

      {!text && saved.length ? (
        <>
          <SectionLabel>Saved places</SectionLabel>
          {saved.map((p) => (
            <ListRow
              key={p.id}
              icon={p.label === 'HOME' ? 'home' : p.label === 'WORK' ? 'briefcase' : 'bookmark'}
              title={p.label === 'HOME' ? 'Home' : p.label === 'WORK' ? 'Work' : p.name}
              subtitle={p.address}
              onPress={() => choose({ ...p, name: p.label === 'HOME' ? 'Home' : p.label === 'WORK' ? 'Work' : p.name })}
            />
          ))}
        </>
      ) : null}

      <SectionLabel>{text ? 'Results' : 'Popular in Pune'}</SectionLabel>
      {results.isLoading ? <Loading /> : null}
      {(results.data ?? []).map((r: PlaceResult, i: number) => (
        <ListRow
          key={`${r.name}-${i}`}
          icon={CATEGORY_ICON[r.category] ?? 'location-outline'}
          title={r.name}
          subtitle={[r.distanceM != null ? fmt.distance(r.distanceM) : null, r.address].filter(Boolean).join(' · ')}
          onPress={() => choose(r)}
        />
      ))}
      {text && results.data && !results.data.length && !results.isFetching ? (
        <View style={{ padding: space.xl, alignItems: 'center', gap: space.sm }}>
          <Ionicons name="search" size={28} color={colors.ink4} />
          <T v="muted">No places in Pune match “{text}”.</T>
        </View>
      ) : null}
    </Screen>
  );
}

const inputStyle = {
  backgroundColor: colors.mist,
  borderRadius: 10,
  borderWidth: 1.5,
  borderColor: 'transparent',
  paddingHorizontal: 12,
  height: 44,
  fontSize: 16,
  color: colors.ink,
  outlineWidth: 0,
} as const;
