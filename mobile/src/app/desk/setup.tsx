import { Ionicons } from '@expo/vector-icons';
import { ReactNode, useEffect, useMemo, useState } from 'react';
import { Text, TextInput, View } from 'react-native';

import type { RouteDto } from '@/api/types';
import { DeskHeader } from '@/components/DeskHeader';
import {
  Banner,
  Button,
  Card,
  Chip,
  ErrorBanner,
  ErrorState,
  IconButton,
  LoadingState,
  Muted,
  Pill,
  Row,
  Screen,
  SectionTitle,
  Stepper,
} from '@/components/ui';
import { useAdminRoutes, useAudit, useCompliance, useResequence, useResetDemo, useUpdateRules } from '@/hooks/queries';
import { confirmAction } from '@/lib/dialogs';
import { dayAndTime, km, minutes } from '@/lib/format';
import { colors } from '@/lib/theme';

export default function SetupScreen() {
  const routes = useAdminRoutes();
  const [routeId, setRouteId] = useState<number | null>(null);
  const route = routes.data?.find((r) => r.id === routeId) ?? routes.data?.[0];

  return (
    <Screen width="xl" refreshing={routes.isRefetching} onRefresh={() => routes.refetch()}>
      <DeskHeader title="Routes & rules" subtitle="Configuration, not code: changes apply immediately and are audited" />
      {routes.isPending ? (
        <LoadingState />
      ) : routes.isError ? (
        <ErrorState error={routes.error} onRetry={() => routes.refetch()} />
      ) : (
        <>
          <View className="flex-row flex-wrap">
            {routes.data.map((r) => (
              <Chip key={r.id} label={r.label} icon="git-commit" selected={r.id === route?.id} onPress={() => setRouteId(r.id)} />
            ))}
          </View>
          {route ? <RouteEditor key={`${route.id}-${route.version}`} route={route} /> : null}
        </>
      )}
      <CompliancePanel />
      <AuditPanel />
      <DemoPanel />
    </Screen>
  );
}

function RouteEditor({ route }: { route: RouteDto }) {
  const update = useUpdateRules();
  const [cap, setCap] = useState(route.maxPassengers);
  const [wait, setWait] = useState(route.maxWaitMin);
  const [detour, setDetour] = useState(route.maxDetourMin);
  const [speed, setSpeed] = useState(route.speedLimitKmh);
  const [saved, setSaved] = useState<string | null>(null);
  const dirty = cap !== route.maxPassengers || wait !== route.maxWaitMin || detour !== route.maxDetourMin || speed !== route.speedLimitKmh;

  const save = async () => {
    const lines = [
      cap !== route.maxPassengers ? `Max passengers ${route.maxPassengers} → ${cap}` : null,
      wait !== route.maxWaitMin ? `Max wait ${route.maxWaitMin} → ${wait} min` : null,
      detour !== route.maxDetourMin ? `Max detour ${route.maxDetourMin} → ${detour} min` : null,
      speed !== route.speedLimitKmh ? `Speed limit ${route.speedLimitKmh} → ${speed} km/h` : null,
    ].filter(Boolean);
    if (!(await confirmAction(`Update ${route.label}?`, `${lines.join('\n')}\n\nApplies to new matches immediately.`, 'Save rules'))) return;
    update.mutate(
      {
        routeId: route.id,
        body: { maxPassengers: cap, maxWaitMin: wait, maxDetourMin: detour, speedLimitKmh: speed, frequencyMin: route.frequencyMin, version: route.version },
      },
      { onSuccess: () => setSaved(`${route.label} updated: ${lines.join(', ')}`) },
    );
  };

  return (
    <View>
      <Card className="mt-2 bg-ink">
        <Text className="text-xl font-bold text-white">{route.label}</Text>
        <Text className="mt-1 text-sm text-line">
          {route.routeType === 'LOOP' ? 'Loop' : 'One way'} · {route.stops.length} stops · {minutes(route.totalMinutes)} · {km(route.totalKm)}
          {route.serviceNote ? ` · ${route.serviceNote}` : ''}
        </Text>
      </Card>

      {saved ? <View className="mt-3"><Banner tone="teal" icon="checkmark-circle" title="Saved" message={saved} /></View> : null}
      {update.isError ? <View className="mt-3"><ErrorBanner error={update.error} /></View> : null}

      <SectionTitle title="Rules applied to this route" />
      <Card>
        <RuleRow label="Max passengers per pick-up" hint="The occupancy cap. The engine never offers a full cab.">
          <Stepper value={cap} min={1} max={12} onChange={setCap} />
        </RuleRow>
        <RuleRow label="Max wait at a stop" hint="Then the driver may mark a no-show.">
          <Stepper value={wait} min={1} max={30} onChange={setWait} suffix="min" />
        </RuleRow>
        <RuleRow label="Max detour for riders on board" hint="Extra minutes a new pickup may add.">
          <Stepper value={detour} min={0} max={60} onChange={setDetour} suffix="min" />
        </RuleRow>
        <RuleRow label="Zone speed limit" hint="Overspeed raises a desk alert.">
          <Stepper value={speed} min={5} max={80} onChange={setSpeed} suffix="km/h" />
        </RuleRow>
        <Row className="mt-3 justify-end">
          {dirty ? (
            <Button
              label="Undo"
              variant="secondary"
              size="md"
              className="mr-2"
              onPress={() => {
                setCap(route.maxPassengers);
                setWait(route.maxWaitMin);
                setDetour(route.maxDetourMin);
                setSpeed(route.speedLimitKmh);
              }}
            />
          ) : null}
          <Button label="Save rules" size="md" icon="save" disabled={!dirty} loading={update.isPending} onPress={save} />
        </Row>
      </Card>

      <SequenceEditor route={route} />
    </View>
  );
}

