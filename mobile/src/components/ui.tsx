import { Ionicons } from '@expo/vector-icons';
import { ComponentProps, ReactNode } from 'react';
import {
  ActivityIndicator,
  Pressable,
  RefreshControl,
  ScrollView,
  Text,
  TextInput,
  TextInputProps,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { ApiError, toApiError } from '@/api/client';
import { colors } from '@/lib/theme';

type IconName = ComponentProps<typeof Ionicons>['name'];

// ------------------------------------------------------------------ layout

const WIDTHS = { md: 'max-w-xl', lg: 'max-w-3xl', xl: 'max-w-6xl' } as const;

export function Screen({
  children,
  scroll = true,
  refreshing,
  onRefresh,
  width = 'md',
  footer,
  topInset = true,
}: {
  children: ReactNode;
  scroll?: boolean;
  refreshing?: boolean;
  onRefresh?: () => void;
  width?: keyof typeof WIDTHS;
  /** Pinned below the scroll area (primary action). */
  footer?: ReactNode;
  topInset?: boolean;
}) {
  const insets = useSafeAreaInsets();
  const inner = <View className={`w-full self-center px-4 pb-8 ${WIDTHS[width]}`}>{children}</View>;
  return (
    <View className="flex-1 bg-mist" style={{ paddingTop: topInset ? insets.top : 0 }}>
      {scroll ? (
        <ScrollView
          className="flex-1"
          contentContainerStyle={{ paddingTop: 12 }}
          keyboardShouldPersistTaps="handled"
          refreshControl={
            onRefresh ? (
              <RefreshControl refreshing={!!refreshing} onRefresh={onRefresh} tintColor={colors.accent} />
            ) : undefined
          }
        >
          {inner}
        </ScrollView>
      ) : (
        <View className="flex-1 pt-3">{inner}</View>
      )}
      {footer ? (
        <View className="border-t border-line bg-white px-4 pt-3" style={{ paddingBottom: Math.max(insets.bottom, 12) }}>
          <View className={`w-full self-center ${WIDTHS[width]}`}>{footer}</View>
        </View>
      ) : null}
    </View>
  );
}

export function Card({ children, className = '', onPress }: { children: ReactNode; className?: string; onPress?: () => void }) {
  // Tailwind resolves conflicting utilities by stylesheet order, not class order, so a
  // "bg-ink" override loses to a default "bg-white". Only add defaults that are not overridden.
  const background = /(^|\s)bg-/.test(className) ? '' : 'bg-white';
  const borderColor = /(^|\s)border-(accent|danger|teal|amber|ink|line|dashed)/.test(className) ? '' : 'border-line';
  const base = `rounded-card border p-4 ${borderColor} ${background} ${className}`;
  if (onPress) {
    return (
      <Pressable onPress={onPress} accessibilityRole="button" className={`${base} active:opacity-80`}>
        {children}
      </Pressable>
    );
  }
  return <View className={base}>{children}</View>;
}

export function Row({ children, className = '' }: { children: ReactNode; className?: string }) {
  return <View className={`flex-row items-center ${className}`}>{children}</View>;
}

// ------------------------------------------------------------------ text

export function H1({ children, className = '' }: { children: ReactNode; className?: string }) {
  return <Text className={`text-2xl font-bold text-ink ${className}`}>{children}</Text>;
}

export function H2({ children, className = '' }: { children: ReactNode; className?: string }) {
  return <Text className={`text-lg font-bold text-ink ${className}`}>{children}</Text>;
}

export function Body({ children, className = '' }: { children: ReactNode; className?: string }) {
  return <Text className={`text-base text-ink-2 ${className}`}>{children}</Text>;
}

export function Muted({ children, className = '' }: { children: ReactNode; className?: string }) {
  return <Text className={`text-sm text-ink-3 ${className}`}>{children}</Text>;
}

export function Eyebrow({ children, className = '' }: { children: ReactNode; className?: string }) {
  return <Text className={`text-xs font-bold uppercase tracking-widest text-ink-3 ${className}`}>{children}</Text>;
}

export function SectionTitle({ title, action }: { title: string; action?: ReactNode }) {
  return (
    <Row className="mb-2 mt-6 justify-between">
      <Eyebrow>{title}</Eyebrow>
      {action}
    </Row>
  );
}

export function KeyValue({ label, value, strong = false }: { label: string; value: ReactNode; strong?: boolean }) {
  return (
    <Row className="justify-between border-b border-line py-2.5 last:border-b-0">
      <Muted>{label}</Muted>
      {typeof value === 'string' || typeof value === 'number' ? (
        <Text className={`ml-4 flex-shrink text-right text-base ${strong ? 'font-bold text-ink' : 'text-ink-2'}`}>{value}</Text>
      ) : (
        value
      )}
    </Row>
  );
}

// ------------------------------------------------------------------ controls

type ButtonVariant = 'primary' | 'secondary' | 'danger' | 'ghost' | 'dark';

const BUTTON_STYLES: Record<ButtonVariant, { box: string; text: string; spinner: string }> = {
  primary: { box: 'bg-accent', text: 'text-white', spinner: colors.white },
  dark: { box: 'bg-ink', text: 'text-white', spinner: colors.white },
  secondary: { box: 'border border-line bg-white', text: 'text-ink', spinner: colors.ink },
  danger: { box: 'bg-danger', text: 'text-white', spinner: colors.white },
  ghost: { box: 'bg-transparent', text: 'text-accent', spinner: colors.accent },
};

export function Button({
  label,
  onPress,
  variant = 'primary',
  loading = false,
  disabled = false,
  icon,
  size = 'lg',
  className = '',
  accessibilityLabel,
}: {
  label: string;
  onPress?: () => void;
  variant?: ButtonVariant;
  loading?: boolean;
  disabled?: boolean;
  icon?: IconName;
  size?: 'sm' | 'md' | 'lg';
  className?: string;
  accessibilityLabel?: string;
}) {
  const style = BUTTON_STYLES[variant];
  const inactive = disabled || loading;
  const pad = size === 'lg' ? 'min-h-[52px] px-5' : size === 'md' ? 'min-h-[44px] px-4' : 'min-h-[36px] px-3';
  const text = size === 'sm' ? 'text-sm' : 'text-base';
  return (
    <Pressable
      onPress={inactive ? undefined : onPress}
      accessibilityRole="button"
      accessibilityLabel={accessibilityLabel ?? label}
      accessibilityState={{ disabled: inactive, busy: loading }}
      className={`flex-row items-center justify-center rounded-xl ${pad} ${style.box} ${
        inactive ? 'opacity-50' : 'active:opacity-80'
      } ${className}`}
    >
      {loading ? (
        <ActivityIndicator color={style.spinner} />
      ) : (
        <>
          {icon ? (
            <Ionicons
              name={icon}
              size={size === 'sm' ? 16 : 20}
              color={variant === 'secondary' ? colors.ink : variant === 'ghost' ? colors.accent : colors.white}
              style={{ marginRight: 8 }}
            />
          ) : null}
          <Text className={`font-bold ${text} ${style.text}`}>{label}</Text>
        </>
      )}
    </Pressable>
  );
}

export function IconButton({
  icon,
  onPress,
  label,
  tone = 'ink',
}: {
  icon: IconName;
  onPress: () => void;
  label: string;
  tone?: 'ink' | 'accent' | 'danger';
}) {
  const color = tone === 'accent' ? colors.accent : tone === 'danger' ? colors.danger : colors.ink;
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={label}
      hitSlop={8}
      className="h-11 w-11 items-center justify-center rounded-full border border-line bg-white active:opacity-70"
    >
      <Ionicons name={icon} size={20} color={color} />
    </Pressable>
  );
}

