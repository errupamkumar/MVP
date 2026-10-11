import { colors, Loading } from '@ecoride/mobile';
import { Redirect, Stack } from 'expo-router';

import { auth } from '@/lib/api';

/** Everything past sign-in. */
export default function AppLayout() {
  const { ready, session } = auth.useAuth();
  if (!ready) return <Loading />;
  if (!session) return <Redirect href="/welcome" />;
  return <Stack screenOptions={{ headerShown: false, contentStyle: { backgroundColor: colors.canvas } }} />;
}