function RuleRow({ label, hint, children }: { label: string; hint: string; children: ReactNode }) {
  return (
    <Row className="flex-wrap justify-between border-b border-line py-3">
      <View className="mb-2 mr-3 min-w-[220px] flex-1">
        <Text className="text-base font-semibold text-ink">{label}</Text>
        <Muted>{hint}</Muted>
      </View>
      {children}
    </Row>
  );
}

interface EditableStop {
  key: string;
  stopId: number;
  stopName: string;
  legMinutes: string;
  legKm: string;
}

function SequenceEditor({ route }: { route: RouteDto }) {
  const resequence = useResequence();
  const initial = useMemo<EditableStop[]>(
    () =>
      route.stops.map((s) => ({
        key: `${s.seq}-${s.stopId}`,
        stopId: s.stopId,
        stopName: s.stopName,
        legMinutes: String(s.legMinutes),
        legKm: String(s.legKm),
      })),
    [route],
  );
  const [stops, setStops] = useState(initial);
  const [saved, setSaved] = useState(false);
  useEffect(() => setStops(initial), [initial]);
  const dirty = JSON.stringify(stops) !== JSON.stringify(initial);

  const move = (index: number, delta: number) => {
    const target = index + delta;
    if (target < 0 || target >= stops.length) return;
    const next = [...stops];
    [next[index], next[target]] = [next[target], next[index]];
    setStops(next);
    setSaved(false);
  };
  const edit = (index: number, patch: Partial<EditableStop>) => {
    setStops((cur) => cur.map((s, i) => (i === index ? { ...s, ...patch } : s)));
    setSaved(false);
  };

  const save = () =>
    resequence.mutate(
      {
        routeId: route.id,
        body: {
          version: route.version,
          stops: stops.map((s, i) => ({
            stopId: s.stopId,
            legMinutes: i === 0 ? 0 : Number(s.legMinutes || 0),
            legKm: i === 0 ? 0 : Number(s.legKm || 0),
          })),
        },
      },
      { onSuccess: () => setSaved(true) },
    );

  return (
    <View>
      <SectionTitle title="Stop sequence" />
      <Muted className="mb-2">
        Vehicles serve stops in this order and never double back. Leg time and distance are from the previous stop.
      </Muted>
      {saved ? <Banner tone="teal" icon="checkmark-circle" title="Sequence saved" message="New trips follow the new order." /> : null}
      {resequence.isError ? <ErrorBanner error={resequence.error} /> : null}
      <Card className="p-2">
        {stops.map((s, i) => (
          <Row key={s.key} className={`px-2 py-2.5 ${i > 0 ? 'border-t border-line' : ''}`}>
            <View className="h-8 w-8 items-center justify-center rounded-full bg-ink">
              <Text className="text-sm font-bold text-white">{i + 1}</Text>
            </View>
            <Text className="ml-3 flex-1 text-base font-semibold text-ink">{s.stopName}</Text>
            {i > 0 ? (
              <>
                <LegInput value={s.legMinutes} suffix="min" onChange={(legMinutes) => edit(i, { legMinutes })} label={`Minutes to ${s.stopName}`} />
                <LegInput value={s.legKm} suffix="km" onChange={(legKm) => edit(i, { legKm })} label={`Kilometres to ${s.stopName}`} decimal />
              </>
            ) : (
              <Muted className="mr-2">start</Muted>
            )}
            <IconButton icon="arrow-up" label={`Move ${s.stopName} up`} onPress={() => move(i, -1)} />
            <View className="w-1" />
            <IconButton icon="arrow-down" label={`Move ${s.stopName} down`} onPress={() => move(i, 1)} />
          </Row>
        ))}
      </Card>
      <Row className="mt-3 justify-end">
        {dirty ? <Button label="Undo" variant="secondary" size="md" className="mr-2" onPress={() => setStops(initial)} /> : null}
        <Button label="Save sequence" size="md" icon="save" disabled={!dirty} loading={resequence.isPending} onPress={save} />
      </Row>
    </View>
  );
}

