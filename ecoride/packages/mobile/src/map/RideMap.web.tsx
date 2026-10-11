import 'leaflet/dist/leaflet.css';

import L from 'leaflet';
import { useEffect, useMemo, useRef } from 'react';
import { Circle, MapContainer, Marker, Polyline, TileLayer, useMap, useMapEvents } from 'react-leaflet';
import { View } from 'react-native';

import { CAR_COLORS, MapMarker, RideMapProps } from './mapTypes';

/**
 * Web map: Leaflet with OpenStreetMap tiles, toned down for a calm base map. Cars glide between GPS
 * fixes with a CSS transition, paused while the map zooms so markers do not drift.
 */
const STYLE_ID = 'ecoride-map-style';
const CSS = `
.eco-marker { background: transparent; border: none; }
.eco-car { transition: transform 1.9s linear; }
.eco-zooming .eco-car { transition: none !important; }
.eco-pill { position: absolute; left: 50%; bottom: 100%; transform: translate(-50%, -4px); white-space: nowrap;
  background: #111827; color: #fff; font: 600 12px/1 system-ui, -apple-system, Segoe UI, Roboto, sans-serif;
  padding: 5px 8px; border-radius: 6px; box-shadow: 0 2px 6px rgba(0,0,0,.25); }
.leaflet-control-attribution { font-size: 9px; background: rgba(255,255,255,.75) !important; }
.leaflet-tile-pane { filter: saturate(0.45) brightness(1.04) contrast(0.97); }
.leaflet-container { background: #EEF0F3; font-family: system-ui, -apple-system, Segoe UI, Roboto, sans-serif; }
`;

function ensureStyle() {
  if (typeof document === 'undefined' || document.getElementById(STYLE_ID)) return;
  const el = document.createElement('style');
  el.id = STYLE_ID;
  el.textContent = CSS;
  document.head.appendChild(el);
}

function carSvg(type: string | null | undefined, highlight: boolean): string {
  const color = CAR_COLORS[type ?? 'MINI'] ?? '#111827';
  const scale = highlight ? 1.15 : 1;
  if (type === 'BIKE') {
    return `<svg width="${16 * scale}" height="${34 * scale}" viewBox="0 0 16 34"><rect x="5" y="1" width="6" height="32" rx="3" fill="${color}"/><rect x="2" y="9" width="12" height="3" rx="1.5" fill="${color}"/><circle cx="8" cy="18" r="4.5" fill="#F59E0B" stroke="#fff" stroke-width="1.5"/></svg>`;
  }
  if (type === 'AUTO') {
    return `<svg width="${24 * scale}" height="${34 * scale}" viewBox="0 0 24 34"><path d="M12 1 C20 1 23 6 23 12 L23 31 Q23 33 21 33 L3 33 Q1 33 1 31 L1 12 C1 6 4 1 12 1 Z" fill="${color}" stroke="#fff" stroke-width="1.5"/><rect x="4" y="12" width="16" height="15" rx="3" fill="#FACC15"/><rect x="6" y="5" width="12" height="5" rx="2" fill="#CFE3FF"/></svg>`;
  }
  const w = type === 'SUV' ? 26 : 24;
  return `<svg width="${w * scale}" height="${42 * scale}" viewBox="0 0 ${w} 42"><rect x="1" y="1" width="${w - 2}" height="40" rx="8" fill="${color}" stroke="#fff" stroke-width="1.5"/><rect x="4" y="9" width="${w - 8}" height="8" rx="2.5" fill="#CFE3FF"/><rect x="4" y="29" width="${w - 8}" height="6" rx="2" fill="#CFE3FF" opacity=".85"/><rect x="5" y="18" width="${w - 10}" height="10" rx="2" fill="${color}" opacity=".6"/></svg>`;
}

function iconFor(m: MapMarker): L.DivIcon {
  const pill = m.label ? `<div class="eco-pill">${escapeHtml(m.label)}</div>` : '';
  switch (m.kind) {
    case 'car': {
      const size = m.highlight ? 50 : 42;
      return L.divIcon({
        className: 'eco-marker eco-car',
        iconSize: [size, size],
        iconAnchor: [size / 2, size / 2],
        html: `<div style="position:relative;width:${size}px;height:${size}px;display:flex;align-items:center;justify-content:center">${pill}<div style="transform:rotate(${m.heading ?? 0}deg);transition:transform .6s ease;display:flex">${carSvg(m.vehicleType, !!m.highlight)}</div></div>`,
      });
    }
    case 'pickup':
      return L.divIcon({
        className: 'eco-marker',
        iconSize: [22, 22],
        iconAnchor: [11, 11],
        html: `<div style="position:relative">${pill}<div style="width:22px;height:22px;border-radius:50%;background:#0A8F5B;border:3px solid #fff;box-shadow:0 1px 5px rgba(0,0,0,.35);display:flex;align-items:center;justify-content:center"><div style="width:6px;height:6px;border-radius:50%;background:#fff"></div></div></div>`,
      });
    case 'drop':
      return L.divIcon({
        className: 'eco-marker',
        iconSize: [20, 20],
        iconAnchor: [10, 10],
        html: `<div style="position:relative">${pill}<div style="width:20px;height:20px;background:#111827;border:3px solid #fff;box-shadow:0 1px 5px rgba(0,0,0,.35);display:flex;align-items:center;justify-content:center"><div style="width:6px;height:6px;background:#fff"></div></div></div>`,
      });
    case 'rider':
      return L.divIcon({
        className: 'eco-marker eco-car',
        iconSize: [26, 26],
        iconAnchor: [13, 13],
        html: `<div style="position:relative">${pill}<div style="width:26px;height:26px;border-radius:50%;background:#1D5FD6;border:3px solid #fff;box-shadow:0 1px 5px rgba(0,0,0,.35);display:flex;align-items:center;justify-content:center;color:#fff;font:700 12px system-ui">R</div></div>`,
      });
    case 'me':
    default:
      return L.divIcon({
        className: 'eco-marker eco-car',
        iconSize: [40, 40],
        iconAnchor: [20, 20],
        html: `<div style="position:relative;width:40px;height:40px;display:flex;align-items:center;justify-content:center">${pill}<div style="position:absolute;width:40px;height:40px;border-radius:50%;background:rgba(29,95,214,.16)"></div><div style="width:16px;height:16px;border-radius:50%;background:#1D5FD6;border:3px solid #fff;box-shadow:0 1px 4px rgba(0,0,0,.35)"></div></div>`,
      });
  }
}

