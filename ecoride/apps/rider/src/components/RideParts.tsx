import { fmt, type DriverCard as Driver } from '@ecoride/core';
import { Avatar, colors, Ionicons, Row, space, T } from '@ecoride/mobile';
import { useEffect, useRef } from 'react';
import { Animated, Easing, Text, View } from 'react-native';

/** The 4-digit code the rider reads out to start the trip. */
export function PinBox({ pin }: { pin: string }) {
  return (
    <View style={{ alignItems: 'center' }}>
      <T v="small" style={{ fontWeight: '700', marginBottom: 2 }}>
        PIN
      </T>
      <View style={{ flexDirection: 'row', gap: 4 }} testID="ride-pin">
        {pin.split('').map((d, i) => (
          <View key={i} style={{ width: 26, height: 34, borderRadius: 6, backgroundColor: colors.ink, alignItems: 'center', justifyContent: 'center' }}>
            <Text style={{ color: colors.white, fontSize: 19, fontWeight: '800' }}>{d}</Text>
          </View>
        ))}
      </View>
    </View>
  );
}

export function ProgressBar({ value, color = colors.brand }: { value: number; color?: string }) {
  return (
    <View style={{ height: 6, borderRadius: 3, backgroundColor: colors.mist, overflow: 'hidden', marginVertical: space.md }}>
      <View style={{ width: `${Math.round(Math.max(0.03, Math.min(1, value)) * 100)}%`, height: 6, borderRadius: 3, backgroundColor: color }} />
    </View>
  );
}

/** An indeterminate bar while drivers are being contacted. */
export function SearchingBar() {
  const x = useRef(new Animated.Value(0)).current;
  useEffect(() => {
    const loop = Animated.loop(
      Animated.timing(x, { toValue: 1, duration: 1400, easing: Easing.inOut(Easing.quad), useNativeDriver: false }),
    );
    loop.start();
    return () => loop.stop();
  }, [x]);
  return (
    <View style={{ height: 6, borderRadius: 3, backgroundColor: colors.mist, overflow: 'hidden', marginVertical: space.md }}>
      <Animated.View
        style={{
          position: 'absolute',
          height: 6,
          width: '35%',
          borderRadius: 3,
          backgroundColor: colors.brand,
          left: x.interpolate({ inputRange: [0, 1], outputRange: ['-35%', '100%'] }),
        }}
      />
    </View>
  );
}

export function DriverRow({ driver }: { driver: Driver }) {
  const v = driver.vehicle;
  return (
    <Row gap={space.md} style={{ marginTop: space.sm }}>
      <Avatar name={driver.name} size={50} />
      <View style={{ flex: 1 }}>
        <T v="h2" numberOfLines={1}>
          {driver.name}
        </T>
        <Row gap={4}>
          <Ionicons name="star" size={13} color={colors.gold} />
          <T v="small" style={{ color: colors.ink2, fontWeight: '600' }}>
            {fmt.rating(driver.rating)}
          </T>
          <T v="small">· {fmt.count(driver.trips, 'trip')}</T>
        </Row>
      </View>
      <View style={{ alignItems: 'flex-end' }}>
        <View style={{ borderWidth: 1.5, borderColor: colors.ink, borderRadius: 6, paddingHorizontal: 8, paddingVertical: 3, backgroundColor: '#FFFBEA' }}>
          <Text style={{ fontWeight: '800', fontSize: 15, color: colors.ink, letterSpacing: 0.5 }}>{plate(v.plate)}</Text>
        </View>
        <T v="small" style={{ marginTop: 3 }}>
          {v.color} {v.make} {v.model}
        </T>
      </View>
    </Row>
  );
}

/** MH12AB4521 -> MH 12 AB 4521 */
export function plate(value: string): string {
  const m = value.match(/^([A-Z]{2})(\d{1,2})([A-Z]{0,3})(\d{4})$/);
  return m ? [m[1], m[2], m[3], m[4]].filter(Boolean).join(' ') : value;
}

export function Stat({ label, value }: { label: string; value: string }) {
  return (
    <View style={{ flex: 1, backgroundColor: colors.mist, borderRadius: 12, padding: space.md }}>
      <T v="small">{label}</T>
      <T v="h2" style={{ marginTop: 2 }}>
        {value}
      </T>
    </View>
  );
}
