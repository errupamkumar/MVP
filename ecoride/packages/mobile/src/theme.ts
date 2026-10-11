import { Platform, ViewStyle } from 'react-native';

/** EcoRide's palette: near-black ink on white (the ride-hailing look) with an eco green brand colour. */
export const colors = {
  ink: '#111827',
  ink2: '#374151',
  ink3: '#6B7280',
  ink4: '#9CA3AF',
  line: '#E5E7EB',
  mist: '#F3F4F6',
  canvas: '#F7F8FA',
  white: '#FFFFFF',
  brand: '#0A8F5B',
  brandDark: '#066B43',
  brandSoft: '#E3F5EC',
  info: '#1D5FD6',
  infoSoft: '#E8F0FE',
  warn: '#B45309',
  warnSoft: '#FEF3C7',
  danger: '#D92D20',
  dangerSoft: '#FDECEC',
  gold: '#F59E0B',
  route: '#111827',
  pickupRoute: '#0A8F5B',
  black: '#000000',
} as const;

export const radius = { sm: 8, md: 12, lg: 16, xl: 24, pill: 999 } as const;

export const space = { xs: 4, sm: 8, md: 12, lg: 16, xl: 24, xxl: 32 } as const;

export const font = {
  regular: '400' as const,
  medium: '500' as const,
  semibold: '600' as const,
  bold: '700' as const,
  heavy: '800' as const,
  mono: Platform.select({ ios: 'Menlo', android: 'monospace', default: 'ui-monospace, Menlo, Consolas, monospace' }),
};

export function shadow(level: 1 | 2 | 3 = 1): ViewStyle {
  const radiusByLevel = { 1: 6, 2: 12, 3: 20 }[level];
  const opacity = { 1: 0.08, 2: 0.12, 3: 0.18 }[level];
  return Platform.select<ViewStyle>({
    web: { boxShadow: `0 ${level * 2}px ${radiusByLevel}px rgba(17,24,39,${opacity})` } as ViewStyle,
    default: {
      shadowColor: '#000',
      shadowOpacity: opacity,
      shadowRadius: radiusByLevel,
      shadowOffset: { width: 0, height: level * 2 },
      elevation: level * 4,
    },
  }) as ViewStyle;
}
