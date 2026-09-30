import '../../global.css';

import { focusManager, QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import { AppState, AppStateStatus, Platform } from 'react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';

import type { ApiError } from '@/api/client';
import { AuthProvider } from '@/auth/AuthContext';
import { ErrorBoundary } from '@/components/ErrorBoundary';

function createQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 5_000,
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

/** React Query refetches on window focus on web; on phones, app foregrounding is the equivalent. */
function useAppFocus() {
  useEffect(() => {
    if (Platform.OS === 'web') return;
    const sub = AppState.addEventListener('change', (status: AppStateStatus) => {
      focusManager.setFocused(status === 'active');
    });
    return () => sub.remove();
  }, []);
}

export default function RootLayout() {
  const [queryClient] = useState(createQueryClient);
  useAppFocus();
  return (
    <SafeAreaProvider>
      <QueryClientProvider client={queryClient}>
        <AuthProvider>
          <ErrorBoundary>
            <StatusBar style="dark" />
            <Stack screenOptions={{ headerShown: false, contentStyle: { backgroundColor: '#F3F6F9' } }} />
          </ErrorBoundary>
        </AuthProvider>
      </QueryClientProvider>
    </SafeAreaProvider>
  );
}
