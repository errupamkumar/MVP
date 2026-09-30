import { useRouter } from 'expo-router';

import { Button, EmptyState, Screen } from '@/components/ui';

export default function NotFound() {
  const router = useRouter();
  return (
    <Screen>
      <EmptyState
        icon="map"
        title="This page does not exist"
        message="The link may be old. Head back to your home screen."
        action={<Button label="Go home" onPress={() => router.replace('/')} />}
      />
    </Screen>
  );
}
