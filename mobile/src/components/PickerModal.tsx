import { Ionicons } from '@expo/vector-icons';
import { useMemo, useState } from 'react';
import { FlatList, Modal, Pressable, Text, TextInput, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { colors } from '@/lib/theme';

import { EmptyState, LoadingState, Muted } from './ui';

export interface PickerItem {
  id: number;
  title: string;
  subtitle?: string;
}

/** Searchable full-screen list, used for pickup and drop stops. */
export function PickerModal({
  visible,
  title,
  items,
  loading,
  selectedId,
  emptyMessage,
  onPick,
  onClose,
}: {
  visible: boolean;
  title: string;
  items: PickerItem[];
  loading?: boolean;
  selectedId?: number | null;
  emptyMessage?: string;
  onPick: (item: PickerItem) => void;
  onClose: () => void;
}) {
  const insets = useSafeAreaInsets();
  const [query, setQuery] = useState('');
  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    return q ? items.filter((i) => i.title.toLowerCase().includes(q)) : items;
  }, [items, query]);

  return (
    <Modal visible={visible} animationType="slide" onRequestClose={onClose} presentationStyle="pageSheet">
      <View className="flex-1 bg-mist" style={{ paddingTop: insets.top }}>
        <View className="w-full max-w-xl flex-1 self-center px-4">
          <View className="flex-row items-center py-3">
            <Pressable onPress={onClose} accessibilityRole="button" accessibilityLabel="Close" hitSlop={10}>
              <Ionicons name="close" size={26} color={colors.ink} />
            </Pressable>
            <Text className="ml-3 text-lg font-bold text-ink">{title}</Text>
          </View>
          <View className="mb-3 flex-row items-center rounded-xl border border-line bg-white px-3">
            <Ionicons name="search" size={18} color={colors.ink3} />
            <TextInput
              value={query}
              onChangeText={setQuery}
              placeholder="Search stops"
              placeholderTextColor={colors.ink3}
              className="min-h-[46px] min-w-0 flex-1 px-2 text-base text-ink"
              autoFocus
              accessibilityLabel="Search stops"
            />
          </View>
          {loading ? (
            <LoadingState />
          ) : (
            <FlatList
              data={filtered}
              keyExtractor={(i) => String(i.id)}
              keyboardShouldPersistTaps="handled"
              ListEmptyComponent={<EmptyState icon="location" title="No stops found" message={emptyMessage} />}
              renderItem={({ item }) => {
                const selected = item.id === selectedId;
                return (
                  <Pressable
                    onPress={() => {
                      setQuery('');
                      onPick(item);
                    }}
                    accessibilityRole="button"
                    className={`mb-2 flex-row items-center rounded-xl border px-4 py-3.5 active:opacity-80 ${
                      selected ? 'border-accent bg-accent-soft' : 'border-line bg-white'
                    }`}
                  >
                    <Ionicons name="location" size={20} color={selected ? colors.accent : colors.ink3} />
                    <View className="ml-3 flex-1">
                      <Text className="text-base font-semibold text-ink">{item.title}</Text>
                      {item.subtitle ? <Muted>{item.subtitle}</Muted> : null}
                    </View>
                    {selected ? <Ionicons name="checkmark" size={20} color={colors.accent} /> : null}
                  </Pressable>
                );
              }}
            />
          )}
        </View>
      </View>
    </Modal>
  );
}
