import { Redirect } from 'expo-router';
import { View } from 'react-native';

import { homePathFor, useAuth } from '@/auth/AuthContext';
import { LoadingState } from '@/components/ui';

/** Entry: send each role to its own app (rider, driver, desk). */
export default function Index() {
  const { ready, user } = useAuth();
  if (!ready) {
    return (
      <View className="flex-1 justify-center bg-mist">
        <LoadingState label="Starting Plant Ride…" />
      </View>
    );
  }
  return <Redirect href={user ? homePathFor(user.role) : '/login'} />;
}
