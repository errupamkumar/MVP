import { Ionicons } from '@expo/vector-icons';
import { Tabs } from 'expo-router';
import { useWindowDimensions } from 'react-native';

import { useAuth } from '@/auth/AuthContext';
import { RoleGate } from '@/components/RoleGate';
import { useDeskBoard } from '@/hooks/queries';
import { colors } from '@/lib/theme';

export default function DeskLayout() {
  return (
    <RoleGate roles={['DESK', 'ADMIN']}>
      <DeskTabs />
    </RoleGate>
  );
}

/** A sidebar on a desk monitor, a bottom bar on the gate tablet. Billing and setup are admin-only. */
function DeskTabs() {
  const { user } = useAuth();
  const { width } = useWindowDimensions();
  const wide = width >= 900;
  const isAdmin = user?.role === 'ADMIN';
  const board = useDeskBoard();
  const waiting = board.data?.kpis.waitingBookings ?? 0;

  return (
    <Tabs
      screenOptions={{
        headerShown: false,
        tabBarPosition: wide ? 'left' : 'bottom',
        tabBarVariant: wide ? 'material' : 'uikit',
        tabBarLabelPosition: wide ? 'beside-icon' : 'below-icon',
        tabBarActiveTintColor: colors.accent,
        tabBarInactiveTintColor: wide ? colors.line : colors.ink3,
        tabBarActiveBackgroundColor: wide ? '#22303F' : undefined,
        tabBarStyle: wide
          ? { backgroundColor: colors.night, borderRightColor: colors.night, minWidth: 210, paddingTop: 16 }
          : { borderTopColor: colors.line, minHeight: 60 },
        tabBarLabelStyle: { fontSize: 14, fontWeight: '700' },
      }}
    >
      <Tabs.Screen
        name="index"
        options={{ title: 'Live board', tabBarIcon: ({ color }) => <Ionicons name="grid" size={22} color={color} /> }}
      />
      <Tabs.Screen
        name="queue"
        options={{
          title: 'Queue',
          tabBarBadge: waiting > 0 ? waiting : undefined,
          tabBarIcon: ({ color }) => <Ionicons name="people" size={22} color={color} />,
        }}
      />
      <Tabs.Screen
        name="billing"
        options={{
          title: 'Billing',
          href: isAdmin ? undefined : null,
          tabBarIcon: ({ color }) => <Ionicons name="wallet" size={22} color={color} />,
        }}
      />
      <Tabs.Screen
        name="setup"
        options={{
          title: 'Routes & rules',
          href: isAdmin ? undefined : null,
          tabBarIcon: ({ color }) => <Ionicons name="options" size={22} color={color} />,
        }}
      />
    </Tabs>
  );
}
