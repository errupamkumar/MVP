import { Ionicons } from '@expo/vector-icons';
import { useFocusEffect, useRouter } from 'expo-router';
import { ComponentProps, useCallback } from 'react';
import { Text, View } from 'react-native';

import { Card, EmptyState, ErrorState, H1, LoadingState, Muted, Row, Screen } from '@/components/ui';
import { useMarkNotificationsRead, useNotifications } from '@/hooks/queries';
import { dayAndTime } from '@/lib/format';
import { colors } from '@/lib/theme';

const ICONS: Record<string, ComponentProps<typeof Ionicons>['name']> = {
  BOOKING_CONFIRMED: 'checkmark-circle',
  BOOKING_WAITING: 'hourglass',
  CAB_ARRIVING: 'car',
  TRIP_STARTED: 'navigate',
  TRIP_COMPLETED: 'flag',
  BOOKING_CANCELLED: 'close-circle',
  NO_SHOW: 'alert-circle',
  REMATCHED: 'swap-horizontal',
  APPROVAL: 'shield-checkmark',
  SCHEDULED: 'calendar',
  WELCOME: 'hand-left',
};

export default function AlertsScreen() {
  const router = useRouter();
  const notifications = useNotifications();
  const { mutate: markAllRead } = useMarkNotificationsRead();
  const hasUnread = notifications.data?.some((n) => !n.read) ?? false;

  // Opening the tab counts as reading; mark after a short delay so the unread dots are seen first.
  useFocusEffect(
    useCallback(() => {
      if (!hasUnread) return;
      const t = setTimeout(() => markAllRead(), 1500);
      return () => clearTimeout(t);
    }, [hasUnread, markAllRead]),
  );

  return (
    <Screen refreshing={notifications.isRefetching} onRefresh={() => notifications.refetch()}>
      <H1 className="mb-1">Alerts</H1>
      <Muted className="mb-4">Confirmations, arrivals, trip start and end, and charges, as they happen.</Muted>
      {notifications.isPending ? (
        <LoadingState />
      ) : notifications.isError ? (
        <ErrorState error={notifications.error} onRetry={() => notifications.refetch()} />
      ) : notifications.data.length === 0 ? (
        <EmptyState icon="notifications-off-outline" title="No alerts yet" message="You will be told here when your cab is assigned and arriving." />
      ) : (
        notifications.data.map((n) => (
          <Card
            key={n.id}
            className={`mb-2 ${n.read ? '' : 'border-accent'}`}
            onPress={n.bookingId ? () => router.push(`/rider/booking/${n.bookingId}`) : undefined}
          >
            <Row className="items-start">
              <View className="h-9 w-9 items-center justify-center rounded-full bg-accent-soft">
                <Ionicons name={ICONS[n.category] ?? 'notifications'} size={18} color={colors.accent} />
              </View>
              <View className="ml-3 flex-1">
                <Row className="justify-between">
                  <Text className="flex-1 text-base font-bold text-ink">{n.title}</Text>
                  {!n.read ? <View className="ml-2 h-2.5 w-2.5 rounded-full bg-accent" /> : null}
                </Row>
                <Text className="mt-0.5 text-sm text-ink-2">{n.body}</Text>
                <Muted className="mt-1 text-xs">{dayAndTime(n.createdAt)}</Muted>
              </View>
            </Row>
          </Card>
        ))
      )}
    </Screen>
  );
}
