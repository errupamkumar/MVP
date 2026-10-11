import { colors, Ionicons } from '@ecoride/mobile';
import { Text, View } from 'react-native';

/** The EcoRide wordmark: a leaf in a green tile, then the name. */
export function Brand({ size = 28, dark = false, suffix }: { size?: number; dark?: boolean; suffix?: string }) {
  return (
    <View style={{ flexDirection: 'row', alignItems: 'center', gap: size * 0.3 }}>
      <View
        style={{
          width: size * 1.25,
          height: size * 1.25,
          borderRadius: size * 0.35,
          backgroundColor: colors.brand,
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        <Ionicons name="leaf" size={size * 0.75} color={colors.white} />
      </View>
      <Text style={{ fontSize: size, fontWeight: '800', color: dark ? colors.white : colors.ink, letterSpacing: -0.5 }}>
        EcoRide
        {suffix ? <Text style={{ fontWeight: '500', color: dark ? '#9CA3AF' : colors.ink3 }}>{` ${suffix}`}</Text> : null}
      </Text>
    </View>
  );
}