export function Field({
  label,
  error,
  hint,
  ...props
}: TextInputProps & { label: string; error?: string; hint?: string }) {
  return (
    <View className="mb-3">
      <Text className="mb-1.5 text-sm font-semibold text-ink-2">{label}</Text>
      <TextInput
        placeholderTextColor={colors.ink3}
        className={`min-h-[48px] rounded-xl border px-3.5 text-base ${error ? 'border-danger' : 'border-line'} ${
          props.editable === false ? 'bg-mist text-ink-3' : 'bg-white text-ink'
        }`}
        accessibilityLabel={label}
        {...props}
      />
      {error ? <Text className="mt-1 text-sm text-danger">{error}</Text> : hint ? <Muted className="mt-1">{hint}</Muted> : null}
    </View>
  );
}

export function Chip({
  label,
  selected,
  onPress,
  icon,
}: {
  label: string;
  selected: boolean;
  onPress: () => void;
  icon?: IconName;
}) {
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityState={{ selected }}
      className={`mb-2 mr-2 min-h-[40px] flex-row items-center rounded-full border px-3.5 ${
        selected ? 'border-accent bg-accent-soft' : 'border-line bg-white'
      }`}
    >
      {icon ? (
        <Ionicons name={icon} size={16} color={selected ? colors.accent : colors.ink3} style={{ marginRight: 6 }} />
      ) : null}
      <Text className={`text-sm font-semibold ${selected ? 'text-accent' : 'text-ink-2'}`}>{label}</Text>
    </Pressable>
  );
}

