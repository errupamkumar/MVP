import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { useEffect, useMemo, useState } from 'react';
import { Pressable, Switch, Text, View } from 'react-native';

import { PickerModal } from '@/components/PickerModal';
import { Banner, Button, Card, Chip, ErrorBanner, Field, Muted, Row, Screen, SectionTitle, Stepper } from '@/components/ui';
import { StopChoice, useBookingDraft, WhenChoice } from '@/features/booking/BookingDraft';
import { useDestinations, useRiderProfile, useStops } from '@/hooks/queries';
import { plantIsoAt, plantIsoIn } from '@/lib/format';
import { colors } from '@/lib/theme';

const PHONE = /^\+?[0-9 ()-]{7,20}$/;
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function whenChoices(): WhenChoice[] {
  return [
    { kind: 'now' },
    { kind: 'later', label: 'In 30 min', iso: plantIsoIn(30) },
    { kind: 'later', label: 'In 1 hour', iso: plantIsoIn(60) },
    { kind: 'later', label: 'Tomorrow 08:00', iso: plantIsoAt(1, 8, 0) },
    { kind: 'later', label: 'Tomorrow 17:30', iso: plantIsoAt(1, 17, 30) },
  ];
}

export default function BookScreen() {
  const router = useRouter();
  const { draft, update } = useBookingDraft();
  const profile = useRiderProfile();
  const stops = useStops();
  const destinations = useDestinations(draft.from?.id ?? null);
  const [picker, setPicker] = useState<'from' | 'to' | null>(null);
  const [touched, setTouched] = useState(false);
  const choices = useMemo(whenChoices, []);

  // Pre-fill rider details from the HR record the first time (still editable).
  useEffect(() => {
    if (profile.data && !draft.riderName && !draft.forGuest) {
      update({
        riderName: profile.data.fullName,
        riderPhone: profile.data.phone ?? '',
        riderEmail: profile.data.email ?? '',
      });
    }
  }, [profile.data, draft.riderName, draft.forGuest, update]);

  // Changing the pickup can make the chosen drop unreachable.
  useEffect(() => {
    if (draft.to && destinations.data && !destinations.data.some((d) => d.stopId === draft.to?.id)) {
      update({ to: null });
    }
  }, [destinations.data, draft.to, update]);

  const errors = {
    from: !draft.from ? 'Choose where you are' : undefined,
    to: !draft.to ? 'Choose where you are going' : undefined,
    riderName: !draft.riderName.trim() ? "Enter the rider's name" : undefined,
    riderPhone: !PHONE.test(draft.riderPhone.trim()) ? 'Enter a phone number the driver can call' : undefined,
    riderEmail: draft.riderEmail.trim() && !EMAIL.test(draft.riderEmail.trim()) ? 'Enter a valid email' : undefined,
  };
  const valid = !Object.values(errors).some(Boolean);
  const selectedDestination = destinations.data?.find((d) => d.stopId === draft.to?.id);

  const pick = (which: 'from' | 'to', choice: StopChoice) => {
    update(which === 'from' ? { from: choice } : { to: choice });
    setPicker(null);
  };

  const next = () => {
    setTouched(true);
    if (valid) router.push('/rider/options');
  };

  const toggleGuest = (forGuest: boolean) => {
    const p = profile.data;
    update(
      forGuest
        ? { forGuest, riderName: '', riderPhone: '', riderEmail: '' }
        : { forGuest, riderName: p?.fullName ?? '', riderPhone: p?.phone ?? '', riderEmail: p?.email ?? '' },
    );
  };

  return (
    <Screen
      topInset={false}
      footer={<Button label="Show ride options" icon="car" onPress={next} disabled={touched && !valid} />}
    >
      <Card className="p-0">
        <StopRow
          icon="radio-button-on"
          label="From"
          value={draft.from?.name}
          placeholder="Pickup stop"
          error={touched ? errors.from : undefined}
          onPress={() => setPicker('from')}
        />
        <View className="ml-12 h-px bg-line" />
        <StopRow
          icon="location"
          label="To"
          value={draft.to?.name}
          placeholder={draft.from ? 'Drop stop' : 'Choose a pickup first'}
          hint={selectedDestination ? `${selectedDestination.routeCode.replace('R', 'Route ')} · ${selectedDestination.rideMinutes} min` : undefined}
          error={touched ? errors.to : undefined}
          onPress={() => draft.from && setPicker('to')}
        />
      </Card>
      {destinations.isError ? <ErrorBanner error={destinations.error} /> : null}

      <SectionTitle title="When" />
      <View className="flex-row flex-wrap">
        {choices.map((c) => (
          <Chip
            key={c.kind === 'now' ? 'now' : c.label}
            label={c.kind === 'now' ? 'Now' : c.label}
            icon={c.kind === 'now' ? 'flash' : 'time'}
            selected={c.kind === 'now' ? draft.when.kind === 'now' : draft.when.kind === 'later' && draft.when.label === c.label}
            onPress={() => update({ when: c })}
          />
        ))}
      </View>

      <SectionTitle title="Seats" />
      <Card className="flex-row items-center justify-between">
        <View className="flex-1 pr-3">
          <Text className="text-base font-semibold text-ink">Riders travelling</Text>
          <Muted>Pick-up runs carry 3 riders max; bigger groups need an exclusive cab.</Muted>
        </View>
        <Stepper value={draft.seats} min={1} max={4} onChange={(seats) => update({ seats })} />
      </Card>

      <SectionTitle title="Rider details" />
      {profile.data ? (
        <Muted className="mb-2">
          P. No. {profile.data.personnelNo} · auto-filled from HRMS. Edit anything that is out of date.
        </Muted>
      ) : null}
      <Card>
        <Row className="mb-3 justify-between">
          <View className="flex-1 pr-3">
            <Text className="text-base font-semibold text-ink">Booking for a guest</Text>
            <Muted>A visitor or colleague rides; your cost centre pays.</Muted>
          </View>
          <Switch
            value={draft.forGuest}
            onValueChange={toggleGuest}
            trackColor={{ true: colors.accent, false: colors.line }}
            thumbColor={colors.white}
            accessibilityLabel="Booking for a guest"
          />
        </Row>
        <Field
          label={draft.forGuest ? "Guest's name" : 'Employee name'}
          value={draft.riderName}
          onChangeText={(riderName) => update({ riderName })}
          error={touched ? errors.riderName : undefined}
          autoCapitalize="words"
        />
        <Field
          label="Contact number"
          value={draft.riderPhone}
          onChangeText={(riderPhone) => update({ riderPhone })}
          keyboardType="phone-pad"
          error={touched ? errors.riderPhone : undefined}
          hint="The driver calls this number if they cannot find you."
        />
        <Field
          label="Email ID (optional)"
          value={draft.riderEmail}
          onChangeText={(riderEmail) => update({ riderEmail })}
          keyboardType="email-address"
          autoCapitalize="none"
          error={touched ? errors.riderEmail : undefined}
        />
      </Card>
      {profile.data?.bookingPaused ? (
        <Banner tone="danger" icon="pause-circle" title="Booking is paused" message="Contact the transport desk to restore it." />
      ) : null}

      <PickerModal
        visible={picker === 'from'}
        title="Pickup stop"
        loading={stops.isPending}
        items={(stops.data ?? []).map((s) => ({ id: s.id, title: s.name, subtitle: `Zone ${s.zone.replace('Z', '')}` }))}
        selectedId={draft.from?.id}
        onPick={(i) => pick('from', { id: i.id, name: i.title })}
        onClose={() => setPicker(null)}
      />
      <PickerModal
        visible={picker === 'to'}
        title={`From ${draft.from?.name ?? ''} to…`}
        loading={destinations.isPending}
        items={(destinations.data ?? []).map((d) => ({
          id: d.stopId,
          title: d.stopName,
          subtitle: `${d.routeCode.replace('R', 'Route ')} · ${d.routeName} · ${d.rideMinutes} min · ${d.rideKm} km`,
        }))}
        selectedId={draft.to?.id}
        emptyMessage="No configured route runs from this stop yet."
        onPick={(i) => pick('to', { id: i.id, name: i.title })}
        onClose={() => setPicker(null)}
      />
    </Screen>
  );
}

function StopRow({
  icon,
  label,
  value,
  placeholder,
  hint,
  error,
  onPress,
}: {
  icon: 'radio-button-on' | 'location';
  label: string;
  value?: string;
  placeholder: string;
  hint?: string;
  error?: string;
  onPress: () => void;
}) {
  return (
    <Pressable onPress={onPress} accessibilityRole="button" accessibilityLabel={`${label}: ${value ?? placeholder}`} className="flex-row items-center px-4 py-3.5 active:opacity-70">
      <Ionicons name={icon} size={20} color={icon === 'location' ? colors.accent : colors.teal} />
      <View className="ml-4 flex-1">
        <Muted>{label}</Muted>
        <Text className={`text-lg font-semibold ${value ? 'text-ink' : 'text-ink-3'}`}>{value ?? placeholder}</Text>
        {hint ? <Muted>{hint}</Muted> : null}
        {error ? <Text className="text-sm text-danger">{error}</Text> : null}
      </View>
      <Ionicons name="chevron-down" size={18} color={colors.ink3} />
    </Pressable>
  );
}
