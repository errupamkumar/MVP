import { Loading } from '@ecoride/mobile';
import { Redirect, Stack } from 'expo-router';

import { auth } from '@/lib/api';

/** Signed-in riders skip the welcome and sign-in screens. */
export default function AuthLayout() {
  const { ready, session } = auth.useAuth();
  if (!ready) return <Loading />;
  if (session) return <Redirect href="/home" />;
  return <Stack screenOptions={{ headerShown: false }} />;
}