export function Segmented<T extends string>({
  options,
  value,
  onChange,
}: {
  options: { value: T; label: string }[];
  value: T;
  onChange: (value: T) => void;
}) {
  return (
    <Row className="rounded-xl border border-line bg-white p-1">
      {options.map((o) => (
        <Pressable
          key={o.value}
          onPress={() => onChange(o.value)}
          accessibilityRole="tab"
          accessibilityState={{ selected: value === o.value }}
          className={`min-h-[40px] flex-1 items-center justify-center rounded-lg ${value === o.value ? 'bg-ink' : ''}`}
        >
          <Text className={`text-sm font-bold ${value === o.value ? 'text-white' : 'text-ink-3'}`}>{o.label}</Text>
        </Pressable>
      ))}
    </Row>
  );
}

export function Stepper({
  value,
  min,
  max,
  onChange,
  suffix,
}: {
  value: number;
  min: number;
  max: number;
  onChange: (value: number) => void;
  suffix?: string;
}) {
  return (
    <Row>
      <IconButton icon="remove" label="Decrease" onPress={() => onChange(Math.max(min, value - 1))} />
      <Text className="mx-3 min-w-[56px] text-center text-lg font-bold text-ink">
        {value}
        {suffix ? <Text className="text-sm font-normal text-ink-3"> {suffix}</Text> : null}
      </Text>
      <IconButton icon="add" label="Increase" onPress={() => onChange(Math.min(max, value + 1))} />
    </Row>
  );
}

// ------------------------------------------------------------------ status

type Tone = 'neutral' | 'accent' | 'teal' | 'danger' | 'amber' | 'ink';

const PILL: Record<Tone, string> = {
  neutral: 'bg-mist text-ink-2',
  accent: 'bg-accent-soft text-accent-deep',
  teal: 'bg-teal-soft text-teal-deep',
  danger: 'bg-danger-soft text-danger',
  amber: 'bg-amber-soft text-amber',
  ink: 'bg-ink text-white',
};

export function Pill({ label, tone = 'neutral' }: { label: string; tone?: Tone }) {
  const [bg, fg] = PILL[tone].split(' ');
  return (
    <View className={`self-start rounded-full px-2.5 py-1 ${bg}`}>
      <Text className={`text-xs font-bold ${fg}`}>{label}</Text>
    </View>
  );
}

export function Banner({
  tone = 'accent',
  icon = 'information-circle',
  title,
  message,
  action,
}: {
  tone?: 'accent' | 'teal' | 'danger' | 'amber';
  icon?: IconName;
  title: string;
  message?: string;
  action?: ReactNode;
}) {
  const bg = { accent: 'bg-accent-soft', teal: 'bg-teal-soft', danger: 'bg-danger-soft', amber: 'bg-amber-soft' }[tone];
  const color = { accent: colors.accent, teal: colors.teal, danger: colors.danger, amber: colors.amber }[tone];
  return (
    <View className={`mb-3 flex-row rounded-card p-3.5 ${bg}`} accessibilityRole="alert">
      <Ionicons name={icon} size={22} color={color} style={{ marginRight: 10, marginTop: 1 }} />
      <View className="flex-1">
        <Text className="text-base font-bold text-ink">{title}</Text>
        {message ? <Text className="mt-0.5 text-sm text-ink-2">{message}</Text> : null}
        {action ? <View className="mt-2">{action}</View> : null}
      </View>
    </View>
  );
}

