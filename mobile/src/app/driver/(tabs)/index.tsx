import { View } from 'react-native';

import { Button, ErrorState, LoadingState, Screen } from '@/components/ui';
import { SignOnPanel } from '@/features/driver/SignOnPanel';
import { TripQueuePanel } from '@/features/driver/TripQueuePanel';
import { useDriverProfile, useDriverQueue, useDriverSos } from '@/hooks/queries';
import { useDriverLocation } from '@/hooks/useDriverLocation';
import { confirmAction, showMessage } from '@/lib/dialogs';

export default function DriverHome() {
  const profile = useDriverProfile();
  const onDuty = !!profile.data?.duty;
  const queue = useDriverQueue(onDuty);
  const location = useDriverLocation(onDuty);
  const sos = useDriverSos();

  const onSos = async () => {
    if (await confirmAction('Send SOS?', 'The transport desk and plant security get your cab and location now.', 'Send SOS', true)) {
      sos.mutate(undefined, {
        onSuccess: (r) => showMessage('SOS sent', r.message),
        onError: (e) => showMessage('SOS not sent', e.message),
      });
    }
  };

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

  return (
    <Screen
      refreshing={profile.isRefetching || queue.isRefetching}
      onRefresh={() => {
        void profile.refetch();
        if (onDuty) void queue.refetch();
      }}
      footer={onDuty ? <Button label="SOS" variant="danger" icon="warning" onPress={onSos} loading={sos.isPending} /> : undefined}
    >
      {!onDuty ? (
        <SignOnPanel profile={profile.data} />
      ) : queue.isPending ? (
        <LoadingState label="Loading your trip queue…" />
      ) : queue.isError ? (
        <ErrorState error={queue.error} onRetry={() => queue.refetch()} />
      ) : (
        <View>
          <TripQueuePanel
            trip={queue.data.trip}
            notice={queue.data.notice}
            profile={profile.data}
            location={location}
            fetchedAt={queue.dataUpdatedAt}
          />
        </View>
      )}
    </Screen>
  );
}
