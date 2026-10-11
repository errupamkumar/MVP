import type { PlaceResult } from '@ecoride/core';

/** "Phoenix Marketcity, Viman Nagar Road, Viman Nagar" from a search or reverse-geocode result. */
export function placeLabel(p: Pick<PlaceResult, 'name' | 'address'>): string {
  if (!p.address || p.name === p.address) return p.name;
  // Reverse geocoding often returns the street as both name and the start of the address.
  return p.address.toLowerCase().startsWith(p.name.toLowerCase()) ? p.address : `${p.name}, ${p.address}`;
}
