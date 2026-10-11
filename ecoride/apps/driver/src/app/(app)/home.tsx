import { decodePolyline, fmt, toApiError, type LatLng } from '@ecoride/core';
import {
  colors,
  IconButton,
  Ionicons,
  Loading,
  MapMarker,
  MapRoute,
  RideMap,
  Row,
  Sheet,
  shadow,
  showMessage,
  space,
  T,
  useNow,
  useReceivedAt,
} from '@ecoride/mobile';
import { useQueryClient } from '@tanstack/react-query';
import { router } from 'expo-router';
import { useEffect, useMemo, useRef, useState } from 'react';
import { Platform, Pressable, Vibration, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { IdlePanel, LastTripPanel, OfferCard, RidePanel } from '@/components/Panels';
import { api, API_URL } from '@/lib/api';
import { DEMO_START, useDriverGps } from '@/lib/gps';
import { keys, useDriverState } from '@/lib/queries';

export default function DriverHome() {
  const insets = useSafeAreaInsets();
  const queryClient = useQueryClient();
  const { data: state, error } = useDriverState();
  const { position, mode } = useDriverGps(state);
  const receivedAt = useReceivedAt(state);
  const now = useNow(250);
  const elapsed = (now - receivedAt) / 1000;
  const [busy, setBusy] = useState(false);
  const refresh = () => void queryClient.invalidateQueries({ queryKey: keys.state });

  const offer = state?.offer ?? null;
  const ride = state?.ride ?? null;
  const lastTrip = state?.lastTrip ?? null;
  const offerSecondsLeft = offer ? Math.max(0, offer.expiresInS - elapsed) : 0;

  // A new request: buzz the phone.
  const lastOffer = useRef<number | null>(null);
  useEffect(() => {
    if (offer && offer.offerId !== lastOffer.current) {
      lastOffer.current = offer.offerId;
      if (Platform.OS !== 'web') Vibration.vibrate([0, 400, 200, 400]);
    }
  }, [offer]);

  const pickupRoute = useMemo(() => decodePolyline(ride?.pickupPolyline), [ride?.pickupPolyline]);
  const tripRoute = useMemo(() => decodePolyline(ride?.routePolyline ?? offer?.routePolyline), [ride?.routePolyline, offer?.routePolyline]);

  if (!state) {
    return error ? (
      <View style={{ flex: 1, padding: space.xl, paddingTop: insets.top + space.xl }}>
        <T v="h2">Cannot reach EcoRide</T>
        <T v="muted">{toApiError(error, API_URL).message}</T>
      </View>
    ) : (
      <Loading label="Starting…" />
    );
  }

  const me = position ?? state.position ?? DEMO_START;
  const vehicleType = state.profile.vehicle.type;
  const markers: MapMarker[] = [];
  const routes: MapRoute[] = [];
  let fitPoints: LatLng[] = [me];
  let fitKey = `idle-${state.profile.availability}`;
  let follow: LatLng | null = null;

  if (ride) {
    markers.push({ id: 'pickup', kind: 'pickup', lat: ride.pickup.lat, lng: ride.pickup.lng, label: ride.status === 'ACCEPTED' ? 'Pickup' : null });
    markers.push({ id: 'drop', kind: 'drop', lat: ride.drop.lat, lng: ride.drop.lng });
    if (ride.status === 'ACCEPTED') {
      routes.push({ id: 'to-pickup', points: [me, ...pickupRoute.slice(1), ride.pickup], color: colors.pickupRoute, width: 6 });
      fitPoints = [me, ride.pickup];
      follow = me;
    } else {
      routes.push({ id: 'trip', points: tripRoute, color: colors.route, width: 6 });
      fitPoints = ride.status === 'IN_PROGRESS' ? [me, ride.drop] : [me, ride.pickup];
      follow = ride.status === 'IN_PROGRESS' ? me : null;
    }
    if (ride.rider.position) {
      markers.push({ id: 'rider', kind: 'rider', lat: ride.rider.position.lat, lng: ride.rider.position.lng, label: ride.rider.name.split(' ')[0] });
    }
    fitKey = `${ride.id}-${ride.status}`;
  } else if (offer) {
    markers.push({ id: 'pickup', kind: 'pickup', lat: offer.pickup.lat, lng: offer.pickup.lng, label: fmt.minutes(offer.pickupEtaS) });
    markers.push({ id: 'drop', kind: 'drop', lat: offer.drop.lat, lng: offer.drop.lng });
    routes.push({ id: 'offer', points: tripRoute, color: '#6B7280', width: 4 });
    routes.push({ id: 'offer-pickup', points: [me, offer.pickup], color: colors.pickupRoute, width: 3, dashed: true });
    fitPoints = [me, offer.pickup, offer.drop];
    fitKey = `offer-${offer.offerId}`;
  }
  markers.push({ id: 'me', kind: 'car', lat: me.lat, lng: me.lng, heading: position?.heading ?? null, vehicleType, highlight: true });

  const online = state.profile.availability !== 'OFFLINE';
  const goOnline = async () => {
    setBusy(true);
    try {
      queryClient.setQueryData(keys.state, await api.online(me));
    } catch (e) {
      showMessage('Cannot go online', toApiError(e, API_URL).message);
    } finally {
      setBusy(false);
    }
  };
  const goOffline = async () => {
    setBusy(true);
    try {
      queryClient.setQueryData(keys.state, await api.offline());
    } catch (e) {
      showMessage('Cannot go offline', toApiError(e, API_URL).message);
    } finally {
      setBusy(false);
    }
  };
  const accept = async () => {
    if (!offer) return;
    setBusy(true);
    try {
      await api.accept(offer.offerId);
    } catch (e) {
      showMessage('Request gone', toApiError(e, API_URL).message);
    } finally {
      setBusy(false);
      refresh();
    }
  };
  const decline = async () => {
    if (!offer) return;
    await api.decline(offer.offerId).catch(() => undefined);
    refresh();
  };

  return (
    <View style={{ flex: 1 }}>
      <RideMap
        initialCenter={me}
        initialZoom={15}
        markers={markers}
        routes={routes}
        fit={{ key: fitKey, points: fitPoints }}
        follow={follow}
        padding={{ top: insets.top + 90, right: 40, bottom: offer && !ride ? 520 : 380, left: 40 }}
      />

      <View style={{ position: 'absolute', top: insets.top + space.md, left: space.lg, right: space.lg }}>
        <Row style={{ justifyContent: 'space-between' }}>
          <IconButton icon="person-circle-outline" label="Profile" onPress={() => router.push('/profile')} size={44} />
          <View style={[{ backgroundColor: online ? colors.brand : colors.ink, borderRadius: 999, paddingHorizontal: 14, height: 36, flexDirection: 'row', alignItems: 'center', gap: 6 }, shadow(1)]}>
            <View style={{ width: 8, height: 8, borderRadius: 4, backgroundColor: online ? '#B7F5D4' : '#9CA3AF' }} />
            <T v="bodyStrong" style={{ color: colors.white, fontSize: 14 }}>
              {state.profile.availability === 'ON_TRIP' ? 'On a trip' : online ? 'Online' : 'Offline'}
            </T>
          </View>
          <Pressable onPress={() => router.push('/earnings')} accessibilityRole="button" accessibilityLabel="Earnings">
            <View style={[{ backgroundColor: colors.white, borderRadius: 999, paddingHorizontal: 14, height: 44, flexDirection: 'row', alignItems: 'center', gap: 6 }, shadow(1)]}>
              <Ionicons name="wallet-outline" size={17} color={colors.ink} />
              <T v="bodyStrong">{fmt.rupees(state.today.earnings)}</T>
            </View>
          </Pressable>
        </Row>
      </View>

      <View style={{ position: 'absolute', left: 0, right: 0, bottom: 0 }}>
        <View style={{ maxWidth: 560, width: '100%', alignSelf: 'center' }}>
          {offer && !ride ? (
            <View style={{ padding: space.md, paddingBottom: insets.bottom + space.md }}>
              <OfferCard offer={offer} secondsLeft={offerSecondsLeft} onAccept={accept} onDecline={decline} busy={busy} />
            </View>
          ) : (
            <Sheet>
              {ride ? (
                <RidePanel key={`${ride.id}-${ride.status}`} ride={ride} elapsed={elapsed} refresh={refresh} />
              ) : lastTrip ? (
                <LastTripPanel key={lastTrip.id} ride={lastTrip} refresh={refresh} />
              ) : (
                <IdlePanel state={state} gpsMode={mode} onGoOnline={goOnline} onGoOffline={goOffline} busy={busy} refresh={refresh} />
              )}
            </Sheet>
          )}
        </View>
      </View>
    </View>
  );
}
