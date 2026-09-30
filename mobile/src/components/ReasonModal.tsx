import { useState } from 'react';
import { Modal, Text, TextInput, View } from 'react-native';

import { colors } from '@/lib/theme';

import { Button, Muted } from './ui';

/** Cross-platform text prompt (Alert.prompt is iOS-only and a no-op on web). */
export function ReasonModal({
  visible,
  title,
  message,
  confirmLabel,
  destructive,
  onSubmit,
  onClose,
}: {
  visible: boolean;
  title: string;
  message?: string;
  confirmLabel: string;
  destructive?: boolean;
  onSubmit: (reason: string) => void;
  onClose: () => void;
}) {
  const [reason, setReason] = useState('');
  const close = () => {
    setReason('');
    onClose();
  };
  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={close}>
      <View className="flex-1 items-center justify-center bg-black/40 px-4">
        <View className="w-full max-w-md rounded-card bg-white p-5">
          <Text className="text-lg font-bold text-ink">{title}</Text>
          {message ? <Muted className="mt-1">{message}</Muted> : null}
          <TextInput
            value={reason}
            onChangeText={setReason}
            placeholder="Reason (shown to the rider)"
            placeholderTextColor={colors.ink3}
            maxLength={200}
            autoFocus
            multiline
            className="mt-4 min-h-[80px] rounded-xl border border-line p-3 text-base text-ink"
            style={{ textAlignVertical: 'top' }}
            accessibilityLabel="Reason"
          />
          <View className="mt-4 flex-row justify-end">
            <Button label="Back" variant="secondary" size="md" onPress={close} className="mr-2" />
            <Button
              label={confirmLabel}
              variant={destructive ? 'danger' : 'primary'}
              size="md"
              disabled={!reason.trim()}
              onPress={() => {
                onSubmit(reason.trim());
                setReason('');
              }}
            />
          </View>
        </View>
      </View>
    </Modal>
  );
}
