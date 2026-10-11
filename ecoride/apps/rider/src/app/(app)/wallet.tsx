import { fmt, toApiError } from '@ecoride/core';
import { Card, Chip, colors, Divider, Header, Loading, Row, Screen, SectionLabel, showMessage, space, T, Button } from '@ecoride/mobile';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { router } from 'expo-router';
import { useState } from 'react';
import { View } from 'react-native';

import { api, API_URL } from '@/lib/api';
import { keys, useWallet } from '@/lib/queries';

const AMOUNTS = [100, 200, 500, 1000];

export default function WalletScreen() {
  const queryClient = useQueryClient();
  const { data } = useWallet();
  const [amount, setAmount] = useState(200);
  const topUp = useMutation({
    mutationFn: () => api.topUp(amount),
    onSuccess: (w) => {
      queryClient.setQueryData(keys.wallet, w);
      void queryClient.invalidateQueries({ queryKey: keys.me });
    },
    onError: (e) => showMessage('Could not add money', toApiError(e, API_URL).message),
  });
  if (!data) return <Loading />;
  return (
    <Screen>
      <Header title="EcoRide Wallet" onBack={() => router.back()} />
      <Card style={{ backgroundColor: colors.ink }}>
        <T v="label" style={{ color: '#9CA3AF' }}>
          Balance
        </T>
        <T v="display" style={{ color: colors.white, marginTop: 4 }}>
          {fmt.rupees(data.balance, { decimals: true })}
        </T>
        <T v="small" style={{ color: '#9CA3AF', marginTop: 4 }}>
          Pay for rides in one tap. If the balance is short, you pay the driver in cash.
        </T>
      </Card>
      <SectionLabel>Add money</SectionLabel>
      <Row style={{ flexWrap: 'wrap' }}>
        {AMOUNTS.map((a) => (
          <Chip key={a} label={fmt.rupees(a)} selected={amount === a} onPress={() => setAmount(a)} />
        ))}
      </Row>
      <Button label={`Add ${fmt.rupees(amount)} with UPI`} icon="phone-portrait-outline" style={{ marginTop: space.md }} loading={topUp.isPending} onPress={() => topUp.mutate()} />
      <T v="small" style={{ marginTop: space.xs, textAlign: 'center' }}>
        Demo: the UPI payment is simulated.
      </T>
      <SectionLabel>Statement</SectionLabel>
      <Card>
        {data.lines.map((l, i) => (
          <View key={l.id}>
            {i > 0 ? <Divider style={{ marginVertical: space.sm }} /> : null}
            <Row style={{ justifyContent: 'space-between' }}>
              <View style={{ flex: 1 }}>
                <T v="bodyStrong" numberOfLines={1}>
                  {l.note}
                </T>
                <T v="small">{fmt.dateTime(l.at)}</T>
              </View>
              <T v="bodyStrong" style={{ color: l.amount >= 0 ? colors.brandDark : colors.ink }}>
                {l.amount >= 0 ? '+' : ''}
                {fmt.rupees(l.amount, { decimals: true })}
              </T>
            </Row>
          </View>
        ))}
        {!data.lines.length ? <T v="muted">No transactions yet.</T> : null}
      </Card>
    </Screen>
  );
}