export function ErrorBanner({ error }: { error: unknown }) {
  if (!error) return null;
  const e = toApiError(error);
  return <Banner tone="danger" icon="alert-circle" title={bannerTitle(e)} message={e.message} />;
}

function bannerTitle(e: ApiError): string {
  if (e.isNetwork) return 'No connection';
  if (e.status === 403) return 'Not allowed';
  if (e.status === 404) return 'Not found';
  if (e.status === 409) return 'Cannot do that right now';
  if (e.status >= 500) return 'Server problem';
  return 'Please check';
}

export function LoadingState({ label = 'Loading…' }: { label?: string }) {
  return (
    <View className="items-center justify-center py-16" accessibilityLabel={label}>
      <ActivityIndicator size="large" color={colors.accent} />
      <Muted className="mt-3">{label}</Muted>
    </View>
  );
}

export function ErrorState({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const e = toApiError(error);
  return (
    <View className="items-center px-6 py-14">
      <View className="mb-3 h-14 w-14 items-center justify-center rounded-full bg-danger-soft">
        <Ionicons name={e.isNetwork ? 'cloud-offline' : 'alert-circle'} size={28} color={colors.danger} />
      </View>
      <H2 className="text-center">{bannerTitle(e)}</H2>
      <Body className="mt-1 text-center">{e.message}</Body>
      {onRetry ? <Button label="Try again" variant="secondary" size="md" icon="refresh" onPress={onRetry} className="mt-4" /> : null}
    </View>
  );
}

export function EmptyState({
  icon,
  title,
  message,
  action,
}: {
  icon: IconName;
  title: string;
  message?: string;
  action?: ReactNode;
}) {
  return (
    <View className="items-center px-6 py-12">
      <View className="mb-3 h-14 w-14 items-center justify-center rounded-full bg-white">
        <Ionicons name={icon} size={26} color={colors.ink3} />
      </View>
      <H2 className="text-center">{title}</H2>
      {message ? <Body className="mt-1 text-center">{message}</Body> : null}
      {action ? <View className="mt-4">{action}</View> : null}
    </View>
  );
}

export function Avatar({ label, tone = 'ink' }: { label: string; tone?: 'ink' | 'accent' | 'teal' }) {
  const bg = { ink: 'bg-ink', accent: 'bg-accent', teal: 'bg-teal' }[tone];
  return (
    <View className={`h-11 w-11 items-center justify-center rounded-full ${bg}`}>
      <Text className="text-base font-bold text-white">{label.toUpperCase()}</Text>
    </View>
  );
}

/** Large, spaced OTP digits: readable at arm's length by a driver in sunlight. */
export function OtpDigits({ code }: { code: string }) {
  return (
    <View className="flex-row justify-center" accessible accessibilityLabel={`Boarding code ${code.split('').join(' ')}`}>
      {code.split('').map((digit, i) => (
        <View key={i} className="mx-1.5 h-16 w-14 items-center justify-center rounded-xl bg-ink">
          <Text className="text-3xl font-bold text-white">{digit}</Text>
        </View>
      ))}
    </View>
  );
}

export function StatTile({ value, label, tone = 'ink' }: { value: string; label: string; tone?: 'ink' | 'accent' | 'teal' | 'danger' }) {
  const color = { ink: 'text-ink', accent: 'text-accent', teal: 'text-teal-deep', danger: 'text-danger' }[tone];
  return (
    <View className="mb-3 mr-3 min-w-[140px] flex-1 rounded-card border border-line bg-white p-4">
      <Text className={`text-3xl font-bold ${color}`}>{value}</Text>
      <Muted className="mt-1">{label}</Muted>
    </View>
  );
}

export function ProgressBar({ pct, tone = 'teal' }: { pct: number; tone?: 'teal' | 'amber' | 'danger' }) {
  const bar = { teal: 'bg-teal', amber: 'bg-amber', danger: 'bg-danger' }[tone];
  return (
    <View className="h-2 overflow-hidden rounded-full bg-mist">
      <View className={`h-2 rounded-full ${bar}`} style={{ width: `${Math.min(100, Math.max(0, pct))}%` }} />
    </View>
  );
}
