import type { LatLng } from './types';

const EARTH_RADIUS_M = 6_371_000;
const toRad = (deg: number) => (deg * Math.PI) / 180;
const toDeg = (rad: number) => (rad * 180) / Math.PI;

/** Google encoded polyline, precision 5 (what OSRM and the EcoRide API send). */
export function decodePolyline(encoded: string | null | undefined): LatLng[] {
  const points: LatLng[] = [];
  if (!encoded) return points;
  let index = 0;
  let lat = 0;
  let lng = 0;
  while (index < encoded.length) {
    let b: number;
    let shift = 0;
    let result = 0;
    do {
      b = encoded.charCodeAt(index++) - 63;
      result |= (b & 0x1f) << shift;
      shift += 5;
    } while (b >= 0x20 && index < encoded.length);
    lat += result & 1 ? ~(result >> 1) : result >> 1;
    if (index >= encoded.length) break;
    shift = 0;
    result = 0;
    do {
      b = encoded.charCodeAt(index++) - 63;
      result |= (b & 0x1f) << shift;
      shift += 5;
    } while (b >= 0x20 && index < encoded.length);
    lng += result & 1 ? ~(result >> 1) : result >> 1;
    points.push({ lat: lat / 1e5, lng: lng / 1e5 });
  }
  return points;
}

/** Great-circle distance in metres. */
export function distanceM(a: LatLng, b: LatLng): number {
  const dLat = toRad(b.lat - a.lat);
  const dLng = toRad(b.lng - a.lng);
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(toRad(a.lat)) * Math.cos(toRad(b.lat)) * Math.sin(dLng / 2) ** 2;
  return 2 * EARTH_RADIUS_M * Math.asin(Math.min(1, Math.sqrt(h)));
}

/** Degrees clockwise from north, 0-359. */
export function bearing(a: LatLng, b: LatLng): number {
  const phi1 = toRad(a.lat);
  const phi2 = toRad(b.lat);
  const dLng = toRad(b.lng - a.lng);
  const y = Math.sin(dLng) * Math.cos(phi2);
  const x = Math.cos(phi1) * Math.sin(phi2) - Math.sin(phi1) * Math.cos(phi2) * Math.cos(dLng);
  return (Math.round(toDeg(Math.atan2(y, x))) + 360) % 360;
}

export function interpolate(a: LatLng, b: LatLng, t: number): LatLng {
  return { lat: a.lat + (b.lat - a.lat) * t, lng: a.lng + (b.lng - a.lng) * t };
}

/**
 * A route you can walk along: the point and heading at any distance from its start.
 * The driver app's GPS simulator drives with it.
 */
export class RoutePath {
  readonly points: LatLng[];
  private readonly cumulative: number[];

  constructor(points: LatLng[]) {
    this.points = points.length ? points : [{ lat: 0, lng: 0 }];
    this.cumulative = [0];
    for (let i = 1; i < this.points.length; i++) {
      this.cumulative.push(this.cumulative[i - 1] + distanceM(this.points[i - 1], this.points[i]));
    }
  }

  get lengthM(): number {
    return this.cumulative[this.cumulative.length - 1];
  }

  pointAt(alongM: number): LatLng {
    if (alongM <= 0) return this.points[0];
    if (alongM >= this.lengthM) return this.points[this.points.length - 1];
    let lo = 0;
    let hi = this.cumulative.length - 1;
    while (hi - lo > 1) {
      const mid = (lo + hi) >> 1;
      if (this.cumulative[mid] <= alongM) lo = mid;
      else hi = mid;
    }
    const seg = this.cumulative[hi] - this.cumulative[lo];
    return interpolate(this.points[lo], this.points[hi], seg <= 0 ? 0 : (alongM - this.cumulative[lo]) / seg);
  }

  headingAt(alongM: number): number {
    return bearing(this.pointAt(Math.max(0, alongM - 5)), this.pointAt(Math.min(this.lengthM, alongM + 15)));
  }

  /** Distance along the route of the point nearest to `p` (to resume a drive part-way). */
  locate(p: LatLng): number {
    let best = Number.POSITIVE_INFINITY;
    let along = 0;
    for (let i = 0; i < this.points.length - 1; i++) {
      const a = this.points[i];
      const b = this.points[i + 1];
      const cos = Math.cos(toRad(a.lat));
      const ax = a.lng * cos;
      const bx = b.lng * cos;
      const px = p.lng * cos;
      const dx = bx - ax;
      const dy = b.lat - a.lat;
      const len2 = dx * dx + dy * dy;
      const t = len2 === 0 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (p.lat - a.lat) * dy) / len2));
      const d = distanceM(interpolate(a, b, t), p);
      if (d < best) {
        best = d;
        along = this.cumulative[i] + (this.cumulative[i + 1] - this.cumulative[i]) * t;
      }
    }
    return along;
  }
}

/** South-west and north-east corners around a set of points. */
export function boundsOf(points: LatLng[]): [LatLng, LatLng] | null {
  if (!points.length) return null;
  let minLat = points[0].lat;
  let maxLat = points[0].lat;
  let minLng = points[0].lng;
  let maxLng = points[0].lng;
  for (const p of points) {
    minLat = Math.min(minLat, p.lat);
    maxLat = Math.max(maxLat, p.lat);
    minLng = Math.min(minLng, p.lng);
    maxLng = Math.max(maxLng, p.lng);
  }
  return [
    { lat: minLat, lng: minLng },
    { lat: maxLat, lng: maxLng },
  ];
}