function escapeHtml(value: string): string {
  return value.replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c] ?? c);
}

function MapBehaviour({ fit, padding, follow, onPress }: Pick<RideMapProps, 'fit' | 'padding' | 'follow' | 'onPress'>) {
  const map = useMap();
  const fitKey = fit?.key;
  const lastFollow = useRef<string | null>(null);

  useMapEvents({
    click: (e) => onPress?.({ lat: e.latlng.lat, lng: e.latlng.lng }),
    zoomstart: () => map.getContainer().classList.add('eco-zooming'),
    zoomend: () => setTimeout(() => map.getContainer().classList.remove('eco-zooming'), 60),
  });

  useEffect(() => {
    // The container can be laid out after Leaflet measured it (React Native Web flex layout).
    const t = setTimeout(() => map.invalidateSize(), 120);
    return () => clearTimeout(t);
  }, [map]);

  useEffect(() => {
    if (!fit || !fit.points.length) return;
    const p = padding ?? { top: 40, right: 40, bottom: 40, left: 40 };
    if (fit.points.length === 1) {
      map.setView([fit.points[0].lat, fit.points[0].lng], Math.max(map.getZoom(), 15), { animate: true });
      return;
    }
    const bounds = L.latLngBounds(fit.points.map((pt) => [pt.lat, pt.lng] as [number, number]));
    map.fitBounds(bounds, {
      paddingTopLeft: [p.left, p.top],
      paddingBottomRight: [p.right, p.bottom],
      maxZoom: 16,
      animate: true,
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [fitKey, map]);

  useEffect(() => {
    if (!follow) return;
    const key = `${follow.lat.toFixed(5)},${follow.lng.toFixed(5)}`;
    if (key === lastFollow.current) return;
    lastFollow.current = key;
    const p = padding ?? { top: 0, right: 0, bottom: 0, left: 0 };
    // Centre the point in the area left visible above the sheet.
    const size = map.getSize();
    const target = map.project([follow.lat, follow.lng], map.getZoom()).add([0, (p.bottom - p.top) / 2]);
    if (size.x > 0) map.panTo(map.unproject(target, map.getZoom()), { animate: true, duration: 1.5 });
  }, [follow, map, padding]);

  return null;
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
  ensureStyle();
  const icons = useMemo(() => new Map<string, L.DivIcon>(), []);
  const iconKey = (m: MapMarker) => `${m.kind}|${m.vehicleType ?? ''}|${Math.round((m.heading ?? 0) / 10)}|${m.label ?? ''}|${m.highlight ? 1 : 0}`;
  const getIcon = (m: MapMarker) => {
    const key = iconKey(m);
    let icon = icons.get(key);
    if (!icon) {
      icon = iconFor(m);
      icons.set(key, icon);
    }
    return icon;
  };

  return (
    <View style={[{ flex: 1, overflow: 'hidden' }, style]}>
      <MapContainer
        center={[initialCenter.lat, initialCenter.lng]}
        zoom={initialZoom}
        zoomControl={false}
        style={{ height: '100%', width: '100%' }}
        attributionControl
      >
        <TileLayer
          url="https://tile.openstreetmap.org/{z}/{x}/{y}.png"
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
          maxZoom={19}
        />
        {circles.map((c) => (
          <Circle
            key={c.id}
            center={[c.lat, c.lng]}
            radius={c.radiusM}
            pathOptions={{ color: c.color, weight: 1.5, fillColor: c.color, fillOpacity: 0.12 }}
          />
        ))}
        {routes.map((r) => (
          <Polyline
            key={r.id}
            positions={r.points.map((p) => [p.lat, p.lng] as [number, number])}
            pathOptions={{ color: r.color ?? '#111827', weight: r.width ?? 5, opacity: 0.9, dashArray: r.dashed ? '2 9' : undefined, lineCap: 'round' }}
          />
        ))}
        {markers.map((m) => (
          <Marker key={m.id} position={[m.lat, m.lng]} icon={getIcon(m)} zIndexOffset={m.highlight ? 1000 : m.kind === 'car' ? 0 : 500} />
        ))}
        <MapBehaviour fit={fit} padding={padding} follow={follow} onPress={onPress} />
      </MapContainer>
    </View>
  );
}
