import { ReactNode, useEffect, useRef, useState } from 'react';

/**
 * Small SVG charts for the console. One y-axis only, thin columns (<= 24px) with 4px
 * rounded data-ends, a 2px surface gap between stacked segments, recessive hairline grid,
 * a legend for two or more series, and a hover tooltip on every column.
 */

export const SERIES = { blue: '#2a78d6', orange: '#eb6834', aqua: '#1baf7a' } as const;
const GRID = '#ECEEF1';

function useWidth<T extends HTMLElement>(): [React.RefObject<T | null>, number] {
  const ref = useRef<T>(null);
  const [width, setWidth] = useState(600);
  useEffect(() => {
    if (!ref.current) return;
    const ro = new ResizeObserver((entries) => setWidth(Math.max(200, entries[0].contentRect.width)));
    ro.observe(ref.current);
    return () => ro.disconnect();
  }, []);
  return [ref, width];
}

/** A clean axis maximum and its ticks (0, 50, 100 ...). */
function niceScale(max: number, ticks = 4): { top: number; step: number } {
  if (max <= 0) return { top: 1, step: 1 };
  const raw = max / ticks;
  const mag = 10 ** Math.floor(Math.log10(raw));
  const step = [1, 2, 2.5, 5, 10].map((m) => m * mag).find((s) => s >= raw) ?? 10 * mag;
  return { top: Math.ceil(max / step) * step, step };
}

function roundedTop(x: number, y: number, w: number, h: number, r: number): string {
  const rr = Math.min(r, w / 2, h);
  return `M${x},${y + h} L${x},${y + rr} Q${x},${y} ${x + rr},${y} L${x + w - rr},${y} Q${x + w},${y} ${x + w},${y + rr} L${x + w},${y + h} Z`;
}

export interface Series {
  key: string;
  label: string;
  color: string;
}

export interface ColumnDatum {
  label: string;
  /** Long label for the tooltip. */
  title?: string;
  values: Record<string, number>;
}

export function Legend({ series }: { series: Series[] }) {
  if (series.length < 2) return null;
  return (
    <div className="mb-2 flex flex-wrap gap-4 text-xs text-ink-2">
      {series.map((s) => (
        <span key={s.key} className="inline-flex items-center gap-1.5">
          <span className="inline-block h-2.5 w-2.5 rounded-sm" style={{ background: s.color }} />
          {s.label}
        </span>
      ))}
    </div>
  );
}

