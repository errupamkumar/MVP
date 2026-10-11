import { Loading } from '@ecoride/mobile';
import { Redirect } from 'expo-router';

import { auth } from '@/lib/api';

export default function Index() {
  const { ready, session } = auth.useAuth();
  if (!ready) return <Loading />;
  return <Redirect href={session ? '/home' : '/welcome'} />;
}
