import { ApiError, toApiError } from '@ecoride/core';
import { Banner, Button, Chip, colors, Header, Ionicons, Row, Screen, space, T, TextField } from '@ecoride/mobile';
import { router } from 'expo-router';
import { useState } from 'react';
import { View } from 'react-native';

import { Brand } from '@/components/Brand';
import { api, API_URL, auth } from '@/lib/api';
import { useConfig } from '@/lib/queries';

const STEPS = ['About you', 'Your vehicle', 'Licence'];

/** Driver onboarding in three steps; the account then waits for an admin to verify it. */
export default function Signup() {
  const { signIn } = auth.useAuth();
  const products = useConfig().data?.products ?? [];
  const [step, setStep] = useState(0);
  const [form, setForm] = useState({
    name: '',
    phone: '',
    email: '',
    password: '',
    vehicleType: 'MINI',
    vehicleMake: '',
    vehicleModel: '',
    vehicleColor: '',
    vehiclePlate: '',
    vehicleYear: '',
    licenseNo: '',
  });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);
  const set = (k: keyof typeof form) => (v: string) => setForm((f) => ({ ...f, [k]: v }));

  const stepValid = [
    form.name.trim().length >= 2 && form.phone.replace(/\D/g, '').length >= 10 && form.password.length >= 8,
    !!form.vehicleType && !!form.vehicleMake.trim() && !!form.vehicleModel.trim() && !!form.vehicleColor.trim() && form.vehiclePlate.replace(/\W/g, '').length >= 8 && /^\d{4}$/.test(form.vehicleYear),
    form.licenseNo.replace(/\s/g, '').length >= 8,
  ][step];

  async function submit() {
    setBusy(true);
    setError(null);
    try {
      const res = await api.signup({
        name: form.name.trim(),
        phone: form.phone,
        email: form.email.trim() || undefined,
        password: form.password,
        licenseNo: form.licenseNo,
        vehicleType: form.vehicleType,
        vehicleMake: form.vehicleMake.trim(),
        vehicleModel: form.vehicleModel.trim(),
        vehicleColor: form.vehicleColor.trim(),
        vehiclePlate: form.vehiclePlate,
        vehicleYear: Number(form.vehicleYear),
      });
      await signIn({ token: res.token, expiresAt: res.expiresAt, profile: res.profile });
      router.replace('/home');
    } catch (e) {
      const err = toApiError(e, API_URL);
      setError(err);
      if (['PHONE_INVALID', 'PHONE_TAKEN', 'EMAIL_TAKEN'].includes(err.code)) setStep(0);
      else if (['PLATE_INVALID', 'PLATE_TAKEN', 'VEHICLE_TYPE_UNKNOWN'].includes(err.code)) setStep(1);
    } finally {
      setBusy(false);
    }
  }

  return (
    <Screen
      footer={
        <Row>
          {step > 0 ? <Button label="Back" variant="secondary" onPress={() => setStep(step - 1)} style={{ flex: 1 }} /> : null}
          <Button
            label={step < STEPS.length - 1 ? 'Continue' : 'Submit application'}
            variant="brand"
            onPress={() => (step < STEPS.length - 1 ? setStep(step + 1) : submit())}
            disabled={!stepValid}
            loading={busy}
            style={{ flex: 2 }}
            testID="signup-next"
          />
        </Row>
      }
    >
      <Header onBack={() => (router.canGoBack() ? router.back() : router.replace('/welcome'))} />
      <Brand size={22} suffix="Driver" />
      <T v="title" style={{ marginTop: space.lg }}>
        Apply to drive
      </T>
      <Row style={{ marginTop: space.md, marginBottom: space.xl }} gap={6}>
        {STEPS.map((s, i) => (
          <View key={s} style={{ flex: 1 }}>
            <View style={{ height: 4, borderRadius: 2, backgroundColor: i <= step ? colors.brand : colors.line }} />
            <T v="small" style={{ marginTop: 6, color: i === step ? colors.ink : colors.ink3, fontWeight: i === step ? '700' : '400' }}>
              {i + 1}. {s}
            </T>
          </View>
        ))}
      </Row>
      {error ? (
        <View style={{ marginBottom: space.md }}>
          <Banner tone="danger" message={error.message} />
        </View>
      ) : null}

      {step === 0 ? (
        <>
          <TextField label="Full name (as on your licence)" value={form.name} onChangeText={set('name')} placeholder="Sandeep Kulkarni" icon="person-outline" testID="signup-name" />
          <TextField label="Mobile number" value={form.phone} onChangeText={set('phone')} keyboardType="phone-pad" placeholder="98765 43210" icon="call-outline" testID="signup-phone" />
          <TextField label="Email (optional)" value={form.email} onChangeText={set('email')} autoCapitalize="none" keyboardType="email-address" placeholder="you@example.com" icon="mail-outline" testID="signup-email" />
          <TextField label="Password" value={form.password} onChangeText={set('password')} secure placeholder="At least 8 characters" icon="lock-closed-outline" testID="signup-password" />
          <T v="small">Driver accounts are separate from rider accounts.</T>
        </>
      ) : null}

      {step === 1 ? (
        <>
          <T v="small" style={{ fontWeight: '600', color: colors.ink2, marginBottom: 6 }}>
            Vehicle type
          </T>
          <Row style={{ flexWrap: 'wrap', marginBottom: space.md }}>
            {products.map((p) => (
              <Chip key={p.code} label={p.name} selected={form.vehicleType === p.code} onPress={() => set('vehicleType')(p.code)} />
            ))}
          </Row>
          <Row gap={space.sm}>
            <View style={{ flex: 1 }}>
              <TextField label="Make" value={form.vehicleMake} onChangeText={set('vehicleMake')} placeholder="Maruti Suzuki" testID="signup-make" />
            </View>
            <View style={{ flex: 1 }}>
              <TextField label="Model" value={form.vehicleModel} onChangeText={set('vehicleModel')} placeholder="Dzire" testID="signup-model" />
            </View>
          </Row>
          <Row gap={space.sm}>
            <View style={{ flex: 1 }}>
              <TextField label="Colour" value={form.vehicleColor} onChangeText={set('vehicleColor')} placeholder="White" testID="signup-color" />
            </View>
            <View style={{ flex: 1 }}>
              <TextField label="Year" value={form.vehicleYear} onChangeText={set('vehicleYear')} keyboardType="number-pad" placeholder="2022" maxLength={4} testID="signup-year" />
            </View>
          </Row>
          <TextField label="Registration number" value={form.vehiclePlate} onChangeText={(t) => set('vehiclePlate')(t.toUpperCase())} autoCapitalize="characters" placeholder="MH12 AB 1234" icon="car-outline" testID="signup-plate" />
        </>
      ) : null}

      {step === 2 ? (
        <>
          <TextField label="Driving licence number" value={form.licenseNo} onChangeText={(t) => set('licenseNo')(t.toUpperCase())} autoCapitalize="characters" placeholder="MH12 20190012345" icon="card-outline" testID="signup-licence" />
          <View style={{ backgroundColor: colors.white, borderRadius: 14, padding: space.lg, gap: space.md }}>
            {['Driving licence', 'Vehicle RC', 'Insurance', 'Profile photo'].map((d) => (
              <Row key={d} gap={space.md}>
                <Ionicons name="document-text-outline" size={20} color={colors.ink2} />
                <T v="body" style={{ flex: 1 }}>
                  {d}
                </T>
                <T v="small" style={{ color: colors.brandDark, fontWeight: '700' }}>
                  Checked at approval
                </T>
              </Row>
            ))}
          </View>
          <T v="small" style={{ marginTop: space.md }}>
            Demo: document photos are not uploaded. An EcoRide admin checks your details and approves the account, usually within a day.
          </T>
        </>
      ) : null}
    </Screen>
  );
}
