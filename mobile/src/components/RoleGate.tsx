import { Redirect } from 'expo-router';
import { ReactNode } from 'react';
import { View } from 'react-native';

import type { Role } from '@/api/types';
import { homePathFor, useAuth } from '@/auth/AuthContext';

import { LoadingState } from './ui';

/** Guards a whole section of the app: signed out goes to login, the wrong role goes to its own home. */
export function RoleGate({ roles, children }: { roles: Role[]; children: ReactNode }) {
  const { ready, user } = useAuth();
  if (!ready) {
    return (
      <View className="flex-1 justify-center bg-mist">
        <LoadingState />
      </View>
    );
  }
  if (!user) {
    return <Redirect href="/login" />;
  }
  if (!roles.includes(user.role)) {
    return <Redirect href={homePathFor(user.role)} />;
  }
  return <>{children}</>;
}
