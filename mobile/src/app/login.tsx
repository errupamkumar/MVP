import { Ionicons } from '@expo/vector-icons';
import { Redirect, useRouter } from 'expo-router';
import { useState } from 'react';
import { KeyboardAvoidingView, Platform, Pressable, ScrollView, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { API_BASE_URL, toApiError } from '@/api/client';
import { homePathFor, useAuth } from '@/auth/AuthContext';
import { Banner, Button, Field, Muted } from '@/components/ui';
import { colors } from '@/lib/theme';

const DEMO_ACCOUNTS = [
  { username: 'LHS-40218', label: 'Rider', who: 'A. Raghavan', icon: 'person' as const },
  { username: 'DRV-2281', label: 'Driver', who: 'S. Kumar · C-12', icon: 'car' as const },
  { username: 'admin', label: 'Desk & admin', who: 'R. Kapoor', icon: 'grid' as const },
];

const SHOW_DEMO = process.env.EXPO_PUBLIC_SHOW_DEMO_ACCOUNTS !== 'false';

export default function LoginScreen() {
  const { user, ready, signIn, sessionNotice } = useAuth();
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [touched, setTouched] = useState(false);

  if (ready && user) {
    return <Redirect href={homePathFor(user.role)} />;
  }

  const submit = async () => {
    setTouched(true);
    if (!username.trim() || !password) return;
    setBusy(true);
    setError(null);
    try {
      const signedIn = await signIn(username, password);
      router.replace(homePathFor(signedIn.role));
    } catch (e) {
      setError(toApiError(e).message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <KeyboardAvoidingView className="flex-1 bg-night" behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
      <ScrollView contentContainerStyle={{ flexGrow: 1 }} keyboardShouldPersistTaps="handled">
        <View className="w-full max-w-md self-center px-6" style={{ paddingTop: insets.top + 40 }}>
          <View className="mb-2 flex-row items-center">
            <View className="mr-3 h-11 w-11 items-center justify-center rounded-xl bg-accent">
              <Ionicons name="git-network" size={22} color={colors.white} />
            </View>
            <View>
              <Text className="text-2xl font-bold text-white">Plant Ride</Text>
              <Text className="text-sm text-line">LHS · smart movement inside the plant</Text>
            </View>
          </View>
          <Text className="mb-8 mt-6 text-base leading-6 text-line">
            Book a cab in under 30 seconds, see it coming, and board with a 4-digit code.
          </Text>
        </View>

        <View className="flex-1 rounded-t-3xl bg-mist px-6 pt-8" style={{ paddingBottom: insets.bottom + 24 }}>
          <View className="w-full max-w-md self-center">
            {sessionNotice ? <Banner tone="amber" icon="time" title={sessionNotice} /> : null}
            {error ? <Banner tone="danger" icon="alert-circle" title="Could not sign in" message={error} /> : null}

            <Field
              label="P. No., driver ID or username"
              value={username}
              onChangeText={setUsername}
              autoCapitalize="characters"
              autoCorrect={false}
              placeholder="e.g. LHS-40218"
              returnKeyType="next"
              error={touched && !username.trim() ? 'Enter your ID' : undefined}
            />
            <Field
              label="Password"
              value={password}
              onChangeText={setPassword}
              secureTextEntry
              placeholder="Password"
              returnKeyType="go"
              onSubmitEditing={submit}
              error={touched && !password ? 'Enter your password' : undefined}
            />
            <Button label="Sign in" onPress={submit} loading={busy} className="mt-2" />

            {SHOW_DEMO ? (
              <View className="mt-8">
                <Text className="mb-2 text-xs font-bold uppercase tracking-widest text-ink-3">Demo accounts · password Plant@123</Text>
                {DEMO_ACCOUNTS.map((a) => (
                  <Pressable
                    key={a.username}
                    onPress={() => {
                      setUsername(a.username);
                      setPassword('Plant@123');
                      setError(null);
                    }}
                    accessibilityRole="button"
                    accessibilityLabel={`Use the ${a.label} demo account`}
                    className="mb-2 flex-row items-center rounded-xl border border-line bg-white px-4 py-3 active:opacity-80"
                  >
                    <Ionicons name={a.icon} size={20} color={colors.accent} />
                    <View className="ml-3 flex-1">
                      <Text className="text-base font-bold text-ink">{a.label}</Text>
                      <Muted>
                        {a.username} · {a.who}
                      </Muted>
                    </View>
                    <Ionicons name="arrow-forward" size={18} color={colors.ink3} />
                  </Pressable>
                ))}
              </View>
            ) : null}
            <Muted className="mt-6 text-center text-xs">Server: {API_BASE_URL}</Muted>
          </View>
        </View>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}
