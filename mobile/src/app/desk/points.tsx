import { Ionicons } from '@expo/vector-icons';
import { useMemo, useState } from 'react';
import { Switch, Text, TextInput, View } from 'react-native';

import type { PickupPointDto, SavePickupPointRequest } from '@/api/types';
import { toApiError } from '@/api/client';
import { DeskHeader } from '@/components/DeskHeader';
import {
  Banner,
  Button,
  Card,
  EmptyState,
  ErrorBanner,
  ErrorState,
  Field,
  LoadingState,
  Muted,
  Pill,
  Row,
  Screen,
  SectionTitle,
} from '@/components/ui';
import { useAdminStops, useSavePickupPoint } from '@/hooks/queries';
import { colors } from '@/lib/theme';

/** The form's text state; numbers stay strings until saved so half-typed values are not lost. */
interface Draft {
  id: number | null;
  code: string;
  name: string;
  zone: string;
  landmark: string;
  latitude: string;
  longitude: string;
  geofenceM: string;
  active: boolean;
}

const EMPTY: Draft = { id: null, code: '', name: '', zone: '', landmark: '', latitude: '', longitude: '', geofenceM: '40', active: true };

function toDraft(p: PickupPointDto): Draft {
  return {
    id: p.id,
    code: p.code,
    name: p.name,
    zone: p.zone,
    landmark: p.landmark ?? '',
    latitude: p.latitude.toFixed(6),
    longitude: p.longitude.toFixed(6),
    geofenceM: String(p.geofenceM),
    active: p.active,
  };
}

/**
 * Pickup points: every place a ride can come to. The admin adds a point,
 * says exactly where to wait (riders see it), sets the arrival radius used by
 * GPS auto-arrival, and takes points out of service. A point joins a route in
 * Routes & rules → Stop sequence.
 */
export default function PickupPointsScreen() {
  const points = useAdminStops();
  const save = useSavePickupPoint();
  const [draft, setDraft] = useState<Draft | null>(null);
  const [filter, setFilter] = useState('');
  // Kept here, not in the form, so the confirmation survives the list refreshing after a save.
  const [saved, setSaved] = useState<string | null>(null);

  const shown = useMemo(() => {
    const q = filter.trim().toLowerCase();
    const all = points.data ?? [];
    return q
      ? all.filter((p) => [p.name, p.code, p.zone, p.landmark ?? ''].some((v) => v.toLowerCase().includes(q)))
      : all;
  }, [points.data, filter]);

  const open = (d: Draft) => {
    save.reset();
    setSaved(null);
    setDraft(d);
  };

  const submit = () => {
    if (!draft) return;
    const body: SavePickupPointRequest = {
      code: draft.id == null ? draft.code.trim().toUpperCase() : null,
      name: draft.name.trim(),
      zone: draft.zone.trim(),
      landmark: draft.landmark.trim() || null,
      latitude: Number(draft.latitude),
      longitude: Number(draft.longitude),
      geofenceM: Number(draft.geofenceM),
      active: draft.id == null ? true : draft.active,
    };
    save.mutate(
      { id: draft.id, body },
      {
        onSuccess: (p) => {
          setSaved(
            draft.id == null
              ? `${p.name} added. Put it on a route in Routes & rules → Stop sequence so rides can come there.`
              : `${p.name} saved${p.active ? '' : ' · out of service'}.`,
          );
          setDraft(null);
        },
      },
    );
  };

  const inService = (points.data ?? []).filter((p) => p.active).length;

  return (
    <Screen width="lg" refreshing={points.isRefetching} onRefresh={() => points.refetch()}>
      <DeskHeader title="Pickup points" subtitle="Where rides pick up and drop. Riders see the landmark; the radius drives GPS arrival." />
      {saved ? <Banner tone="teal" icon="checkmark-circle" title={saved} /> : null}

      {draft ? (
        <PointForm draft={draft} onChange={setDraft} onSave={submit} onCancel={() => setDraft(null)} saving={save.isPending} error={save.error} />
      ) : (
        <Row className="mb-1 flex-wrap justify-between">
          <View className="mb-2 mr-3 min-w-[240px] flex-1">
            <TextInput
              value={filter}
              onChangeText={setFilter}
              placeholder="Search by name, code, zone or landmark"
              placeholderTextColor={colors.ink3}
              accessibilityLabel="Search pickup points"
              className="min-h-[44px] rounded-xl border border-line bg-white px-3.5 text-base text-ink"
            />
          </View>
          <Button label="Add pickup point" icon="add" size="md" className="mb-2" onPress={() => open(EMPTY)} />
        </Row>
      )}

      {points.isPending ? (
        <LoadingState />
      ) : points.isError ? (
        <ErrorState error={points.error} onRetry={() => points.refetch()} />
      ) : (
        <>
          <SectionTitle title={`${inService} in service · ${points.data.length - inService} out of service`} />
          {shown.length === 0 ? (
            <EmptyState icon="location" title="No pickup point matches" message="Try another name, code or zone." />
          ) : (
            shown.map((p) => <PointRow key={p.id} p={p} onEdit={() => open(toDraft(p))} editing={draft?.id === p.id} />)
          )}
        </>
      )}
    </Screen>
  );
}

