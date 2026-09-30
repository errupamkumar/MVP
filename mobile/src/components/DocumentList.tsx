import { Ionicons } from '@expo/vector-icons';
import { Text, View } from 'react-native';

import type { DocumentStatus } from '@/api/types';
import { colors } from '@/lib/theme';

import { Muted, Row } from './ui';

/** Licence, gate pass, insurance, fitness, PUC: green valid, amber expiring, red expired (blocks dispatch). */
export function DocumentList({ documents }: { documents: DocumentStatus[] }) {
  return (
    <View>
      {documents.map((d) => {
        const icon = d.state === 'VALID' ? 'checkmark-circle' : d.state === 'EXPIRING' ? 'warning' : 'close-circle';
        const color = d.state === 'VALID' ? colors.teal : d.state === 'EXPIRING' ? colors.amber : colors.danger;
        return (
          <Row key={d.document} className="border-b border-line py-2.5">
            <Ionicons name={icon} size={20} color={color} />
            <View className="ml-3 flex-1">
              <Text className="text-base font-semibold text-ink">{d.document}</Text>
              <Muted>{d.reference}</Muted>
            </View>
            <Text
              className={`text-sm font-semibold ${
                d.state === 'VALID' ? 'text-teal-deep' : d.state === 'EXPIRING' ? 'text-amber' : 'text-danger'
              }`}
            >
              {d.label}
            </Text>
          </Row>
        );
      })}
    </View>
  );
}
