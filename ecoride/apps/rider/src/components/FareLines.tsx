import { fmt, type FareBreakdown } from '@ecoride/core';
import { Divider, KeyValue, space } from '@ecoride/mobile';
import { View } from 'react-native';

/** Every line of a fare, adding up to the total. */
export function FareLines({ fare }: { fare: FareBreakdown }) {
  const extraKm = Math.max(0, fare.distanceKm - fare.baseKm);
  return (
    <View>
      <KeyValue label={`Base fare (first ${fare.baseKm} km)`} value={fmt.rupees(fare.baseFare, { decimals: true })} />
      <KeyValue label={`Distance (${extraKm.toFixed(2)} km more)`} value={fmt.rupees(fare.distanceFare, { decimals: true })} />
      <KeyValue label={`Time (${fare.durationMin} min)`} value={fmt.rupees(fare.timeFare, { decimals: true })} />
      {fare.minimumFareTopUp > 0 ? <KeyValue label="Minimum fare top-up" value={fmt.rupees(fare.minimumFareTopUp, { decimals: true })} /> : null}
      {fare.surgeFare > 0 ? <KeyValue label={`Surge (${fare.surgeMultiplier}x)`} value={fmt.rupees(fare.surgeFare, { decimals: true })} /> : null}
      {fare.waitingFare > 0 ? <KeyValue label={`Waiting (${fare.waitingMin} min)`} value={fmt.rupees(fare.waitingFare, { decimals: true })} /> : null}
      {fare.discount > 0 ? <KeyValue label={`Promo ${fare.promoCode ?? ''}`} value={`−${fmt.rupees(fare.discount, { decimals: true })}`} /> : null}
      <KeyValue label={`GST (${fare.taxPct}%)`} value={fmt.rupees(fare.tax, { decimals: true })} />
      {Math.abs(fare.rounding) >= 0.01 ? <KeyValue label="Rounding" value={fmt.rupees(fare.rounding, { decimals: true })} /> : null}
      <Divider style={{ marginVertical: space.sm }} />
      <KeyValue label="Total" value={fmt.rupees(fare.total)} strong />
    </View>
  );
}
