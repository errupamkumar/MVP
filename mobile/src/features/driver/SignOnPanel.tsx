import { Ionicons } from '@expo/vector-icons';
import { useEffect, useState } from 'react';
import { Pressable, Text, View } from 'react-native';

import type { DriverProfileDto, VehicleOptionDto } from '@/api/types';
import { DocumentList } from '@/components/DocumentList';
import {
  Banner,
  Button,
  Card,
  ErrorBanner,
  ErrorState,
  Field,
  H1,
  LoadingState,
  Muted,
  Row,
  SectionTitle,
} from '@/components/ui';
import { useDriverVehicles, useStartDuty } from '@/hooks/queries';
import { colors } from '@/lib/theme';

const CHECKS: { key: string; label: string }[] = [
  { key: 'TYRES', label: 'Tyres and pressure' },
  { key: 'LIGHTS', label: 'Lights and indicators' },
  { key: 'BELTS', label: 'Seat belts (all seats)' },
  { key: 'FUEL', label: 'Fuel level' },
  { key: 'FIRST_AID', label: 'First-aid kit and extinguisher' },
];

/** Start of duty: documents, vehicle, the five-point check and the odometer. Blocks the queue until done. */
export function SignOnPanel({ profile }: { profile: DriverProfileDto }) {
  const vehicles = useDriverVehicles(true);
  const start = useStartDuty();
  const [vehicleId, setVehicleId] = useState<number | null>(null);
  const [checked, setChecked] = useState<string[]>([]);
  const [odometer, setOdometer] = useState('');

  const selected = vehicles.data?.find((v) => v.id === vehicleId) ?? null;

  // Choosing a vehicle pre-fills its last odometer reading. Only on choice: a background
  // refetch must never overwrite what the driver has typed.
  const choose = (v: VehicleOptionDto) => {
    setVehicleId(v.id);
    setOdometer(String(v.odometerKm));
  };

  // Pre-select the driver's regular cab when it is available.
  useEffect(() => {
    if (vehicleId == null && vehicles.data) {
      const preferred = vehicles.data.find((v) => v.id === profile.defaultVehicleId && v.available) ?? vehicles.data.find((v) => v.available);
      if (preferred) {
        setVehicleId(preferred.id);
        setOdometer(String(preferred.odometerKm));
      }
    }
  }, [vehicles.data, vehicleId, profile.defaultVehicleId]);

  const odometerValue = Number(odometer.replace(/\D/g, ''));
  const odometerError =
    selected && odometer && odometerValue < selected.odometerKm
      ? `Below the last recorded ${selected.odometerKm.toLocaleString('en-IN')} km`
      : undefined;
  const ready = !!selected?.available && checked.length === CHECKS.length && !!odometer && !odometerError && !profile.blockedReason;

  const toggle = (key: string) => setChecked((c) => (c.includes(key) ? c.filter((k) => k !== key) : [...c, key]));

  return (
    <View>
      <H1>Start-of-duty check</H1>
      <Muted className="mb-3">Compliance is checked here, before dispatch, not at the gate.</Muted>

      {profile.blockedReason ? <Banner tone="danger" icon="ban" title="Duty is blocked" message={profile.blockedReason} /> : null}

      <SectionTitle title="Your documents" />
      <Card>
        <DocumentList documents={profile.documents} />
      </Card>

      <SectionTitle title="Vehicle" />
      {vehicles.isPending ? (
        <LoadingState />
      ) : vehicles.isError ? (
        <ErrorState error={vehicles.error} onRetry={() => vehicles.refetch()} />
      ) : (
        vehicles.data.map((v) => <VehicleOption key={v.id} v={v} selected={v.id === vehicleId} onPress={() => choose(v)} />)
      )}

      {selected ? (
        <>
          <SectionTitle title={`Vehicle check (${checked.length} of ${CHECKS.length})`} />
          <Card className="p-2">
            {CHECKS.map((c) => {
              const on = checked.includes(c.key);
              return (
                <Pressable
                  key={c.key}
                  onPress={() => toggle(c.key)}
                  accessibilityRole="checkbox"
                  accessibilityState={{ checked: on }}
                  className="min-h-[56px] flex-row items-center rounded-xl px-3 active:bg-mist"
                >
                  <Ionicons name={on ? 'checkbox' : 'square-outline'} size={28} color={on ? colors.teal : colors.ink3} />
                  <Text className="ml-3 text-lg text-ink">{c.label}</Text>
                </Pressable>
              );
            })}
          </Card>

          <SectionTitle title="Odometer" />
          <Field
            label="Opening reading (km)"
            value={odometer}
            onChangeText={(t) => setOdometer(t.replace(/\D/g, ''))}
            keyboardType="number-pad"
            error={odometerError}
            hint={`Last recorded ${selected.odometerKm.toLocaleString('en-IN')} km. This starts today's distance log.`}
          />
        </>
      ) : null}

      {start.isError ? <ErrorBanner error={start.error} /> : null}
      <Button
        label="Sign on for duty"
        icon="play-circle"
        className="mt-2"
        disabled={!ready}
        loading={start.isPending}
        onPress={() =>
          selected && start.mutate({ vehicleId: selected.id, startOdometer: odometerValue, checklist: checked })
        }
      />
      {!ready && selected ? (
        <Muted className="mt-2 text-center">
          {checked.length < CHECKS.length ? 'Tick every check to continue.' : !odometer ? 'Enter the odometer reading.' : ''}
        </Muted>
      ) : null}
    </View>
  );
}

function VehicleOption({ v, selected, onPress }: { v: VehicleOptionDto; selected: boolean; onPress: () => void }) {
  const warn = v.documents.find((d) => d.state === 'EXPIRING');
  return (
    <Pressable
      onPress={v.available ? onPress : undefined}
      accessibilityRole="radio"
      accessibilityState={{ selected, disabled: !v.available }}
      className={`mb-2 rounded-card border-2 bg-white p-4 ${selected ? 'border-accent' : 'border-line'} ${
        v.available ? '' : 'opacity-60'
      }`}
    >
      <Row className="justify-between">
        <View className="flex-1">
          <Text className="text-xl font-bold text-ink">
            {v.code} <Text className="text-base font-normal text-ink-3">· {v.registrationNo}</Text>
          </Text>
          <Muted>
            {v.vehicleType === 'SHUTTLE' ? 'Shuttle' : 'Cab'} · {v.seatCapacity} seats · {v.vendorName}
            {v.currentStopName ? ` · at ${v.currentStopName}` : ''}
          </Muted>
        </View>
        <Ionicons
          name={!v.available ? 'ban' : selected ? 'radio-button-on' : 'radio-button-off'}
          size={26}
          color={!v.available ? colors.danger : selected ? colors.accent : colors.ink3}
        />
      </Row>
      {v.blockedReason ? <Text className="mt-2 text-sm font-semibold text-danger">{v.blockedReason}</Text> : null}
      {!v.blockedReason && warn ? (
        <Text className="mt-2 text-sm font-semibold text-amber">
          {warn.document} {warn.label}
        </Text>
      ) : null}
    </Pressable>
  );
}
