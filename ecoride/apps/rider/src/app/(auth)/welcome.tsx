import { Button, colors, Ionicons, IconName, space, T } from '@ecoride/mobile';
import { router } from 'expo-router';
import { ScrollView, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Brand } from '@/components/Brand';

const POINTS: { icon: IconName; title: string; text: string }[] = [
  { icon: 'navigate', title: 'Pickup from where you are', text: 'GPS sets your pickup; your driver sees you on the map.' },
  { icon: 'pricetag', title: 'Upfront prices', text: 'See the fare before you book. Bike, Auto, Mini, Sedan or SUV.' },
  { icon: 'car-sport', title: 'Track your ride live', text: 'Watch your driver arrive, share a PIN to start, pay any way.' },
];

export default function Welcome() {
  const insets = useSafeAreaInsets();
  return (
    <View style={{ flex: 1, backgroundColor: colors.ink }}>
      <ScrollView contentContainerStyle={{ flexGrow: 1, paddingTop: insets.top + space.xxl, paddingHorizontal: space.xl }}>
        <View style={{ maxWidth: 480, width: '100%', alignSelf: 'center', flex: 1 }}>
          <Brand size={30} dark />
          <T v="display" style={{ color: colors.white, marginTop: space.xxl, fontSize: 38, lineHeight: 44 }}>
            Go anywhere{'\n'}in Pune.
          </T>
          <T v="body" style={{ color: '#9CA3AF', marginTop: space.md, fontSize: 16 }}>
            Rides in minutes, priced before you book.
          </T>
          <View style={{ marginTop: space.xxl, gap: space.lg }}>
            {POINTS.map((p) => (
              <View key={p.title} style={{ flexDirection: 'row', gap: space.md, alignItems: 'flex-start' }}>
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
        <Button label="Create an account" variant="brand" onPress={() => router.push('/signup')} testID="welcome-signup" />
        <Button label="I already have an account" variant="light" onPress={() => router.push('/login')} testID="welcome-login" />
      </View>
    </View>
  );
}
