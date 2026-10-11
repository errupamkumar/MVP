import type { LatLng } from '@ecoride/core';
import type { StyleProp, ViewStyle } from 'react-native';

export type MarkerKind = 'pickup' | 'drop' | 'car' | 'me' | 'rider';

export interface MapMarker {
  id: string;
  kind: MarkerKind;
  lat: number;
  lng: number;
  /** Degrees from north; cars are drawn pointing this way. */
  heading?: number | null;
  vehicleType?: string | null;
  /** A small pill above the marker ("4 min", "Pickup"). */
  label?: string | null;
  /** Draw larger and on top (the rider's own cab among others). */
  highlight?: boolean;
}

export interface MapRoute {
  id: string;
  points: LatLng[];
  color?: string;
  width?: number;
  dashed?: boolean;
}

export interface MapCircle {
  id: string;
  lat: number;
  lng: number;
  radiusM: number;
  color: string;
}

export interface MapPadding {
  top: number;
  right: number;
  bottom: number;
  left: number;
}

export interface RideMapProps {
  initialCenter: LatLng;
  initialZoom?: number;
  markers?: MapMarker[];
  routes?: MapRoute[];
  circles?: MapCircle[];
  /** Fit these points into view whenever `key` changes. */
  fit?: { key: string; points: LatLng[] } | null;
  /** Space covered by overlays (the bottom sheet), kept clear when fitting. */
  padding?: MapPadding;
  /** Keep this point centred as it moves (driver navigation). */
  follow?: LatLng | null;
  onPress?: (point: LatLng) => void;
  style?: StyleProp<ViewStyle>;
}

export const CAR_COLORS: Record<string, string> = {
  BIKE: '#111827',
  AUTO: '#0A8F5B',
  MINI: '#1F2937',
  SEDAN: '#111827',
  SUV: '#0F172A',
};
