import { Ionicons } from '@expo/vector-icons';
import { Redirect, useRouter } from 'expo-router';
import { ComponentProps, useEffect, useMemo, useRef, useState } from 'react';
import { Pressable, Text, View } from 'react-native';

import type { RideOptionDto, RideOptionsRequest, RideType } from '@/api/types';
import {
  Banner,
  Button,
  Card,
  ErrorBanner,
  ErrorState,
  Eyebrow,
  KeyValue,
  LoadingState,
  Muted,
  Row,
  Screen,
  SectionTitle,
} from '@/components/ui';
import { useBookingDraft } from '@/features/booking/BookingDraft';
import { useCreateBooking, useRideOptions } from '@/hooks/queries';
import { dayAndTime, km, minutes } from '@/lib/format';
import { uuid } from '@/lib/ids';
import { colors } from '@/lib/theme';

const ICONS: Record<RideOptionDto['type'], ComponentProps<typeof Ionicons>['name']> = {
  SHUTTLE: 'bus',
  SHARED: 'people',
  EXCLUSIVE: 'car-sport',
};

export default function RideOptionsScreen() {
  const router = useRouter();
  const { draft, reset } = useBookingDraft();
  const [selected, setSelected] = useState<RideType | null>(null);
  const create = useCreateBooking();

  const scheduledAt = draft.when.kind === 'later' ? draft.when.iso : null;
  const request = useMemo<RideOptionsRequest | null>(
    () => (draft.from && draft.to ? { fromStopId: draft.from.id, toStopId: draft.to.id, seats: draft.seats, scheduledAt } : null),
    [draft.from, draft.to, draft.seats, scheduledAt],
  );
  const options = useRideOptions(request);

  // One Idempotency-Key per booking attempt: a double tap or a retried request
  // returns the same booking instead of creating two. A new choice = a new attempt.
  const attemptKey = useRef(uuid());
  useEffect(() => {
    attemptKey.current = uuid();
  }, [request, selected]);

  // Pick the best bookable option by default (shared when available).
  useEffect(() => {
    if (!selected && options.data) {
      const shared = options.data.options.find((o) => o.type === 'SHARED' && o.bookable);
      if (shared) setSelected('SHARED');
    }
  }, [options.data, selected]);

  if (!request) {
    return <Redirect href="/rider/book" />;
  }

  const confirm = () => {
    if (!selected || !draft.from || !draft.to) return;
    create.mutate(
      {
        idempotencyKey: attemptKey.current,
        body: {
          fromStopId: draft.from.id,
          toStopId: draft.to.id,
          rideType: selected,
          seats: draft.seats,
          scheduledAt,
          riderName: draft.riderName.trim(),
          riderPhone: draft.riderPhone.trim(),
          riderEmail: draft.riderEmail.trim() || null,
          forGuest: draft.forGuest,
        },
      },
      {
        onSuccess: (booking) => {
          reset();
          router.dismissAll();
          router.push(`/rider/booking/${booking.id}`);
        },
      },
    );
  };

  const chosen = options.data?.options.find((o) => o.type === selected);
  const confirmLabel =
    chosen?.type === 'EXCLUSIVE' && chosen.requiresApproval ? 'Send for approval' : scheduledAt ? 'Schedule ride' : 'Confirm ride';

  return (
    <Screen
      topInset={false}
      refreshing={options.isRefetching}
      onRefresh={() => options.refetch()}
      footer={
        options.data ? (
          <Button label={confirmLabel} icon="checkmark-circle" onPress={confirm} loading={create.isPending} disabled={!selected} />
        ) : null
      }
    >
      {options.isPending ? (
        <LoadingState label="Checking cabs, seats and ETAs…" />
      ) : options.isError ? (
        <ErrorState error={options.error} onRetry={() => options.refetch()} />
      ) : (
        <>
          <Card className="bg-ink">
            <Eyebrow className="text-line">{options.data.route.label}</Eyebrow>
            <Text className="mt-1 text-xl font-bold text-white">
              {options.data.from.name} → {options.data.to.name}
            </Text>
            <Text className="mt-1 text-sm text-line">
              {km(options.data.rideKm)} · about {minutes(options.data.rideMinutes)} ·{' '}
              {scheduledAt ? `pickup ${dayAndTime(scheduledAt)}` : 'pickup now'} · {draft.seats}{' '}
              {draft.seats === 1 ? 'seat' : 'seats'}
            </Text>
          </Card>

          <SectionTitle title="Ride options" />
          {options.data.options.map((o) => (
            <OptionCard
              key={o.type}
              option={o}
              selected={selected === o.type}
              onPress={o.bookable ? () => setSelected(o.type as RideType) : undefined}
            />
          ))}

          <Card className="mt-2 flex-row items-center bg-accent-soft">
            <Ionicons name="people-circle" size={26} color={colors.accent} />
            <View className="ml-3 flex-1">
              <Text className="text-base font-bold text-ink">
                Pick-up runs carry {options.data.occupancyCap} riders max
              </Text>
              <Muted>A full cab is never offered a fourth rider; you get the next cab and its ETA instead.</Muted>
            </View>
          </Card>

          <SectionTitle title="Charged to" />
          <Card>
            <KeyValue label="Cost centre" value={`${options.data.costCentreCode} · ${options.data.costCentreName}`} strong />
            <KeyValue label="Rider" value={draft.forGuest ? `${draft.riderName} (guest)` : draft.riderName} />
            <KeyValue label="Contact" value={draft.riderPhone} />
          </Card>

          {create.isError ? <View className="mt-3"><ErrorBanner error={create.error} /></View> : null}
          {chosen?.note ? <View className="mt-3"><Banner tone="amber" icon="information-circle" title={chosen.note} /></View> : null}
        </>
      )}
    </Screen>
  );
}

function OptionCard({ option, selected, onPress }: { option: RideOptionDto; selected: boolean; onPress?: () => void }) {
  const info = !option.bookable;
  return (
    <Pressable
      onPress={onPress}
      disabled={!onPress}
      accessibilityRole={onPress ? 'radio' : 'text'}
      accessibilityState={{ selected, disabled: !onPress }}
      className={`mb-2 flex-row items-center rounded-card border-2 bg-white p-4 ${
        selected ? 'border-accent' : 'border-line'
      } ${info && option.type !== 'SHUTTLE' ? 'opacity-60' : ''}`}
    >
      <View className={`h-12 w-12 items-center justify-center rounded-full ${option.type === 'SHUTTLE' ? 'bg-teal-soft' : 'bg-accent-soft'}`}>
        <Ionicons name={ICONS[option.type]} size={22} color={option.type === 'SHUTTLE' ? colors.teal : colors.accent} />
      </View>
      <View className="ml-3 flex-1">
        <Text className="text-base font-bold text-ink">{option.title}</Text>
        <Muted>{option.subtitle}</Muted>
        {option.type === 'SHUTTLE' ? <Text className="mt-0.5 text-xs font-semibold text-teal-deep">Information only · just walk up</Text> : null}
      </View>
      <View className="items-end">
        {option.etaMinutes != null ? (
          <>
            <Text className="text-xl font-bold text-ink">{option.etaMinutes < 1 ? 'Now' : option.etaMinutes}</Text>
            {option.etaMinutes >= 1 ? <Muted>min away</Muted> : null}
          </>
        ) : (
          <Muted>—</Muted>
        )}
        {option.bookable ? (
          <Row className="mt-1">
            <Ionicons name={selected ? 'radio-button-on' : 'radio-button-off'} size={20} color={selected ? colors.accent : colors.ink3} />
          </Row>
        ) : null}
      </View>
    </Pressable>
  );
}
