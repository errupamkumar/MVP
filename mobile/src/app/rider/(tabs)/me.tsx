import { useRouter } from 'expo-router';
import { View } from 'react-native';

import { API_BASE_URL } from '@/api/client';
import { useAuth } from '@/auth/AuthContext';
import { Avatar, Banner, Button, Card, ErrorState, H1, KeyValue, LoadingState, Muted, Row, Screen, SectionTitle } from '@/components/ui';
import { useRiderProfile } from '@/hooks/queries';
import { confirmAction } from '@/lib/dialogs';
import { initials } from '@/lib/format';

export default function MeScreen() {
  const router = useRouter();
  const { signOut } = useAuth();
  const profile = useRiderProfile();

  const onSignOut = async () => {
    if (await confirmAction('Sign out?', 'You will need your P. No. and password to sign in again.', 'Sign out')) {
      await signOut();
      router.replace('/login');
    }
  };

  return (
    <Screen refreshing={profile.isRefetching} onRefresh={() => profile.refetch()}>
      {profile.isPending ? (
        <LoadingState />
      ) : profile.isError ? (
        <ErrorState error={profile.error} onRetry={() => profile.refetch()} />
      ) : (
        <>
          <Row className="mb-4">
            <Avatar label={initials(profile.data.fullName)} tone="accent" />
            <View className="ml-3 flex-1">
              <H1>{profile.data.fullName}</H1>
              <Muted>
                {profile.data.personnelNo} · {profile.data.department}
              </Muted>
            </View>
          </Row>
          {profile.data.bookingPaused ? (
            <Banner tone="danger" icon="pause-circle" title="Booking is paused" message="Contact the transport desk to restore it." />
          ) : null}
          <Card>
            <KeyValue label="P. No." value={profile.data.personnelNo} />
            <KeyValue label="Email" value={profile.data.email ?? '—'} />
            <KeyValue label="Contact" value={profile.data.phone ?? '—'} />
            <KeyValue label="Grade" value={profile.data.grade} />
            <KeyValue label="Cost centre" value={`${profile.data.costCentreCode} · ${profile.data.costCentreName}`} strong />
            <KeyValue label="Exclusive rides" value={profile.data.exclusiveEligible ? 'Released by grade rule' : 'Need approval'} />
            <KeyValue label="No-shows (30 days)" value={`${profile.data.noShowsInWindow} of 3 allowed`} />
          </Card>
          <Muted className="mt-2">These details come from HRMS. Ask HR to correct anything that is wrong.</Muted>

          <SectionTitle title="Privacy" />
          <Card>
            <Muted>
              Plant Ride tracks vehicles, not people. Your ride history is kept for the retention period LHS sets and is used for
              billing and safety only.
            </Muted>
          </Card>

          <Button label="Sign out" variant="secondary" icon="log-out" onPress={onSignOut} className="mt-6" />
          <Muted className="mt-4 text-center text-xs">Server {API_BASE_URL}</Muted>
        </>
      )}
    </Screen>
  );
}
