import { Stack } from 'expo-router';

import { RoleGate } from '@/components/RoleGate';

export default function DriverLayout() {
  return (
    <RoleGate roles={['DRIVER']}>
      <Stack screenOptions={{ headerShown: false }} />
    </RoleGate>
  );
}
