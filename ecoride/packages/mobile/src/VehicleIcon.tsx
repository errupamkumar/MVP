import { MaterialCommunityIcons } from '@expo/vector-icons';
import { View } from 'react-native';

import { colors } from './theme';

const ICON: Record<string, keyof typeof MaterialCommunityIcons.glyphMap> = {
  BIKE: 'motorbike',
  AUTO: 'rickshaw',
  MINI: 'car-hatchback',
  SEDAN: 'car-side',
  SUV: 'car-estate',
};

/** The product's picture on ride lists: bike, auto rickshaw, hatchback, sedan, SUV. */
export function VehicleIcon({ type, size = 44, tint = colors.ink }: { type: string; size?: number; tint?: string }) {
  return (
    <View style={{ width: size, height: size, borderRadius: size / 2, backgroundColor: colors.mist, alignItems: 'center', justifyContent: 'center' }}>
      <MaterialCommunityIcons name={ICON[type] ?? 'car'} size={size * 0.6} color={tint} />
    </View>
  );
}
