import { decodePolyline, fmt, toApiError, type PaymentMethod, type QuoteOption } from '@ecoride/core';
import {
  Banner,
  Button,
  Chip,
  colors,
  IconButton,
  Ionicons,
  Loading,
  RideMap,
  Row,
  Sheet,
  space,
  T,
  VehicleIcon,
} from '@ecoride/mobile';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Redirect, router } from 'expo-router';
import { useEffect, useMemo, useState } from 'react';
import { Pressable, ScrollView, TextInput, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { api, API_URL } from '@/lib/api';
import { useBooking } from '@/lib/booking';
import { placeLabel } from '@/lib/places';
import { useRiderPosition } from '@/lib/position';
import { keys, useWallet } from '@/lib/queries';

const METHODS: { key: PaymentMethod; label: string; icon: 'cash-outline' | 'phone-portrait-outline' | 'wallet-outline' }[] = [
  { key: 'UPI', label: 'UPI', icon: 'phone-portrait-outline' },
  { key: 'CASH', label: 'Cash', icon: 'cash-outline' },
  { key: 'WALLET', label: 'Wallet', icon: 'wallet-outline' },
];

export default function ChooseRide() {
  const insets = useSafeAreaInsets();
  const queryClient = useQueryClient();
  const booking = useBooking();
  const { position } = useRiderPosition();
  const wallet = useWallet().data;
  const [promo, setPromo] = useState<string | null>(null);
  const [promoDraft, setPromoDraft] = useState('');
  const [promoOpen, setPromoOpen] = useState(false);
  const [selected, setSelected] = useState<string | null>(null);
  const [method, setMethod] = useState<PaymentMethod>('UPI');
  const [error, setError] = useState<string | null>(null);

  // A pickup is needed: when the rider went straight to a saved place, use where they are.
  useEffect(() => {
    if (!booking.pickup) {
      void api.reverse(position).then((here) => booking.setPickup({ lat: position.lat, lng: position.lng, address: placeLabel(here) }, true));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [booking.pickup]);

  const pickup = booking.pickup;
  const drop = booking.drop;
  const quote = useQuery({
    queryKey: ['quote', pickup?.lat, pickup?.lng, drop?.lat, drop?.lng, promo],
    queryFn: () => api.quote(pickup!, drop!, promo),
    enabled: !!pickup && !!drop,
    // Upfront prices are held for a few minutes; refresh before they lapse.
    refetchInterval: (q) => (q.state.data ? Math.max(30, q.state.data.validForS - 20) * 1000 : false),
  });
  const q = quote.data;

  useEffect(() => {
    if (!q || selected) return;
    const firstWithCars = q.options.find((o) => o.vehicleType === 'MINI' && o.etaS != null) ?? q.options.find((o) => o.etaS != null);
    setSelected((firstWithCars ?? q.options[0])?.vehicleType ?? null);
  }, [q, selected]);

  const route = useMemo(() => decodePolyline(q?.route.polyline), [q?.route.polyline]);
  const option = q?.options.find((o) => o.vehicleType === selected) ?? null;

  const book = useMutation({
    mutationFn: () => api.request(q!.quoteId, option!.vehicleType, method),
    onSuccess: (ride) => {
      void queryClient.invalidateQueries({ queryKey: keys.current });
      router.replace(`/ride/${ride.id}`);
    },
    onError: (e) => {
      const err = toApiError(e, API_URL);
      setError(err.message);
      if (err.code === 'QUOTE_EXPIRED' || err.status === 404) void quote.refetch();
    },
  });

  if (!drop) return <Redirect href="/where-to" />;

  const walletShort = method === 'WALLET' && wallet && option && wallet.balance < option.fare.total;
  const discount = option?.fare.discount ?? 0;

  return (
    <View style={{ flex: 1 }}>
      <RideMap
        initialCenter={pickup ?? position}
        initialZoom={13}
        routes={route.length ? [{ id: 'route', points: route, color: colors.route, width: 5 }] : []}
        markers={[
          ...(pickup ? [{ id: 'pickup', kind: 'pickup' as const, lat: pickup.lat, lng: pickup.lng, label: option?.etaS != null ? `Pickup · ${fmt.minutes(option.etaS)}` : 'Pickup' }] : []),
          { id: 'drop', kind: 'drop' as const, lat: drop.lat, lng: drop.lng, label: q ? fmt.minutes(q.route.durationS) : null },
        ]}
        fit={route.length ? { key: q?.route.polyline.slice(0, 40) ?? 'route', points: route } : null}
        padding={{ top: insets.top + 90, right: 40, bottom: 470, left: 40 }}
      />
      <View style={{ position: 'absolute', top: insets.top + space.md, left: space.lg }}>
        <IconButton icon="arrow-back" label="Back" onPress={() => router.back()} size={44} />
      </View>

      <View style={{ position: 'absolute', left: 0, right: 0, bottom: 0 }}>
        <View style={{ maxWidth: 560, width: '100%', alignSelf: 'center' }}>
          <Sheet>
            <Row style={{ justifyContent: 'space-between', marginBottom: space.xs }}>
              <T v="h1">Choose a ride</T>
              {q ? (
                <T v="muted">
                  {fmt.distance(q.route.distanceM)} · {fmt.minutes(q.route.durationS)}
                </T>
              ) : null}
            </Row>
            <Pressable onPress={() => router.push('/where-to')}>
              <T v="small" numberOfLines={1} style={{ marginBottom: space.sm }}>
                {fmt.shortPlace(pickup?.address)} → {fmt.shortPlace(drop.address)}
              </T>
            </Pressable>

            {quote.isError ? (
              <Banner tone="danger" message={toApiError(quote.error, API_URL).message} />
            ) : !q ? (
              <View style={{ height: 220 }}>
                <Loading label="Finding the best route and prices…" />
              </View>
            ) : (
              <>
                {q.surge.multiplier > 1 ? (
                  <View style={{ marginBottom: space.sm }}>
                    <Banner tone="warn" title={`Fares are ${q.surge.multiplier}x right now`} message={`High demand${q.surge.zone ? ` around ${q.surge.zone}` : ''}. Prices include the surge and never go above the city cap.`} icon="trending-up" />
                  </View>
                ) : null}
                <ScrollView style={{ maxHeight: 250 }}>
                  {q.options.map((o) => (
                    <OptionRow key={o.vehicleType} option={o} selected={o.vehicleType === selected} durationS={q.route.durationS} onPress={() => setSelected(o.vehicleType)} />
                  ))}
                </ScrollView>

                <Row style={{ marginTop: space.md, flexWrap: 'wrap' }}>
                  {METHODS.map((m) => (
                    <Chip
                      key={m.key}
                      icon={m.icon}
                      label={m.key === 'WALLET' && wallet ? `Wallet ${fmt.rupees(wallet.balance)}` : m.label}
                      selected={method === m.key}
                      onPress={() => setMethod(m.key)}
                    />
                  ))}
                  <Chip icon="pricetag-outline" label={promo ? promo : 'Promo'} selected={!!promo} onPress={() => setPromoOpen((v) => !v)} />
                </Row>
                {promoOpen ? (
                  <Row style={{ marginTop: space.sm }}>
                    <TextInput
                      value={promoDraft}
                      onChangeText={(t) => setPromoDraft(t.toUpperCase())}
                      placeholder="FIRST50"
                      autoCapitalize="characters"
                      placeholderTextColor={colors.ink4}
                      style={{ flex: 1, height: 42, borderRadius: 10, backgroundColor: colors.mist, paddingHorizontal: 12, fontSize: 15, color: colors.ink }}
                    />
                    <Button label="Apply" size="md" variant="primary" onPress={() => { setPromo(promoDraft.trim() || null); setPromoOpen(false); }} />
                    {promo ? <Button label="Remove" size="md" variant="ghost" onPress={() => { setPromo(null); setPromoDraft(''); }} /> : null}
                  </Row>
                ) : null}
                {q.promo ? (
                  <T v="small" style={{ marginTop: space.sm, color: q.promo.valid ? colors.brandDark : colors.danger }}>
                    {q.promo.valid ? `${q.promo.code}: ${q.promo.message}` : q.promo.message}
                  </T>
                ) : null}
                {walletShort ? (
                  <T v="small" style={{ marginTop: space.sm, color: colors.danger }}>
                    Wallet balance {fmt.rupees(wallet!.balance)} is less than the fare. Add money or pay another way.
                  </T>
                ) : null}
                {error ? (
                  <T v="small" style={{ marginTop: space.sm, color: colors.danger }}>
                    {error}
                  </T>
                ) : null}
                <Button
                  label={option ? `Book ${option.name} · ${fmt.rupees(option.fare.total)}` : 'Choose a ride'}
                  onPress={() => {
                    setError(null);
                    book.mutate();
                  }}
                  loading={book.isPending}
                  disabled={!option || !!walletShort}
                  style={{ marginTop: space.md }}
                  testID="book-ride"
                />
                {discount > 0 ? (
                  <T v="small" style={{ textAlign: 'center', marginTop: space.xs, color: colors.brandDark }}>
                    You save {fmt.rupees(discount)} with {option?.fare.promoCode}
                  </T>
                ) : null}
              </>
            )}
          </Sheet>
        </View>
      </View>
    </View>
  );
}

function OptionRow({ option, selected, durationS, onPress }: { option: QuoteOption; selected: boolean; durationS: number; onPress: () => void }) {
  const unavailable = option.etaS == null;
  const dropBy = option.etaS != null ? fmt.timeIn(option.etaS + durationS) : null;
  return (
    <Pressable onPress={onPress} accessibilityRole="radio" accessibilityState={{ selected }} testID={`option-${option.vehicleType}`}>
      <View
        style={{
          flexDirection: 'row',
          alignItems: 'center',
          gap: space.md,
          padding: space.md,
          borderRadius: 14,
          borderWidth: 2,
          borderColor: selected ? colors.ink : 'transparent',
          backgroundColor: selected ? colors.white : 'transparent',
          opacity: unavailable ? 0.55 : 1,
          marginBottom: 2,
        }}
      >
        <VehicleIcon type={option.vehicleType} size={48} />
        <View style={{ flex: 1 }}>
          <Row gap={6}>
            <T v="h2">{option.name}</T>
            <Ionicons name="person" size={12} color={colors.ink3} />
            <T v="small">{option.capacity}</T>
          </Row>
          <T v="small" numberOfLines={1}>
            {unavailable ? 'No cars nearby right now' : `${fmt.minutes(option.etaS)} away · drop ${dropBy}`}
          </T>
        </View>
        <View style={{ alignItems: 'flex-end' }}>
          <T v="h2">{fmt.rupees(option.fare.total)}</T>
          {option.fare.discount > 0 ? (
            <T v="small" style={{ textDecorationLine: 'line-through' }}>
              {fmt.rupees(option.fare.total + option.fare.discount * (1 + option.fare.taxPct / 100))}
            </T>
          ) : null}
        </View>
      </View>
    </Pressable>
  );
}
