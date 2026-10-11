import { fmt } from '@ecoride/core';
import { Card, colors, Divider, Header, Loading, Row, Screen, SectionLabel, space, T } from '@ecoride/mobile';
import { router } from 'expo-router';
import { View } from 'react-native';

import { StatTile } from '@/components/Panels';
import { useEarnings } from '@/lib/queries';

const LEDGER_LABEL: Record<string, string> = {
  EARNING: 'Trip earning',
  TIP: 'Tip',
  CANCEL_FEE: 'Cancellation fee',
  PENALTY: 'Penalty',
  CASH_COLLECTED: 'Cash kept',
  PAYOUT: 'Payout',
  ADJUSTMENT: 'Adjustment',
};

export default function Earnings() {
  const { data } = useEarnings();
  if (!data) return <Loading />;
  const max = Math.max(1, ...data.days.map((d) => d.net));
  return (
    <Screen>
      <Header title="Earnings" onBack={() => router.back()} />
      <Card style={{ backgroundColor: colors.ink }}>
        <T v="label" style={{ color: '#9CA3AF' }}>
          Today
        </T>
        <T v="display" style={{ color: colors.white, marginTop: 4 }} testID="earnings-today">
          {fmt.rupees(data.today.net)}
        </T>
        <T v="small" style={{ color: '#9CA3AF', marginTop: 4 }}>
          {fmt.count(data.today.trips, 'trip')} · {data.today.distanceKm} km · tips {fmt.rupees(data.today.tips)}
          {data.today.onlineMinutes ? ` · online ${fmt.minutes(data.today.onlineMinutes * 60)}` : ''}
        </T>
      </Card>

      <SectionLabel>Last 7 days</SectionLabel>
      <Card>
        <View style={{ flexDirection: 'row', alignItems: 'flex-end', height: 140, gap: 8 }}>
          {data.days.map((d) => (
            <View key={d.date} style={{ flex: 1, alignItems: 'center', justifyContent: 'flex-end', height: '100%' }}>
              <T v="small" style={{ fontSize: 10, marginBottom: 3 }}>
                {d.net > 0 ? `₹${Math.round(d.net)}` : ''}
              </T>
              <View style={{ width: '70%', height: `${Math.max(2, (d.net / max) * 80)}%`, borderTopLeftRadius: 4, borderTopRightRadius: 4, backgroundColor: d.date === data.days[data.days.length - 1].date ? colors.brand : colors.ink3 }} />
              <T v="small" style={{ marginTop: 4 }}>
                {new Date(`${d.date}T00:00:00`).toLocaleDateString('en-IN', { weekday: 'short' }).slice(0, 2)}
              </T>
            </View>
          ))}
        </View>
        <Divider />
        <Row gap={space.sm}>
          <StatTile label="Week" value={fmt.rupees(data.week.net)} icon="cash-outline" />
          <StatTile label="Trips" value={String(data.week.trips)} icon="car-outline" />
          <StatTile label="Cash kept" value={fmt.rupees(data.week.cashKept)} icon="cash-outline" />
        </Row>
      </Card>

      <SectionLabel>Next payout</SectionLabel>
      <Card>
        <Row style={{ justifyContent: 'space-between' }}>
          <View style={{ flex: 1 }}>
            <T v="h1" style={{ color: data.balance >= 0 ? colors.ink : colors.danger }}>
              {fmt.rupees(data.balance, { decimals: true })}
            </T>
            <T v="muted">
              {data.balance >= 0
                ? 'Paid to your bank account every Monday.'
                : 'You hold more cash than you earned: this is the commission due to EcoRide.'}
            </T>
          </View>
        </Row>
      </Card>

      <SectionLabel>Activity</SectionLabel>
      <Card>
        {data.lines.map((l, i) => (
          <View key={l.id}>
            {i > 0 ? <Divider style={{ marginVertical: space.sm }} /> : null}
            <Row style={{ justifyContent: 'space-between' }}>
              <View style={{ flex: 1 }}>
                <T v="bodyStrong">
                  {LEDGER_LABEL[l.type] ?? l.type}
                  {l.rideCode ? ` · ${l.rideCode}` : ''}
                </T>
                <T v="small" numberOfLines={1}>
                  {fmt.dateTime(l.at)} · {l.note}
                </T>
              </View>
              <T v="bodyStrong" style={{ color: l.amount >= 0 ? colors.brandDark : colors.ink2 }}>
                {l.amount >= 0 ? '+' : ''}
                {fmt.rupees(l.amount, { decimals: true })}
              </T>
            </Row>
          </View>
        ))}
      </Card>
    </Screen>
  );
}
