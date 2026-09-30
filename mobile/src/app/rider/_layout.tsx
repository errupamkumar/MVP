import { Stack } from 'expo-router';

import { RoleGate } from '@/components/RoleGate';
import { BookingDraftProvider } from '@/features/booking/BookingDraft';
import { colors } from '@/lib/theme';

export default function RiderLayout() {
  return (
    <RoleGate roles={['EMPLOYEE']}>
      <BookingDraftProvider>
        <Stack
          screenOptions={{
            headerTintColor: colors.ink,
            headerTitleStyle: { fontWeight: '700' },
            headerShadowVisible: false,
            headerStyle: { backgroundColor: colors.mist },
            contentStyle: { backgroundColor: colors.mist },
          }}
        >
          <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
          <Stack.Screen name="book" options={{ title: 'Book a ride' }} />
          <Stack.Screen name="options" options={{ title: 'Choose your ride' }} />
          <Stack.Screen name="booking/[id]" options={{ title: 'Your ride' }} />
        </Stack>
      </BookingDraftProvider>
    </RoleGate>
  );
}
