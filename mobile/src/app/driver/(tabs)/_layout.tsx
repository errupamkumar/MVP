import { Ionicons } from '@expo/vector-icons';
import { Tabs } from 'expo-router';

import { colors } from '@/lib/theme';

export default function DriverTabs() {
  return (
    <Tabs
      screenOptions={{
        headerShown: false,
        tabBarActiveTintColor: colors.accent,
        tabBarInactiveTintColor: colors.ink3,
        tabBarStyle: { minHeight: 64, borderTopColor: colors.line },
        tabBarLabelStyle: { fontSize: 13, fontWeight: '700' },
      }}
    >
      <Tabs.Screen
        name="index"
        options={{ title: 'Trips', tabBarIcon: ({ color }) => <Ionicons name="navigate" color={color} size={26} /> }}
      />
      <Tabs.Screen
        name="issues"
        options={{ title: 'Issues', tabBarIcon: ({ color }) => <Ionicons name="construct" color={color} size={26} /> }}
      />
      <Tabs.Screen
        name="duty"
        options={{ title: 'Duty', tabBarIcon: ({ color }) => <Ionicons name="id-card" color={color} size={26} /> }}
      />
    </Tabs>
  );
}
