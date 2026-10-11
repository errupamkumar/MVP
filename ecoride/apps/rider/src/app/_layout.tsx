import type { ApiError } from '@ecoride/core';
import { colors } from '@ecoride/mobile';
import { focusManager, QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import { AppState, Platform } from 'react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';

import { auth } from '@/lib/api';
import { BookingProvider } from '@/lib/booking';

function createQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 3000,
        // Retry network blips and 5xx; a 4xx answer will not change on retry.
        retry: (count, error) => {
          const e = error as unknown as ApiError;
          return count < 2 && (e?.status === 0 || (e?.status ?? 0) >= 500);
        },
      },
      mutations: { retry: false },
    },
  });
}

export default function RootLayout() {
  const [queryClient] = useState(createQueryClient);
  useEffect(() => {
    if (Platform.OS === 'web') return;
    const sub = AppState.addEventListener('change', (s) => focusManager.setFocused(s === 'active'));
    return () => sub.remove();
  }, []);
  return (
    <SafeAreaProvider>
      <QueryClientProvider client={queryClient}>
        <auth.AuthProvider>
          <BookingProvider>
            <StatusBar style="dark" />
            <Stack screenOptions={{ headerShown: false, contentStyle: { backgroundColor: colors.canvas } }} />
          </BookingProvider>
        </auth.AuthProvider>
      </QueryClientProvider>
    </SafeAreaProvider>
  );
}
