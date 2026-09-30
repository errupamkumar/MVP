import { Ionicons } from '@expo/vector-icons';
import { useState } from 'react';
import { Platform, Share, Text, useWindowDimensions, View } from 'react-native';

import { adminApi } from '@/api/endpoints';
import { DeskHeader } from '@/components/DeskHeader';
import {
  Banner,
  Button,
  Card,
  ErrorBanner,
  ErrorState,
  IconButton,
  LoadingState,
  Muted,
  Pill,
  ProgressBar,
  Row,
  Screen,
  SectionTitle,
  StatTile,
} from '@/components/ui';
import { useBilling } from '@/hooks/queries';
import { currentPeriod, km, periodLabel, rupees, shiftPeriod, wholeRupees } from '@/lib/format';
import { colors } from '@/lib/theme';

export default function BillingScreen() {
  const [period, setPeriod] = useState(currentPeriod);
  const billing = useBilling(period);
  const { width } = useWindowDimensions();
  const wide = width >= 900;
  const [exporting, setExporting] = useState(false);
  const [exportError, setExportError] = useState<unknown>(null);
  const [exported, setExported] = useState<string | null>(null);

  const exportCsv = async () => {
    setExporting(true);
    setExportError(null);
    try {
      const csv = await adminApi.exportCsv(period);
      const fileName = `plant-ride-costs-${period}.csv`;
      if (Platform.OS === 'web') {
        const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = fileName;
        link.click();
        URL.revokeObjectURL(url);
      } else {
        await Share.share({ title: fileName, message: csv });
      }
      setExported(`${fileName} · ${csv.trim().split('\n').length - 1} rides`);
    } catch (e) {
      setExportError(e);
    } finally {
      setExporting(false);
    }
  };

  return (
    <Screen width="xl" refreshing={billing.isRefetching} onRefresh={() => billing.refetch()}>
      <DeskHeader title="Billing & cost centres" subtitle="Every kilometre on a cost centre, ready for the ERP" />

      <Row className="mb-4 justify-between">
        <Row>
          <IconButton icon="chevron-back" label="Previous month" onPress={() => setPeriod((p) => shiftPeriod(p, -1))} />
          <Text className="mx-3 text-xl font-bold text-ink">{periodLabel(period)}</Text>
          <IconButton
            icon="chevron-forward"
            label="Next month"
            onPress={() => setPeriod((p) => (p >= currentPeriod() ? p : shiftPeriod(p, 1)))}
          />
          {billing.data ? (
            <View className="ml-3">
              <Pill label={billing.data.draft ? 'Draft' : 'Closed'} tone={billing.data.draft ? 'amber' : 'teal'} />
            </View>
          ) : null}
        </Row>
        <Button label="Export to SAP (CSV)" icon="download" size="md" variant="dark" loading={exporting} onPress={exportCsv} />
      </Row>
      {exported ? <Banner tone="teal" icon="document" title="Export ready" message={exported} /> : null}
      {exportError ? <ErrorBanner error={exportError} /> : null}

      {billing.isPending ? (
        <LoadingState label="Adding up the month…" />
      ) : billing.isError ? (
        <ErrorState error={billing.error} onRetry={() => billing.refetch()} />
      ) : (
        <>
          <View className="flex-row flex-wrap">
            <StatTile value={billing.data.totals.trips.toLocaleString('en-IN')} label="Trips this month" />
            <StatTile value={billing.data.totals.rides.toLocaleString('en-IN')} label="Rides charged" />
            <StatTile value={km(billing.data.totals.gpsKm)} label="GPS distance" />
            <StatTile value={wholeRupees(billing.data.totals.amount)} label="Transport cost" tone="accent" />
            <StatTile value={String(billing.data.totals.ridersPerTrip)} label="Riders per trip" tone="teal" />
          </View>
          <Muted className="mb-2">
            Shared trips split by the {billing.data.sharingRule.toLowerCase()} rule. GPS kilometres are the contractual source for vendor bills.
          </Muted>

          <View className={wide ? 'flex-row items-start' : ''}>
            <View className={wide ? 'mr-4 flex-[3]' : ''}>
              <SectionTitle title="Charges by cost centre" />
              <Card className="p-0">
                {billing.data.costCentres.map((c, i) => (
                  <View key={c.code} className={`px-4 py-3 ${i > 0 ? 'border-t border-line' : ''}`}>
                    <Row className="justify-between">
                      <View className="flex-1">
                        <Text className="text-base font-bold text-ink">
                          {c.code} <Text className="font-normal text-ink-2">· {c.name}</Text>
                        </Text>
                        <Muted>
                          {c.trips} trips · {c.rides} rides · {km(c.km)}
                        </Muted>
                      </View>
                      <View className="items-end">
                        <Text className="text-base font-bold text-ink">{rupees(c.amount)}</Text>
                        <Muted className="text-xs">of {wholeRupees(c.budget)}</Muted>
                      </View>
                    </Row>
                    <Row className="mt-2">
                      <View className="flex-1">
                        <ProgressBar pct={c.budgetUsedPct} tone={c.budgetStatus === 'OVER' ? 'danger' : c.budgetStatus === 'WARN' ? 'amber' : 'teal'} />
                      </View>
                      <Text
                        className={`ml-3 w-20 text-right text-sm font-bold ${
                          c.budgetStatus === 'OVER' ? 'text-danger' : c.budgetStatus === 'WARN' ? 'text-amber' : 'text-teal-deep'
                        }`}
                      >
                        {c.budgetUsedPct}% used
                      </Text>
                    </Row>
                  </View>
                ))}
              </Card>
            </View>

            <View className={wide ? 'flex-[2]' : ''}>
              <SectionTitle title="Vendor bill vs GPS" />
              {billing.data.vendors.map((v) => (
                <Card key={v.vendorCode} className={`mb-2 ${v.status === 'DISPUTE' ? 'border-danger' : ''}`}>
                  <Row className="justify-between">
                    <Text className="flex-1 text-base font-bold text-ink">{v.vendorName}</Text>
                    <Pill
                      label={v.status === 'DISPUTE' ? 'Dispute' : v.status === 'MATCHED' ? 'Matches GPS' : 'No claim yet'}
                      tone={v.status === 'DISPUTE' ? 'danger' : v.status === 'MATCHED' ? 'teal' : 'neutral'}
                    />
                  </Row>
                  <Row className="mt-2 justify-between">
                    <View>
                      <Muted className="text-xs">Claimed</Muted>
                      <Text className="text-base font-semibold text-ink">{v.claimedKm != null ? km(v.claimedKm) : '—'}</Text>
                    </View>
                    <View>
                      <Muted className="text-xs">GPS</Muted>
                      <Text className="text-base font-semibold text-ink">{km(v.gpsKm)}</Text>
                    </View>
                    <View className="items-end">
                      <Muted className="text-xs">Gap</Muted>
                      <Text className={`text-base font-bold ${v.status === 'DISPUTE' ? 'text-danger' : 'text-ink'}`}>
                        {v.gapKm != null ? `${v.gapKm > 0 ? '+' : ''}${km(v.gapKm)} (${v.gapPct}%)` : '—'}
                      </Text>
                    </View>
                  </Row>
                  {v.status === 'DISPUTE' && v.claimedAmount != null ? (
                    <Muted className="mt-2">
                      Billed {rupees(v.claimedAmount)} against {rupees(v.gpsAmount)} on GPS distance.
                    </Muted>
                  ) : null}
                </Card>
              ))}

              <SectionTitle title="Compliance watch" />
              <Card>
                <ComplianceLine icon="warning" color={colors.amber} text={`${billing.data.compliance.documentsExpiringSoon} documents expiring within 30 days`} />
                <ComplianceLine
                  icon="ban"
                  color={colors.danger}
                  text={`${billing.data.compliance.expiredDocuments} expired · ${billing.data.compliance.blockedVehicles} vehicle(s) and ${billing.data.compliance.blockedDrivers} driver(s) blocked from dispatch`}
                />
                <ComplianceLine
                  icon="checkmark-circle"
                  color={colors.teal}
                  text={`${billing.data.compliance.fullyCompliantVehicles} of ${billing.data.compliance.totalVehicles} vehicles fully compliant`}
                />
              </Card>
            </View>
          </View>
        </>
      )}
    </Screen>
  );
}

function ComplianceLine({ icon, color, text }: { icon: 'warning' | 'ban' | 'checkmark-circle'; color: string; text: string }) {
  return (
    <Row className="py-1.5">
      <Ionicons name={icon} size={18} color={color} />
      <Text className="ml-2 flex-1 text-base text-ink-2">{text}</Text>
    </Row>
  );
}
