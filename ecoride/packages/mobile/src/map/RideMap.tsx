import { useEffect, useRef } from 'react';
import { Text, View } from 'react-native';
import MapView, { Circle, Marker, Polyline } from 'react-native-maps';

import { colors } from '../theme';
import { CAR_COLORS, MapMarker, RideMapProps } from './mapTypes';

/**
 * Phone map: react-native-maps (Google Maps on Android, Apple Maps on iOS; both ship in
 * Expo Go). The web build uses RideMap.web.tsx (Leaflet) with the same props.
 */
const zoomToDelta = (zoom: number) => 360 / Math.pow(2, zoom);

function Pill({ text }: { text: string }) {
  return (
    <View style={{ backgroundColor: colors.ink, borderRadius: 6, paddingHorizontal: 7, paddingVertical: 4, marginBottom: 3 }}>
      <Text style={{ color: colors.white, fontSize: 11, fontWeight: '700' }}>{text}</Text>
    </View>
  );
}

function MarkerBody({ m }: { m: MapMarker }) {
  switch (m.kind) {
    case 'car': {
      const color = CAR_COLORS[m.vehicleType ?? 'MINI'] ?? colors.ink;
      const scale = m.highlight ? 1.15 : 1;
      if (m.vehicleType === 'BIKE') {
        return <View style={{ width: 8 * scale, height: 26 * scale, borderRadius: 4, backgroundColor: color, borderWidth: 1.5, borderColor: colors.white }} />;
      }
      return (
        <View style={{ width: 20 * scale, height: 36 * scale, borderRadius: 7, backgroundColor: m.vehicleType === 'AUTO' ? colors.brand : color, borderWidth: 1.5, borderColor: colors.white, alignItems: 'center', paddingTop: 6 * scale }}>
          <View style={{ width: 13 * scale, height: 7 * scale, borderRadius: 2, backgroundColor: m.vehicleType === 'AUTO' ? '#FACC15' : '#CFE3FF' }} />
        </View>
      );
    }
    case 'pickup':
      return (
        <View style={{ width: 22, height: 22, borderRadius: 11, backgroundColor: colors.brand, borderWidth: 3, borderColor: colors.white, alignItems: 'center', justifyContent: 'center' }}>
          <View style={{ width: 6, height: 6, borderRadius: 3, backgroundColor: colors.white }} />
        </View>
      );
    case 'drop':
      return (
        <View style={{ width: 20, height: 20, backgroundColor: colors.ink, borderWidth: 3, borderColor: colors.white, alignItems: 'center', justifyContent: 'center' }}>
          <View style={{ width: 6, height: 6, backgroundColor: colors.white }} />
        </View>
      );
    case 'rider':
      return (
        <View style={{ width: 26, height: 26, borderRadius: 13, backgroundColor: colors.info, borderWidth: 3, borderColor: colors.white, alignItems: 'center', justifyContent: 'center' }}>
          <Text style={{ color: colors.white, fontWeight: '700', fontSize: 11 }}>R</Text>
        </View>
      );
    case 'me':
    default:
      return (
        <View style={{ width: 36, height: 36, borderRadius: 18, backgroundColor: 'rgba(29,95,214,0.16)', alignItems: 'center', justifyContent: 'center' }}>
          <View style={{ width: 16, height: 16, borderRadius: 8, backgroundColor: colors.info, borderWidth: 3, borderColor: colors.white }} />
        </View>
      );
  }
}

export function RideMap({
  initialCenter,
  initialZoom = 14,
  markers = [],
  routes = [],
  circles = [],
  fit,
  padding,
  follow,
  onPress,
  style,
}: RideMapProps) {
  const ref = useRef<MapView>(null);
  const fitKey = fit?.key;
  const edgePadding = padding ?? { top: 40, right: 40, bottom: 40, left: 40 };

  useEffect(() => {
    if (!fit || !fit.points.length || !ref.current) return;
    if (fit.points.length === 1) {
      ref.current.animateCamera({ center: { latitude: fit.points[0].lat, longitude: fit.points[0].lng }, zoom: 15 }, { duration: 500 });
      return;
    }
    ref.current.fitToCoordinates(
      fit.points.map((p) => ({ latitude: p.lat, longitude: p.lng })),
      { edgePadding, animated: true },
    );
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [fitKey]);

  useEffect(() => {
    if (!follow || !ref.current) return;
    ref.current.animateCamera({ center: { latitude: follow.lat, longitude: follow.lng } }, { duration: 1500 });
  }, [follow]);

  const delta = zoomToDelta(initialZoom);
  return (
    <MapView
      ref={ref}
      style={[{ flex: 1 }, style]}
      initialRegion={{ latitude: initialCenter.lat, longitude: initialCenter.lng, latitudeDelta: delta, longitudeDelta: delta }}
      mapPadding={edgePadding}
      showsPointsOfInterests={false}
      showsCompass={false}
      toolbarEnabled={false}
      onPress={(e) => onPress?.({ lat: e.nativeEvent.coordinate.latitude, lng: e.nativeEvent.coordinate.longitude })}
    >
      {circles.map((c) => (
        <Circle
          key={c.id}
          center={{ latitude: c.lat, longitude: c.lng }}
          radius={c.radiusM}
          strokeColor={c.color}
          fillColor={`${c.color}22`}
          strokeWidth={1.5}
        />
      ))}
      {routes.map((r) => (
        <Polyline
          key={r.id}
          coordinates={r.points.map((p) => ({ latitude: p.lat, longitude: p.lng }))}
          strokeColor={r.color ?? colors.route}
          strokeWidth={r.width ?? 5}
          lineDashPattern={r.dashed ? [2, 8] : undefined}
          lineCap="round"
        />
      ))}
      {markers.map((m) => (
        <Marker
          key={m.id}
          coordinate={{ latitude: m.lat, longitude: m.lng }}
          anchor={{ x: 0.5, y: m.label ? 0.8 : 0.5 }}
          flat={m.kind === 'car'}
          rotation={m.kind === 'car' ? m.heading ?? 0 : 0}
          zIndex={m.highlight ? 10 : m.kind === 'car' ? 1 : 5}
        >
          <View style={{ alignItems: 'center' }}>
            {m.label ? <Pill text={m.label} /> : null}
            <MarkerBody m={m} />
          </View>
        </Marker>
      ))}
    </MapView>
  );
}
