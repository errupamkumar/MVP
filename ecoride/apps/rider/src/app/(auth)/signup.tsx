import { ApiError, toApiError } from '@ecoride/core';
import { Button, colors, Header, Row, Screen, space, T, TextField } from '@ecoride/mobile';
import { router } from 'expo-router';
import { useState } from 'react';
import { Pressable } from 'react-native';

import { Brand } from '@/components/Brand';
import { api, API_URL, auth } from '@/lib/api';

export default function Signup() {
  const { signIn } = auth.useAuth();
  const [name, setName] = useState('');
  const [phone, setPhone] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);

  const phoneDigits = phone.replace(/\D/g, '');
  const valid = name.trim().length >= 2 && phoneDigits.length >= 10 && password.length >= 8;

  async function submit() {
    setBusy(true);
    setError(null);
    try {
      const res = await api.signup({ name: name.trim(), phone, email: email.trim() || undefined, password });
      await signIn({ token: res.token, expiresAt: res.expiresAt, profile: res.profile });
      router.replace({ pathname: '/home', params: { welcome: '1' } });
    } catch (e) {
      setError(toApiError(e, API_URL));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Screen
      footer={<Button label="Create account" variant="brand" onPress={submit} loading={busy} disabled={!valid} testID="signup-submit" />}
    >
      <Header onBack={() => (router.canGoBack() ? router.back() : router.replace('/welcome'))} />
      <Brand size={24} />
      <T v="title" style={{ marginTop: space.xl }}>
        Create your account
      </T>
      <T v="muted" style={{ marginTop: space.xs, marginBottom: space.xl }}>
        Rider accounts are separate from driver accounts. New riders get ₹200 in their EcoRide wallet.
      </T>
      <TextField label="Full name" value={name} onChangeText={setName} placeholder="Ananya Rao" icon="person-outline" error={error?.fieldError('name')} testID="signup-name" />
      <TextField
        label="Mobile number"
        value={phone}
        onChangeText={setPhone}
        keyboardType="phone-pad"
        placeholder="98765 43210"
        icon="call-outline"
        error={error?.code === 'PHONE_INVALID' || error?.code === 'PHONE_TAKEN' ? error.message : error?.fieldError('phone')}
        testID="signup-phone"
      />
      <TextField
        label="Email (optional)"
        value={email}
        onChangeText={setEmail}
        autoCapitalize="none"
        keyboardType="email-address"
        placeholder="you@example.com"
        icon="mail-outline"
        error={error?.code === 'EMAIL_TAKEN' ? error.message : error?.fieldError('email')}
        testID="signup-email"
      />
      <TextField
        label="Password"
        value={password}
        onChangeText={setPassword}
        secure
        placeholder="At least 8 characters"
        icon="lock-closed-outline"
        error={error?.fieldError('password')}
        testID="signup-password"
      />
      {error && !error.fieldErrors.length && !['PHONE_INVALID', 'PHONE_TAKEN', 'EMAIL_TAKEN'].includes(error.code) ? (
        <T v="small" style={{ color: colors.danger }}>
          {error.message}
        </T>
      ) : null}
      <Row style={{ justifyContent: 'center', marginTop: space.md }}>
        <T v="muted">Already riding with us?</T>
        <Pressable onPress={() => router.replace('/login')} accessibilityRole="link">
          <T v="bodyStrong" style={{ color: colors.brand }}>
            Sign in
          </T>
        </Pressable>
      </Row>
    </Screen>
  );
}
