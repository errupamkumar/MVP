import { useState } from 'react';
import { View } from 'react-native';

import type { IssueType } from '@/api/types';
import { Banner, Button, Card, Chip, EmptyState, ErrorBanner, Field, H1, LoadingState, Muted, Screen, SectionTitle } from '@/components/ui';
import { useDriverProfile, useReportIssue } from '@/hooks/queries';

const TYPES: { type: IssueType; label: string; icon: 'car' | 'medkit' | 'time' | 'water' | 'chatbubble'; hint: string }[] = [
  { type: 'BREAKDOWN', label: 'Breakdown', icon: 'car', hint: 'The cab goes off-road and your riders are moved to another cab.' },
  { type: 'ACCIDENT', label: 'Accident', icon: 'medkit', hint: 'Raised as critical to the desk and plant security.' },
  { type: 'DELAY', label: 'Delay', icon: 'time', hint: 'Your riders are told the cab is running late.' },
  { type: 'FUEL', label: 'Fuel entry', icon: 'water', hint: 'Adds to the fuel log for this duty.' },
  { type: 'OTHER', label: 'Other', icon: 'chatbubble', hint: 'Anything else the desk should know.' },
];

export default function IssuesScreen() {
  const profile = useDriverProfile();
  const report = useReportIssue();
  const [type, setType] = useState<IssueType>('BREAKDOWN');
  const [description, setDescription] = useState('');
  const [litres, setLitres] = useState('');
  const [touched, setTouched] = useState(false);

  if (profile.isPending) {
    return (
      <Screen>
        <LoadingState />
      </Screen>
    );
  }
  if (!profile.data?.duty) {
    return (
      <Screen>
        <H1 className="mb-2">Report an issue</H1>
        <EmptyState icon="id-card-outline" title="Sign on first" message="Issues are reported against the vehicle you are on duty with." />
      </Screen>
    );
  }

  const selected = TYPES.find((t) => t.type === type)!;
  const litresValue = litres ? Number(litres) : null;
  const errors = {
    description: !description.trim() ? 'Say what happened' : undefined,
    litres: type === 'FUEL' && (!litresValue || litresValue <= 0 || litresValue > 300) ? 'Enter the litres drawn' : undefined,
  };

  const send = () => {
    setTouched(true);
    if (errors.description || errors.litres) return;
    report.mutate(
      { type, description: description.trim(), litres: type === 'FUEL' ? litresValue : null },
      {
        onSuccess: () => {
          setDescription('');
          setLitres('');
          setTouched(false);
        },
      },
    );
  };

  return (
    <Screen footer={<Button label="Send to transport desk" icon="send" onPress={send} loading={report.isPending} variant={type === 'BREAKDOWN' || type === 'ACCIDENT' ? 'danger' : 'primary'} />}>
      <H1>Report an issue</H1>
      <Muted className="mb-3">
        {profile.data.duty.vehicle.code} · {profile.data.duty.vehicle.registrationNo}. The desk sees it instantly.
      </Muted>

      {report.isSuccess ? (
        <Banner tone="teal" icon="checkmark-circle" title="Sent" message={report.data.message} />
      ) : null}
      {report.isError ? <ErrorBanner error={report.error} /> : null}

      <SectionTitle title="What happened" />
      <View className="flex-row flex-wrap">
        {TYPES.map((t) => (
          <Chip key={t.type} label={t.label} icon={t.icon} selected={type === t.type} onPress={() => setType(t.type)} />
        ))}
      </View>
      <Muted className="mb-3">{selected.hint}</Muted>

      <Card>
        {type === 'FUEL' ? (
          <Field
            label="Litres drawn"
            value={litres}
            onChangeText={(t) => setLitres(t.replace(/[^0-9.]/g, ''))}
            keyboardType="decimal-pad"
            error={touched ? errors.litres : undefined}
          />
        ) : null}
        <Field
          label="Details"
          value={description}
          onChangeText={setDescription}
          placeholder={type === 'FUEL' ? 'Pump, slip number' : 'e.g. Rear right tyre puncture near Coke Oven Road'}
          multiline
          maxLength={400}
          style={{ minHeight: 100, textAlignVertical: 'top', paddingTop: 12 }}
          error={touched ? errors.description : undefined}
        />
      </Card>
    </Screen>
  );
}