export function ColumnChart({
  data,
  series,
  height = 220,
  format = (v) => v.toLocaleString('en-IN'),
  labelEvery = 1,
  highlightLast = false,
}: {
  data: ColumnDatum[];
  series: Series[];
  height?: number;
  format?: (v: number) => string;
  labelEvery?: number;
  highlightLast?: boolean;
}) {
  const [ref, width] = useWidth<HTMLDivElement>();
  const [hover, setHover] = useState<number | null>(null);
  const left = 44;
  const bottom = 24;
  const top = 8;
  const plotW = width - left - 8;
  const plotH = height - bottom - top;
  const totals = data.map((d) => series.reduce((s, x) => s + (d.values[x.key] ?? 0), 0));
  const { top: max, step } = niceScale(Math.max(...totals, 0));
  const band = plotW / Math.max(1, data.length);
  const barW = Math.min(24, band * 0.6);
  const y = (v: number) => top + plotH - (v / max) * plotH;
  const ticks: number[] = [];
  for (let t = 0; t <= max + 1e-9; t += step) ticks.push(t);

  return (
    <div>
      <Legend series={series} />
      <div ref={ref} className="relative" onMouseLeave={() => setHover(null)}>
        <svg width={width} height={height} role="img" aria-label={series.map((s) => s.label).join(' and ')}>
          {ticks.map((t) => (
            <g key={t}>
              <line x1={left} x2={width - 8} y1={y(t)} y2={y(t)} stroke={GRID} strokeWidth={1} />
              <text x={left - 8} y={y(t) + 4} textAnchor="end" fontSize={11} fill="#6B7280" className="num">
                {format(t)}
              </text>
            </g>
          ))}
          {data.map((d, i) => {
            const cx = left + band * i + band / 2;
            let acc = 0;
            const segs = series.map((s) => ({ s, v: d.values[s.key] ?? 0 })).filter((x) => x.v > 0);
            return (
              <g key={i} opacity={hover == null || hover === i ? 1 : 0.45}>
                {segs.map(({ s, v }, j) => {
                  const y0 = y(acc);
                  acc += v;
                  const y1 = y(acc);
                  const isTop = j === segs.length - 1;
                  // 2px surface gap between stacked segments.
                  const h = Math.max(0, y0 - y1 - (j > 0 ? 2 : 0));
                  const yy = y1;
                  const fill = highlightLast && i !== data.length - 1 && series.length === 1 ? '#86b6ef' : s.color;
                  return isTop ? (
                    <path key={s.key} d={roundedTop(cx - barW / 2, yy, barW, h, 4)} fill={fill} />
                  ) : (
                    <rect key={s.key} x={cx - barW / 2} y={yy} width={barW} height={h} fill={fill} />
                  );
                })}
                {i % labelEvery === 0 || i === data.length - 1 ? (
                  <text x={cx} y={height - 6} textAnchor="middle" fontSize={11} fill="#6B7280">
                    {d.label}
                  </text>
                ) : null}
                <rect x={left + band * i} y={top} width={band} height={plotH} fill="transparent" onMouseEnter={() => setHover(i)} />
              </g>
            );
          })}
          <line x1={left} x2={width - 8} y1={y(0)} y2={y(0)} stroke="#D1D5DB" strokeWidth={1} />
        </svg>
        {hover != null ? (
          <Tooltip x={Math.min(width - 170, Math.max(0, left + band * hover + band / 2 - 80))} y={Math.max(0, y(totals[hover]) - 70)}>
            <div className="mb-1 font-semibold text-ink">{data[hover].title ?? data[hover].label}</div>
            {series.map((s) => (
              <div key={s.key} className="flex items-center justify-between gap-4">
                <span className="inline-flex items-center gap-1.5 text-ink-2">
                  <span className="inline-block h-2 w-2 rounded-sm" style={{ background: s.color }} />
                  {s.label}
                </span>
                <span className="num font-semibold text-ink">{format(data[hover].values[s.key] ?? 0)}</span>
              </div>
            ))}
          </Tooltip>
        ) : null}
      </div>
    </div>
  );
}

function Tooltip({ x, y, children }: { x: number; y: number; children: ReactNode }) {
  return (
    <div className="pointer-events-none absolute z-10 w-40 rounded-lg border border-line bg-white px-3 py-2 text-xs shadow-lg" style={{ left: x, top: y }}>
      {children}
    </div>
  );
}

/** Horizontal bars for a breakdown by category; value at the bar end, hover for detail. */
export function BarList({
  items,
  format = (v) => v.toLocaleString('en-IN'),
  color = SERIES.blue,
  detail,
}: {
  items: { key: string; label: string; value: number }[];
  format?: (v: number) => string;
  color?: string;
  detail?: (key: string) => string;
}) {
  const max = Math.max(1, ...items.map((i) => i.value));
  const total = items.reduce((s, i) => s + i.value, 0);
  return (
    <div className="space-y-3">
      {items.map((i) => (
        <div key={i.key} className="group" title={detail ? detail(i.key) : `${i.label}: ${format(i.value)} (${Math.round((i.value / Math.max(1, total)) * 100)}%)`}>
          <div className="mb-1 flex justify-between text-sm">
            <span className="font-medium text-ink-2">{i.label}</span>
            <span className="num font-semibold text-ink">
              {format(i.value)} <span className="font-normal text-ink-3">· {Math.round((i.value / Math.max(1, total)) * 100)}%</span>
            </span>
          </div>
          <div className="h-2.5 rounded-full bg-mist">
            <div className="h-2.5 rounded-full transition-all group-hover:opacity-80" style={{ width: `${(i.value / max) * 100}%`, background: color }} />
          </div>
        </div>
      ))}
    </div>
  );
}

