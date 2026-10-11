import 'leaflet/dist/leaflet.css';

import type { LatLng } from '@ecoride/core';
import L from 'leaflet';
import { ReactNode, useEffect } from 'react';
import { MapContainer, TileLayer, useMap, useMapEvents } from 'react-leaflet';

export const PUNE: LatLng = { lat: 18.555, lng: 73.85 };

export const AVAILABILITY_COLOR = {
  ONLINE: '#0A8F5B',
  ON_TRIP: '#2a78d6',
  OFFER: '#d97706',
} as const;

const iconCache = new Map<string, L.DivIcon>();

/** A top-down car pointing along its heading, coloured by state, with an optional name pill. */
export function carIcon(color: string, heading: number | null, label?: string | null, big = false): L.DivIcon {
  const h = Math.round((heading ?? 0) / 15) * 15;
  const key = `${color}|${h}|${label ?? ''}|${big}`;
  const cached = iconCache.get(key);
  if (cached) return cached;
  const w = big ? 18 : 14;
  const len = big ? 30 : 24;
  const size = len + 8;
  const pill = label
    ? `<div style="position:absolute;left:50%;bottom:100%;transform:translate(-50%,-2px);white-space:nowrap;background:#111827;color:#fff;font:600 11px/1 Inter,system-ui,sans-serif;padding:4px 6px;border-radius:5px;box-shadow:0 1px 4px rgba(0,0,0,.25)">${label}</div>`
    : '';
  const icon = L.divIcon({
    className: 'eco-marker eco-glide',
    iconSize: [size, size],
    iconAnchor: [size / 2, size / 2],
    html: `<div style="position:relative;width:${size}px;height:${size}px;display:flex;align-items:center;justify-content:center">${pill}<svg width="${w}" height="${len}" viewBox="0 0 ${w} ${len}" style="transform:rotate(${h}deg)"><rect x="1" y="1" width="${w - 2}" height="${len - 2}" rx="${w / 3}" fill="${color}" stroke="#fff" stroke-width="1.5"/><rect x="3" y="${len * 0.22}" width="${w - 6}" height="${len * 0.18}" rx="1.5" fill="#fff" opacity=".85"/></svg></div>`,
  });
  iconCache.set(key, icon);
  return icon;
}

export function pointIcon(kind: 'pickup' | 'drop', label?: string): L.DivIcon {
  const key = `${kind}|${label ?? ''}`;
  const cached = iconCache.get(key);
  if (cached) return cached;
  const shape =
    kind === 'pickup'
      ? '<div style="width:14px;height:14px;border-radius:50%;background:#0A8F5B;border:3px solid #fff;box-shadow:0 1px 4px rgba(0,0,0,.35)"></div>'
      : '<div style="width:13px;height:13px;background:#111827;border:3px solid #fff;box-shadow:0 1px 4px rgba(0,0,0,.35)"></div>';
  const pill = label
    ? `<div style="position:absolute;left:50%;bottom:100%;transform:translate(-50%,-2px);white-space:nowrap;background:#fff;color:#111827;font:600 11px/1 Inter,system-ui,sans-serif;padding:4px 6px;border-radius:5px;box-shadow:0 1px 4px rgba(0,0,0,.25)">${label}</div>`
    : '';
  const icon = L.divIcon({ className: 'eco-marker', iconSize: [20, 20], iconAnchor: [10, 10], html: `<div style="position:relative;display:flex;justify-content:center">${pill}${shape}</div>` });
  iconCache.set(key, icon);
  return icon;
}

function Behaviour({ fit, fitKey }: { fit?: LatLng[]; fitKey?: string }) {
  const map = useMap();
  useMapEvents({
    zoomstart: () => map.getContainer().classList.add('eco-zooming'),
    zoomend: () => setTimeout(() => map.getContainer().classList.remove('eco-zooming'), 60),
  });
  useEffect(() => {
    if (!fit || fit.length === 0) return;
    if (fit.length === 1) map.setView([fit[0].lat, fit[0].lng], 15);
    else map.fitBounds(L.latLngBounds(fit.map((p) => [p.lat, p.lng] as [number, number])), { padding: [40, 40], maxZoom: 16 });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [fitKey, map]);
  return null;
}

export function MapCanvas({ children, height = 520, center = PUNE, zoom = 12, fit, fitKey }: { children?: ReactNode; height?: number | string; center?: LatLng; zoom?: number; fit?: LatLng[]; fitKey?: string }) {
  return (
    <div style={{ height }} className="overflow-hidden rounded-xl">
      <MapContainer center={[center.lat, center.lng]} zoom={zoom} style={{ height: '100%', width: '100%' }} zoomControl>
        <TileLayer url="https://tile.openstreetmap.org/{z}/{x}/{y}.png" attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors' maxZoom={19} />
        {children}
        <Behaviour fit={fit} fitKey={fitKey} />
      </MapContainer>
    </div>
  );
}