function LegInput({
  value,
  suffix,
  onChange,
  label,
  decimal,
}: {
  value: string;
  suffix: string;
  onChange: (v: string) => void;
  label: string;
  decimal?: boolean;
}) {
  return (
    <Row className="mr-2">
      <TextInput
        value={value}
        onChangeText={(t) => onChange(t.replace(decimal ? /[^0-9.]/g : /\D/g, ''))}
        keyboardType={decimal ? 'decimal-pad' : 'number-pad'}
        accessibilityLabel={label}
        className="h-10 w-14 rounded-lg border border-line bg-white text-center text-base text-ink"
      />
      <Text className="ml-1 text-xs text-ink-3">{suffix}</Text>
    </Row>
  );
}

function CompliancePanel() {
  const compliance = useCompliance();
  return (
    <View>
      <SectionTitle title="Document compliance" />
      {compliance.isPending ? (
        <LoadingState />
      ) : compliance.isError ? (
        <ErrorBanner error={compliance.error} />
      ) : compliance.data.length === 0 ? (
        <Card>
          <Muted>Every document is valid for more than 30 days.</Muted>
        </Card>
      ) : (
        <Card className="p-0">
          {compliance.data.map((c, i) => (
            <Row key={`${c.entityType}-${c.entityId}-${c.document}`} className={`px-4 py-3 ${i > 0 ? 'border-t border-line' : ''}`}>
              <Ionicons name={c.entityType === 'VEHICLE' ? 'car' : 'person'} size={18} color={colors.ink3} />
              <View className="ml-3 flex-1">
                <Text className="text-base font-semibold text-ink">
                  {c.code} · {c.document}
                </Text>
                <Muted>
                  {c.name} · {c.reference}
                </Muted>
              </View>
              <Pill label={c.blocksDispatch ? `${c.label} · blocked` : c.label} tone={c.state === 'EXPIRED' ? 'danger' : 'amber'} />
            </Row>
          ))}
        </Card>
      )}
    </View>
  );
}

function AuditPanel() {
  const audit = useAudit();
  return (
    <View>
      <SectionTitle title="Audit trail" />
      {audit.isPending ? (
        <LoadingState />
      ) : audit.isError ? (
        <ErrorBanner error={audit.error} />
      ) : (
        <Card className="p-0">
          {audit.data.slice(0, 15).map((a, i) => (
            <View key={a.id} className={`px-4 py-2.5 ${i > 0 ? 'border-t border-line' : ''}`}>
              <Row className="justify-between">
                <Text className="text-sm font-bold text-ink">{a.action.replace(/_/g, ' ').toLowerCase()}</Text>
                <Muted className="text-xs">{dayAndTime(a.createdAt)}</Muted>
              </Row>
              <Muted className="text-sm">
                {a.actorName}
                {a.details ? ` · ${a.details}` : ''}
              </Muted>
            </View>
          ))}
        </Card>
      )}
    </View>
  );
}

function DemoPanel() {
  const reset = useResetDemo();
  return (
    <View>
      <SectionTitle title="Demo tools" />
      <Card>
        <Text className="text-base font-semibold text-ink">Reset demo data</Text>
        <Muted>Reloads the seed data: C-12 goes off duty, the two Gate 2 riders wait again, and billing returns to the seeded month.</Muted>
        {reset.isSuccess ? <View className="mt-2"><Banner tone="teal" icon="refresh" title={reset.data.message} /></View> : null}
        {reset.isError ? <View className="mt-2"><ErrorBanner error={reset.error} /></View> : null}
        <View className="mt-3 self-start">
          <Button
            label="Reset demo data"
            variant="secondary"
            size="md"
            icon="refresh"
            loading={reset.isPending}
            onPress={async () => {
              if (await confirmAction('Reset all demo data?', 'Every booking, trip and duty goes back to the starting state.', 'Reset', true)) {
                reset.mutate();
              }
            }}
          />
        </View>
      </Card>
    </View>
  );
}
