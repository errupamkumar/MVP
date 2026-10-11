import { Button, colors, Ionicons, IconName, space, T } from '@ecoride/mobile';
import { router } from 'expo-router';
import { ScrollView, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Brand } from '@/components/Brand';

const POINTS: { icon: IconName; title: string; text: string }[] = [
  { icon: 'cash', title: 'Keep at least 80% of every fare', text: 'Weekly payouts, tips go to you in full.' },
  { icon: 'time', title: 'Drive when you want', text: 'Go online and offline with one tap.' },
  { icon: 'navigate', title: 'Requests near you', text: 'See the pickup, the trip and what you earn before you accept.' },
];

export default function Welcome() {
  const insets = useSafeAreaInsets();
  return (
    <View style={{ flex: 1, backgroundColor: colors.ink }}>
      <ScrollView contentContainerStyle={{ flexGrow: 1, paddingTop: insets.top + space.xxl, paddingHorizontal: space.xl }}>
        <View style={{ maxWidth: 480, width: '100%', alignSelf: 'center' }}>
          <Brand size={28} dark suffix="Driver" />
          <T v="display" style={{ color: colors.white, marginTop: space.xxl, fontSize: 36, lineHeight: 42 }}>
            Earn on your{'\n'}own schedule.
          </T>
          <View style={{ marginTop: space.xxl, gap: space.lg }}>
            {POINTS.map((p) => (
              <View key={p.title} style={{ flexDirection: 'row', gap: space.md }}>
                <View style={{ width: 40, height: 40, borderRadius: 20, backgroundColor: '#1F2937', alignItems: 'center', justifyContent: 'center' }}>
                  <Ionicons name={p.icon} size={19} color={colors.brand} />
                </View>
                <View style={{ flex: 1 }}>
                  <T v="bodyStrong" style={{ color: colors.white }}>
                    {p.title}
                  </T>
                  <T v="muted" style={{ color: '#9CA3AF', marginTop: 2 }}>
                    {p.text}
                  </T>
                </View>
              </View>
            ))}
          </View>
        </View>
      </ScrollView>
      <View style={{ paddingHorizontal: space.xl, paddingBottom: insets.bottom + space.xl, paddingTop: space.lg, gap: space.md, maxWidth: 520, width: '100%', alignSelf: 'center' }}>
        <Button label="Apply to drive" variant="brand" onPress={() => router.push('/signup')} testID="welcome-signup" />
        <Button label="Sign in" variant="light" onPress={() => router.push('/login')} testID="welcome-login" />
      </View>
    </View>
  );
}
