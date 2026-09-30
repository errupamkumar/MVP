import { useRouter } from 'expo-router';
import { Text, View } from 'react-native';

import { useAuth } from '@/auth/AuthContext';
import { confirmAction } from '@/lib/dialogs';
import { dayAndTime } from '@/lib/format';

import { IconButton, Muted, Row } from './ui';

export function DeskHeader({ title, subtitle, updatedAt }: { title: string; subtitle?: string; updatedAt?: string }) {
  const { user, signOut } = useAuth();
  const router = useRouter();
  return (
    <Row className="mb-4 items-start justify-between">
      <View className="flex-1 pr-3">
        <Text className="text-2xl font-bold text-ink">{title}</Text>
        <Muted>
          {subtitle ?? ''}
          {updatedAt ? `${subtitle ? ' · ' : ''}updated ${dayAndTime(updatedAt)}` : ''}
        </Muted>
      </View>
      <Row>
        <View className="mr-3 items-end">
          <Text className="text-sm font-bold text-ink">{user?.fullName}</Text>
          <Muted className="text-xs">{user?.role === 'ADMIN' ? 'Fleet admin & desk' : 'Transport desk'}</Muted>
        </View>
        <IconButton
          icon="log-out-outline"
          label="Sign out"
          onPress={async () => {
            if (await confirmAction('Sign out?', 'You can sign back in any time.', 'Sign out')) {
              await signOut();
              router.replace('/login');
            }
          }}
        />
      </Row>
    </Row>
  );
}
