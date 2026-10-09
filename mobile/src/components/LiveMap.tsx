import { Ionicons } from '@expo/vector-icons';
import { useState } from 'react';
import { type LayoutChangeEvent, Text, View } from 'react-native';

import { colors } from '@/lib/theme';

/** highlight = orange (the rider's pickup, the driver's next stop), target = dark (the rider's drop). */
export type MapStopTone = 'highlight' | 'target' | 'passed' | 'normal';

export interface MapStop {
  key: string;
  name: string;
  latitude: number;
  longitude: number;
  tone: MapStopTone;
}

export interface MapMarker {
  key: string;
  kind: 'cab' | 'rider';
  label: string;
  latitude: number;
  longitude: number;
  /** Reported in the last two minutes; stale markers are drawn grey. */
  live: boolean;
  accuracyM?: number | null;
}

const PAD_X = 52;
/** Room above the top point for a marker's label, and below the bottom one for a stop name and the scale bar. */
const PAD_TOP = 40;
const PAD_BOTTOM = 56;
/** Never zoom in closer than this, so a cab at its stop does not fill the frame. */
const MIN_SPAN_M = 400;
const SCALE_STEPS = [20, 50, 100, 200, 250, 500, 1000, 2000, 5000];

/**
 * A schematic plant map drawn from coordinates: the route's stops joined in
 * order, the cab and riders on top, north up, with a scale bar. The plant's
 * internal roads are not on public maps, and this needs no map SDK or key,
 * so it looks the same on a phone and on the web console.
 *
 * `focus` lists the keys (stops or markers) the view is fitted to, e.g. the
 * cab, the rider and the pickup; everything else is drawn when it falls inside.
 */
export function LiveMap({
  stops,
  markers,
  focus,
  height = 240,
}: {
  stops: MapStop[];
  markers: MapMarker[];
  focus?: string[];
  height?: number;
}) {
  const [width, setWidth] = useState(0);
  const points = [...stops, ...markers];
  if (points.length === 0) return null;
  const focused = focus ? points.filter((p) => focus.includes(p.key)) : [];
  const fit = focused.length > 0 ? focused : points;

  const midLat = fit.reduce((sum, p) => sum + p.latitude, 0) / fit.length;
  const mPerLng = Math.cos((midLat * Math.PI) / 180) * 111_320;
  const mPerLat = 110_540;
  const xs = fit.map((p) => p.longitude * mPerLng);
  const ys = fit.map((p) => -p.latitude * mPerLat);
  const minX = Math.min(...xs);
  const maxX = Math.max(...xs);
  const minY = Math.min(...ys);
  const maxY = Math.max(...ys);
  const scale =
    width > 0
      ? Math.min(
          (width - 2 * PAD_X) / Math.max(maxX - minX, MIN_SPAN_M),
          (height - PAD_TOP - PAD_BOTTOM) / Math.max(maxY - minY, MIN_SPAN_M),
        )
      : 0;
  const cx = (minX + maxX) / 2;
  const cy = (minY + maxY) / 2;
  const at = (p: { latitude: number; longitude: number }) => ({
    x: width / 2 + (p.longitude * mPerLng - cx) * scale,
    y: PAD_TOP + (height - PAD_TOP - PAD_BOTTOM) / 2 + (-p.latitude * mPerLat - cy) * scale,
  });
  const scaleM = SCALE_STEPS.find((m) => m * scale >= 50) ?? SCALE_STEPS[SCALE_STEPS.length - 1];
  const labelShift = width > 0 ? riderLabelShifts(markers, at) : {};

  return (
    <View
      onLayout={(e: LayoutChangeEvent) => setWidth(e.nativeEvent.layout.width)}
      className="overflow-hidden rounded-xl border border-line bg-mist"
      style={{ height }}
      accessible
      accessibilityLabel={`Map: ${markers.map((m) => m.label).join(', ')}${stops.length ? `; stops ${stops.map((s) => s.name).join(', ')}` : ''}`}
    >
      {width > 0 ? (
        <>
          {stops.slice(1).map((s, i) => (
            <Segment key={`seg-${s.key}`} from={at(stops[i])} to={at(s)} passed={stops[i].tone === 'passed' && s.tone === 'passed'} />
          ))}
          {stops.map((s) => (
            <StopDot key={s.key} stop={s} {...at(s)} width={width} />
          ))}
          {markers.map((m) =>
            m.accuracyM && m.accuracyM * scale > 14 ? <AccuracyHalo key={`acc-${m.key}`} marker={m} radius={m.accuracyM * scale} {...at(m)} /> : null,
          )}
          {markers.map((m) => (
            <Marker key={m.key} marker={m} {...at(m)} labelShift={labelShift[m.key] ?? 0} />
          ))}
          <View className="absolute bottom-2 left-3">
            <View style={{ width: scaleM * scale, height: 4, borderColor: colors.ink3, borderBottomWidth: 2, borderLeftWidth: 2, borderRightWidth: 2 }} />
            <Text className="mt-0.5 text-[10px] text-ink-3">{scaleM >= 1000 ? `${scaleM / 1000} km` : `${scaleM} m`}</Text>
          </View>
          <View className="absolute right-3 top-2 items-center">
            <Ionicons name="navigate" size={14} color={colors.ink3} style={{ transform: [{ rotate: '-45deg' }] }} />
            <Text className="text-[10px] font-bold text-ink-3">N</Text>
          </View>
        </>
      ) : null}
    </View>
  );
}