function PointRow({ p, onEdit, editing }: { p: PickupPointDto; onEdit: () => void; editing: boolean }) {
  return (
    <Card className={`mb-2 ${editing ? 'border-accent' : ''} ${p.active ? '' : 'bg-mist'}`}>
      <Row className="items-start justify-between">
        <View className="flex-1 pr-3">
          <Row className="flex-wrap">
            <Text className={`mr-2 text-lg font-bold ${p.active ? 'text-ink' : 'text-ink-3'}`}>{p.name}</Text>
            <Pill label={p.code} tone="neutral" />
            <View className="w-1.5" />
            <Pill label={`Zone ${p.zone}`} tone="neutral" />
          </Row>
          <Row className="mt-1.5">
            <Ionicons name="location" size={15} color={p.landmark ? colors.accent : colors.ink3} />
            <Muted className="ml-1.5 flex-1">{p.landmark ?? 'No landmark yet: tell riders exactly where to wait'}</Muted>
          </Row>
          <Muted className="mt-1 text-xs">
            {p.latitude.toFixed(6)}, {p.longitude.toFixed(6)} · arrival radius {p.geofenceM} m
          </Muted>
          <Row className="mt-2 flex-wrap">
            {p.routes.length === 0 ? (
              <Pill label="Not on a route yet" tone="amber" />
            ) : (
              p.routes.map((r) => (
                <View key={r} className="mb-1 mr-1.5">
                  <Pill label={r} tone="accent" />
                </View>
              ))
            )}
            {p.liveBookings > 0 ? (
              <View className="mb-1">
                <Pill label={`${p.liveBookings} open ${p.liveBookings === 1 ? 'booking' : 'bookings'}`} tone="teal" />
              </View>
            ) : null}
          </Row>
        </View>
        <View className="items-end">
          <Pill label={p.active ? 'In service' : 'Out of service'} tone={p.active ? 'teal' : 'danger'} />
          <Button label="Edit" icon="create-outline" size="sm" variant="secondary" className="mt-2" onPress={onEdit} accessibilityLabel={`Edit ${p.name}`} />
        </View>
      </Row>
    </Card>
  );
}

function PointForm({
  draft,
  onChange,
  onSave,
  onCancel,
  saving,
  error,
}: {
  draft: Draft;
  onChange: (d: Draft) => void;
  onSave: () => void;
  onCancel: () => void;
  saving: boolean;
  error: unknown;
}) {
  const set = (patch: Partial<Draft>) => onChange({ ...draft, ...patch });
  const apiError = error ? toApiError(error) : null;
  const fe = (field: string) => apiError?.fieldError(field);
  const creating = draft.id == null;
  const numeric = (t: string) => t.replace(/[^0-9.-]/g, '');
  return (
    <Card className="mb-4 border-accent">
      <Text className="mb-3 text-lg font-bold text-ink">{creating ? 'Add a pickup point' : `Edit ${draft.name}`}</Text>
      {error ? <ErrorBanner error={error} /> : null}
      <View className="flex-row flex-wrap items-start">
        <View className="mr-3 min-w-[140px] flex-1">
          <Field
            label="Code"
            value={draft.code}
            onChangeText={(code) => set({ code: code.toUpperCase().replace(/[^A-Z0-9-]/g, '') })}
            editable={creating}
            placeholder="GATE3"
            autoCapitalize="characters"
            maxLength={20}
            error={fe('code')}
            hint={creating ? 'Short and permanent' : 'Fixed once created'}
          />
        </View>
        <View className="mr-3 min-w-[200px] flex-[2]">
          <Field label="Name" value={draft.name} onChangeText={(name) => set({ name })} placeholder="Gate 3" maxLength={100} error={fe('name')} />
        </View>
        <View className="min-w-[100px] flex-1">
          <Field label="Zone" value={draft.zone} onChangeText={(zone) => set({ zone: zone.toUpperCase() })} placeholder="Z1" maxLength={20} error={fe('zone')} />
        </View>
      </View>
      <Field
        label="Where to wait (shown to riders)"
        value={draft.landmark}
        onChangeText={(landmark) => set({ landmark })}
        placeholder="Security cabin, truck lane"
        maxLength={160}
        error={fe('landmark')}
      />
      <View className="flex-row flex-wrap items-start">
        <View className="mr-3 min-w-[150px] flex-1">
          <Field label="Latitude" value={draft.latitude} onChangeText={(t) => set({ latitude: numeric(t) })} keyboardType="decimal-pad" placeholder="18.619500" error={fe('latitude')} />
        </View>
        <View className="mr-3 min-w-[150px] flex-1">
          <Field label="Longitude" value={draft.longitude} onChangeText={(t) => set({ longitude: numeric(t) })} keyboardType="decimal-pad" placeholder="73.795500" error={fe('longitude')} />
        </View>
        <View className="min-w-[120px] flex-1">
          <Field
            label="Arrival radius (m)"
            value={draft.geofenceM}
            onChangeText={(t) => set({ geofenceM: t.replace(/\D/g, '') })}
            keyboardType="number-pad"
            error={fe('geofenceM')}
            hint="10 to 500"
          />
        </View>
      </View>
      {!creating ? (
        <Row className="mb-3 justify-between rounded-xl bg-mist p-3">
          <View className="flex-1 pr-3">
            <Text className="text-base font-semibold text-ink">In service</Text>
            <Muted>Out of service hides it from riders. A point on a route or with open bookings cannot be taken out.</Muted>
          </View>
          <Switch
            value={draft.active}
            onValueChange={(active) => set({ active })}
            accessibilityLabel="In service"
            trackColor={{ true: colors.teal, false: colors.line }}
            thumbColor={colors.white}
          />
        </Row>
      ) : null}
      <Row className="justify-end">
        <Button label="Cancel" variant="secondary" size="md" className="mr-2" onPress={onCancel} />
        <Button label={creating ? 'Add pickup point' : 'Save changes'} icon="save" size="md" loading={saving} onPress={onSave} />
      </Row>
    </Card>
  );
}
