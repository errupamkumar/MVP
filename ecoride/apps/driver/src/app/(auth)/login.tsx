import { toApiError } from '@ecoride/core';
import { Banner, Button, Card, colors, Header, Row, Screen, space, T, TextField } from '@ecoride/mobile';
import { router } from 'expo-router';
import { useState } from 'react';
import { Pressable } from 'react-native';

import { Brand } from '@/components/Brand';
import { api, API_URL, auth } from '@/lib/api';

export default function Login() {
  const { signIn, notice } = auth.useAuth();
  const [login, setLogin] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(l = login, p = password) {
    setBusy(true);
    setError(null);
    try {
      const res = await api.login(l, p);
      await signIn({ token: res.token, expiresAt: res.expiresAt, profile: res.profile });
      router.replace('/home');
    } catch (e) {
      setError(toApiError(e, API_URL).message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <Screen>
      <Header onBack={() => (router.canGoBack() ? router.back() : router.replace('/welcome'))} />
      <Brand size={24} suffix="Driver" />
      <T v="title" style={{ marginTop: space.xl }}>
        Sign in to drive
      </T>
      <T v="muted" style={{ marginTop: space.xs, marginBottom: space.xl }}>
        Use the mobile number or email you applied with.
      </T>
      {notice ? <Banner tone="warn" message={notice} /> : null}
      <TextField label="Mobile number or email" value={login} onChangeText={setLogin} autoCapitalize="none" placeholder="98766 00001" icon="person-outline" testID="login-id" />
      <TextField label="Password" value={password} onChangeText={setPassword} secure placeholder="Password" icon="lock-closed-outline" testID="login-password" />
      {error ? (
        <T v="small" style={{ color: colors.danger, marginBottom: space.md }}>
          {error}
        </T>
      ) : null}
      <Button label="Sign in" onPress={() => submit()} loading={busy} disabled={!login || !password} testID="login-submit" />
      <Row style={{ justifyContent: 'center', marginTop: space.lg }}>
        <T v="muted">Not driving with us yet?</T>
        <Pressable onPress={() => router.replace('/signup')} accessibilityRole="link">
          <T v="bodyStrong" style={{ color: colors.brand }}>
            Apply now
          </T>
        </Pressable>
      </Row>
      <Card style={{ marginTop: space.xxl, backgroundColor: colors.mist }}>
        <T v="label">Demo driver</T>
        <T v="body" style={{ marginTop: space.xs }}>
          Rahul Patil · Mini MH12AB4521 · 98766 00001 · Driver@123
        </T>
        <Button
          label="Sign in as the demo driver"
          variant="light"
          size="md"
          style={{ marginTop: space.md }}
          onPress={() => {
            setLogin('9876600001');
            setPassword('Driver@123');
            void submit('9876600001', 'Driver@123');
          }}
        />
      </Card>
      <T v="small" style={{ textAlign: 'center', marginTop: space.lg }}>
        Server: {API_URL}
      </T>
    </Screen>
  );
}