function Segment({ from, to, passed }: { from: { x: number; y: number }; to: { x: number; y: number }; passed: boolean }) {
  const length = Math.hypot(to.x - from.x, to.y - from.y);
  if (length < 1) return null;
  const angle = Math.atan2(to.y - from.y, to.x - from.x);
  return (
    <View
      style={{
        position: 'absolute',
        left: (from.x + to.x) / 2 - length / 2,
        top: (from.y + to.y) / 2 - 2,
        width: length,
        height: 4,
        borderRadius: 2,
        backgroundColor: passed ? colors.line : '#A9B6C3',
        transform: [{ rotate: `${angle}rad` }],
      }}
    />
  );
}

type Box = { x: number; y: number; w: number; h: number };
const overlaps = (a: Box, b: Box) => a.x < b.x + b.w && b.x < a.x + a.w && a.y < b.y + b.h && b.y < a.y + a.h;

/**
 * Riders waiting together at a gate would stack their name labels on top of
 * each other and of the cab: move each label down until it is clear.
 */
function riderLabelShifts(markers: MapMarker[], at: (p: MapMarker) => { x: number; y: number }): Record<string, number> {
  const taken: Box[] = [];
  for (const m of markers) {
    if (m.kind !== 'cab') continue;
    const p = at(m);
    taken.push({ x: p.x - 15, y: p.y - 15, w: 30, h: 30 }, { x: p.x - 30, y: p.y - 36, w: 60, h: 18 });
  }
  const shifts: Record<string, number> = {};
  const riders = markers.filter((m) => m.kind === 'rider').sort((a, b) => at(a).y - at(b).y);
  for (const m of riders) {
    const p = at(m);
    const w = 14 + m.label.length * 6.2;
    let dy = 0;
    for (let i = 0; i < 6; i++) {
      const box = { x: p.x + 15, y: p.y - 9 + dy, w, h: 18 };
      if (!taken.some((t) => overlaps(t, box))) {
        taken.push(box);
        break;
      }
      dy += 19;
    }
    shifts[m.key] = dy;
  }
  return shifts;
}

function StopDot({ stop, x, y, width }: { stop: MapStop; x: number; y: number; width: number }) {
  const fill = { highlight: colors.accent, target: colors.ink, passed: colors.line, normal: colors.white }[stop.tone];
  const border = { highlight: colors.accent, target: colors.ink, passed: colors.ink3, normal: colors.ink3 }[stop.tone];
  return (
    <>
      <View
        style={{ position: 'absolute', left: x - 7, top: y - 7, width: 14, height: 14, borderRadius: 7, backgroundColor: fill, borderColor: border, borderWidth: 2 }}
      />
      <Text
        numberOfLines={1}
        // Kept inside the frame, so a stop at the edge still shows its whole name.
        style={{ position: 'absolute', left: Math.min(Math.max(x - 50, 4), width - 104), top: y + 8, width: 100, textAlign: 'center' }}
        className={`text-[10px] ${stop.tone === 'passed' ? 'text-ink-3' : 'font-semibold text-ink-2'}`}
      >
        {stop.name}
      </Text>
    </>
  );
}

function AccuracyHalo({ marker, radius, x, y }: { marker: MapMarker; radius: number; x: number; y: number }) {
  const r = Math.min(radius, 80);
  const tint = marker.kind === 'rider' ? colors.teal : colors.ink;
  return (
    <View
      style={{ position: 'absolute', left: x - r, top: y - r, width: 2 * r, height: 2 * r, borderRadius: r, backgroundColor: `${tint}22`, borderColor: `${tint}55`, borderWidth: 1 }}
    />
  );
}

function Marker({ marker, x, y, labelShift }: { marker: MapMarker; x: number; y: number; labelShift: number }) {
  const size = marker.kind === 'cab' ? 30 : 26;
  const bg = !marker.live ? colors.ink3 : marker.kind === 'cab' ? colors.ink : colors.teal;
  return (
    <>
      <View
        style={{
          position: 'absolute',
          left: x - size / 2,
          top: y - size / 2,
          width: size,
          height: size,
          borderRadius: size / 2,
          backgroundColor: bg,
          borderColor: colors.white,
          borderWidth: 2,
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        <Ionicons name={marker.kind === 'cab' ? 'car' : 'person'} size={marker.kind === 'cab' ? 16 : 14} color={colors.white} />
      </View>
      {/* The cab's label sits above it, a rider's to the right, so a rider waiting beside the cab stays readable. */}
      <View
        style={
          marker.kind === 'cab'
            ? { position: 'absolute', left: x - 50, top: y - size / 2 - 20, width: 100, alignItems: 'center' }
            : { position: 'absolute', left: x + size / 2 + 2, top: y - 9 + labelShift, width: 100, alignItems: 'flex-start' }
        }
      >
        <View className="rounded-full px-1.5 py-0.5" style={{ backgroundColor: bg }}>
          <Text numberOfLines={1} className="text-[10px] font-bold text-white">
            {marker.label}
          </Text>
        </View>
      </View>
    </>
  );
}
