import { useRouter } from 'expo-router';
import { useState } from 'react';
import { Text, View } from 'react-native';

import type { DutySummaryDto } from '@/api/types';
import { useAuth } from '@/auth/AuthContext';
import { DocumentList } from '@/components/DocumentList';
import {
  Banner,
  Button,
  Card,
  ErrorBanner,
  ErrorState,
  Field,
  H1,
  KeyValue,
  LoadingState,
  Muted,
  Row,
  Screen,
  SectionTitle,
  StatTile,
} from '@/components/ui';
import { useDriverProfile, useDriverQueue, useEndDuty } from '@/hooks/queries';
import { confirmAction } from '@/lib/dialogs';
import { hoursMinutes, km, time } from '@/lib/format';

export default function DutyScreen() {
  const router = useRouter();
  const { signOut } = useAuth();
  const profile = useDriverProfile();
  const onDuty = !!profile.data?.duty;
  const queue = useDriverQueue(onDuty);
  const endDuty = useEndDuty();
  const [odometer, setOdometer] = useState('');
  const [fuel, setFuel] = useState('');
  const [summary, setSummary] = useState<DutySummaryDto | null>(null);

  if (profile.isPending) {
    return (
      <Screen>
        <LoadingState />
      </Screen>
    );
  }
  if (profile.isError) {
    return (
      <Screen>
        <ErrorState error={profile.error} onRetry={() => profile.refetch()} />
      </Screen>
    );
  }

  const p = profile.data;
  const duty = p.duty;
  const odometerValue = Number(odometer || 0);
  const odometerError = duty && odometer && odometerValue < duty.startOdometer ? `Below the opening ${duty.startOdometer} km` : undefined;
  const tripOpen = !!queue.data?.trip;

  const signOff = async () => {
    if (!duty || !odometer || odometerError) return;
    if (await confirmAction('Sign off duty?', `${duty.vehicle.code} closes at ${odometerValue} km.`, 'Sign off')) {
      endDuty.mutate(
        { endOdometer: odometerValue, fuelLitres: fuel ? Number(fuel) : null },
        {
          onSuccess: (s) => {
            setSummary(s);
            setOdometer('');
            setFuel('');
          },
        },
      );
    }
  };

  const onSignOut = async () => {
    if (await confirmAction('Sign out of the app?', 'Sign off duty first if your shift is over.', 'Sign out')) {
      await signOut();
      router.replace('/login');
    }
  };

  return (
    <Screen refreshing={profile.isRefetching} onRefresh={() => profile.refetch()}>
      <H1>{p.fullName}</H1>
      <Muted>
        {p.driverCode} · {p.vendorName} · gates: {p.gatesAllowed}
      </Muted>

      {summary ? (
        <>
          <SectionTitle title="Duty closed" />
          <Card className="bg-teal-soft">
            <Text className="text-lg font-bold text-ink">
              {summary.vehicleCode} · {time(summary.startedAt)} – {time(summary.endedAt)}
            </Text>
            <Row className="mt-3 flex-wrap">
              <StatTile value={String(summary.tripsCompleted)} label="Trips completed" />
              <StatTile value={km(summary.gpsKm)} label="GPS distance" />
              <StatTile value={String(summary.ridersCarried)} label="Riders carried" />
              <StatTile value={hoursMinutes(summary.dutyMinutes)} label="Duty hours" />
            </Row>
            <Banner
              tone={summary.odometerCheck === 'MATCHES' ? 'teal' : 'amber'}
              icon={summary.odometerCheck === 'MATCHES' ? 'checkmark-circle' : 'alert-circle'}
              title={summary.odometerCheck === 'MATCHES' ? 'GPS km matches the odometer' : 'Odometer needs a review'}
              message={`Odometer ${summary.odometerKm} km vs GPS ${summary.gpsKm} km on trips.`}
            />
            <Muted>This is where the vendor bill and the fuel log come from.</Muted>
          </Card>
        </>
      ) : null}

      <SectionTitle title="Documents" />
      <Card>
        <DocumentList documents={p.documents} />
      </Card>

      {duty ? (
        <>
          <SectionTitle title="Current duty" />
          <Card>
            <KeyValue label="Vehicle" value={`${duty.vehicle.code} · ${duty.vehicle.registrationNo}`} strong />
            <KeyValue label="Since" value={time(duty.startedAt)} />
            <KeyValue label="Duty hours" value={`${hoursMinutes(duty.minutesOnDuty)} of ${hoursMinutes(duty.maxDutyMinutes)}`} />
            <KeyValue label="Trips completed" value={String(duty.tripsCompleted)} />
            <KeyValue label="Riders carried" value={String(duty.ridersCarried)} />
            <KeyValue label="Distance" value={km(duty.distanceKm)} />
            <KeyValue label="Opening odometer" value={`${duty.startOdometer} km`} />
          </Card>

          <SectionTitle title="Close duty" />
          {tripOpen ? (
            <Banner tone="amber" icon="car" title="Finish your trip first" message="Drop every rider before signing off." />
          ) : null}
          <Card>
            <Field
              label="Closing odometer (km)"
              value={odometer}
              onChangeText={(t) => setOdometer(t.replace(/\D/g, ''))}
              keyboardType="number-pad"
              error={odometerError}
            />
            <Field
              label="Fuel drawn today (litres, optional)"
              value={fuel}
              onChangeText={(t) => setFuel(t.replace(/[^0-9.]/g, ''))}
              keyboardType="decimal-pad"
            />
            {endDuty.isError ? <ErrorBanner error={endDuty.error} /> : null}
            <Button
              label="Sign off duty"
              icon="stop-circle"
              variant="dark"
              onPress={signOff}
              disabled={!odometer || !!odometerError || tripOpen}
              loading={endDuty.isPending}
            />
          </Card>
        </>
      ) : (
        <View className="mt-4">
          <Muted>You are off duty. Sign on from the Trips tab.</Muted>
        </View>
      )}

      <Button label="Sign out of the app" variant="secondary" icon="log-out" onPress={onSignOut} className="mt-6" />
    </Screen>
  );
}
