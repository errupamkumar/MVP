import { Ionicons } from '@expo/vector-icons';
import { ComponentProps, ReactNode, useState } from 'react';
import {
  ActivityIndicator,
  Pressable,
  ScrollView,
  StyleProp,
  StyleSheet,
  Text,
  TextInput,
  TextInputProps,
  TextStyle,
  View,
  ViewStyle,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { colors, font, radius, shadow, space } from './theme';

export type IconName = ComponentProps<typeof Ionicons>['name'];
export { Ionicons };

// ------------------------------------------------------------------ text

type TextVariant = 'display' | 'title' | 'h1' | 'h2' | 'body' | 'bodyStrong' | 'muted' | 'small' | 'label' | 'mono';

const TEXT: Record<TextVariant, TextStyle> = {
  display: { fontSize: 34, fontWeight: font.heavy, color: colors.ink, letterSpacing: -0.5 },
  title: { fontSize: 26, fontWeight: font.bold, color: colors.ink, letterSpacing: -0.3 },
  h1: { fontSize: 20, fontWeight: font.bold, color: colors.ink },
  h2: { fontSize: 17, fontWeight: font.semibold, color: colors.ink },
  body: { fontSize: 15, color: colors.ink2, lineHeight: 21 },
  bodyStrong: { fontSize: 15, color: colors.ink, fontWeight: font.semibold },
  muted: { fontSize: 14, color: colors.ink3, lineHeight: 19 },
  small: { fontSize: 12, color: colors.ink3 },
  label: { fontSize: 12, color: colors.ink3, fontWeight: font.semibold, letterSpacing: 0.6, textTransform: 'uppercase' },
  mono: { fontSize: 15, color: colors.ink, fontFamily: font.mono },
};

export function T({
  children,
  v = 'body',
  style,
  numberOfLines,
  selectable,
  testID,
}: {
  children: ReactNode;
  v?: TextVariant;
  style?: StyleProp<TextStyle>;
  numberOfLines?: number;
  selectable?: boolean;
  testID?: string;
}) {
  return (
    <Text style={[TEXT[v], style]} numberOfLines={numberOfLines} selectable={selectable} testID={testID}>
      {children}
    </Text>
  );
}

// ------------------------------------------------------------------ layout

export function Screen({
  children,
  scroll = true,
  footer,
  background = colors.canvas,
  padded = true,
  topInset = true,
}: {
  children: ReactNode;
  scroll?: boolean;
  footer?: ReactNode;
  background?: string;
  padded?: boolean;
  topInset?: boolean;
}) {
  const insets = useSafeAreaInsets();
  const body = <View style={[styles.screenInner, padded && { paddingHorizontal: space.lg }]}>{children}</View>;
  return (
    <View style={{ flex: 1, backgroundColor: background, paddingTop: topInset ? insets.top : 0 }}>
      {scroll ? (
        <ScrollView style={{ flex: 1 }} contentContainerStyle={{ paddingBottom: space.xxl }} keyboardShouldPersistTaps="handled">
          {body}
        </ScrollView>
      ) : (
        <View style={{ flex: 1 }}>{body}</View>
      )}
      {footer ? (
        <View style={[styles.footer, { paddingBottom: Math.max(insets.bottom, space.md) }]}>
          <View style={styles.screenInner}>{footer}</View>
        </View>
      ) : null}
    </View>
  );
}

export function Header({ title, onBack, right }: { title?: string; onBack?: () => void; right?: ReactNode }) {
  return (
    <View style={styles.header}>
      {onBack ? <IconButton icon="arrow-back" onPress={onBack} label="Back" flat /> : <View style={{ width: 40 }} />}
      <T v="h2" style={{ flex: 1, textAlign: 'center' }} numberOfLines={1}>
        {title ?? ''}
      </T>
      <View style={{ minWidth: 40, alignItems: 'flex-end' }}>{right}</View>
    </View>
  );
}

export function Row({ children, style, gap = space.sm }: { children: ReactNode; style?: StyleProp<ViewStyle>; gap?: number }) {
  return <View style={[{ flexDirection: 'row', alignItems: 'center', gap }, style]}>{children}</View>;
}

export function Card({ children, style, onPress }: { children: ReactNode; style?: StyleProp<ViewStyle>; onPress?: () => void }) {
  if (onPress) {
    return (
      <Pressable onPress={onPress} accessibilityRole="button" style={({ pressed }) => [styles.card, pressed && { opacity: 0.85 }, style]}>
        {children}
      </Pressable>
    );
  }
  return <View style={[styles.card, style]}>{children}</View>;
}

export function Divider({ style }: { style?: StyleProp<ViewStyle> }) {
  return <View style={[{ height: StyleSheet.hairlineWidth, backgroundColor: colors.line, marginVertical: space.md }, style]} />;
}

/** The white panel that slides over the map, as in every ride-hailing app. */
export function Sheet({ children, style }: { children: ReactNode; style?: StyleProp<ViewStyle> }) {
  const insets = useSafeAreaInsets();
  return (
    <View style={[styles.sheet, { paddingBottom: Math.max(insets.bottom, space.lg) }, style]}>
      <View style={styles.grabber} />
      {children}
    </View>
  );
}

// ------------------------------------------------------------------ controls

type ButtonVariant = 'primary' | 'brand' | 'secondary' | 'ghost' | 'danger' | 'light';

const BUTTON: Record<ButtonVariant, { bg: string; fg: string; border?: string }> = {
  primary: { bg: colors.ink, fg: colors.white },
  brand: { bg: colors.brand, fg: colors.white },
  secondary: { bg: colors.mist, fg: colors.ink },
  ghost: { bg: 'transparent', fg: colors.ink },
  danger: { bg: colors.danger, fg: colors.white },
  light: { bg: colors.white, fg: colors.ink, border: colors.line },
};

export function Button({
  label,
  onPress,
  variant = 'primary',
  icon,
  loading = false,
  disabled = false,
  size = 'lg',
  style,
  testID,
}: {
  label: string;
  onPress?: () => void;
  variant?: ButtonVariant;
  icon?: IconName;
  loading?: boolean;
  disabled?: boolean;
  size?: 'sm' | 'md' | 'lg';
  style?: StyleProp<ViewStyle>;
  testID?: string;
}) {
  const v = BUTTON[variant];
  const inactive = disabled || loading;
  const height = size === 'lg' ? 54 : size === 'md' ? 44 : 34;
  return (
    <Pressable
      testID={testID}
      accessibilityRole="button"
      accessibilityLabel={label}
      accessibilityState={{ disabled: inactive, busy: loading }}
      onPress={inactive ? undefined : onPress}
      style={({ pressed }) => [
        styles.button,
        { backgroundColor: v.bg, height, borderColor: v.border ?? 'transparent', borderWidth: v.border ? 1 : 0 },
        size === 'sm' && { paddingHorizontal: space.md, borderRadius: radius.sm },
        inactive && { opacity: 0.45 },
        pressed && !inactive && { opacity: 0.85 },
        style,
      ]}
    >
      {loading ? (
        <ActivityIndicator color={v.fg} />
      ) : (
        <>
          {icon ? <Ionicons name={icon} size={size === 'sm' ? 15 : 19} color={v.fg} /> : null}
          <Text style={{ color: v.fg, fontWeight: font.bold, fontSize: size === 'sm' ? 13 : 16 }}>{label}</Text>
        </>
      )}
    </Pressable>
  );
}

export function IconButton({
  icon,
  onPress,
  label,
  flat = false,
  color = colors.ink,
  size = 40,
  style,
}: {
  icon: IconName;
  onPress?: () => void;
  label: string;
  flat?: boolean;
  color?: string;
  size?: number;
  style?: StyleProp<ViewStyle>;
}) {
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={label}
      hitSlop={6}
      style={({ pressed }) => [
        { width: size, height: size, borderRadius: size / 2, alignItems: 'center', justifyContent: 'center' },
        !flat && { backgroundColor: colors.white, ...shadow(1) },
        pressed && { opacity: 0.7 },
        style,
      ]}
    >
      <Ionicons name={icon} size={Math.round(size * 0.5)} color={color} />
    </Pressable>
  );
}

export function TextField({
  label,
  error,
  icon,
  secure,
  style,
  ...props
}: TextInputProps & { label?: string; error?: string | null; icon?: IconName; secure?: boolean }) {
  const [hidden, setHidden] = useState(!!secure);
  const [focused, setFocused] = useState(false);
  return (
    <View style={{ marginBottom: space.md }}>
      {label ? (
        <T v="small" style={{ marginBottom: 6, fontWeight: font.semibold, color: colors.ink2 }}>
          {label}
        </T>
      ) : null}
      <View
        style={[
          styles.input,
          focused && { borderColor: colors.ink, backgroundColor: colors.white },
          !!error && { borderColor: colors.danger },
        ]}
      >
        {icon ? <Ionicons name={icon} size={18} color={colors.ink3} /> : null}
        <TextInput
          placeholderTextColor={colors.ink4}
          secureTextEntry={hidden}
          onFocus={(e) => {
            setFocused(true);
            props.onFocus?.(e);
          }}
          onBlur={(e) => {
            setFocused(false);
            props.onBlur?.(e);
          }}
          style={[styles.inputText, style]}
          {...props}
        />
        {secure ? (
          <Pressable onPress={() => setHidden((h) => !h)} accessibilityLabel={hidden ? 'Show password' : 'Hide password'} hitSlop={8}>
            <Ionicons name={hidden ? 'eye-outline' : 'eye-off-outline'} size={18} color={colors.ink3} />
          </Pressable>
        ) : null}
      </View>
      {error ? (
        <T v="small" style={{ color: colors.danger, marginTop: 4 }}>
          {error}
        </T>
      ) : null}
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
  selected?: boolean;
  onPress?: () => void;
  icon?: IconName;
}) {
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityState={{ selected: !!selected }}
      style={[styles.chip, selected && { backgroundColor: colors.ink, borderColor: colors.ink }]}
    >
      {icon ? <Ionicons name={icon} size={14} color={selected ? colors.white : colors.ink2} /> : null}
      <Text style={{ color: selected ? colors.white : colors.ink2, fontWeight: font.semibold, fontSize: 13 }}>{label}</Text>
    </Pressable>
  );
}

// ------------------------------------------------------------------ display

export function Avatar({ name, size = 44, color = colors.ink }: { name: string; size?: number; color?: string }) {
  const parts = name.trim().split(/\s+/);
  const letters = ((parts[0]?.[0] ?? '') + (parts.length > 1 ? parts[parts.length - 1][0] : '')).toUpperCase();
  return (
    <View style={{ width: size, height: size, borderRadius: size / 2, backgroundColor: color, alignItems: 'center', justifyContent: 'center' }}>
      <Text style={{ color: colors.white, fontWeight: font.bold, fontSize: size * 0.38 }}>{letters}</Text>
    </View>
  );
}

export function Badge({
  label,
  tone = 'neutral',
  icon,
}: {
  label: string;
  tone?: 'neutral' | 'brand' | 'info' | 'warn' | 'danger' | 'dark';
  icon?: IconName;
}) {
  const palette = {
    neutral: [colors.mist, colors.ink2],
    brand: [colors.brandSoft, colors.brandDark],
    info: [colors.infoSoft, colors.info],
    warn: [colors.warnSoft, colors.warn],
    danger: [colors.dangerSoft, colors.danger],
    dark: [colors.ink, colors.white],
  }[tone];
  return (
    <View style={[styles.badge, { backgroundColor: palette[0] }]}>
      {icon ? <Ionicons name={icon} size={12} color={palette[1]} /> : null}
      <Text style={{ color: palette[1], fontSize: 12, fontWeight: font.bold }}>{label}</Text>
    </View>
  );
}

export function Banner({
  tone = 'info',
  title,
  message,
  icon,
  action,
}: {
  tone?: 'info' | 'warn' | 'danger' | 'brand';
  title?: string;
  message: string;
  icon?: IconName;
  action?: ReactNode;
}) {
  const [bg, fg] = {
    info: [colors.infoSoft, colors.info],
    warn: [colors.warnSoft, colors.warn],
    danger: [colors.dangerSoft, colors.danger],
    brand: [colors.brandSoft, colors.brandDark],
  }[tone];
  return (
    <View style={[styles.banner, { backgroundColor: bg }]}>
      <Ionicons name={icon ?? (tone === 'danger' ? 'alert-circle' : tone === 'warn' ? 'warning' : 'information-circle')} size={20} color={fg} />
      <View style={{ flex: 1 }}>
        {title ? <Text style={{ color: fg, fontWeight: font.bold, fontSize: 14, marginBottom: 2 }}>{title}</Text> : null}
        <Text style={{ color: colors.ink2, fontSize: 13, lineHeight: 18 }}>{message}</Text>
        {action ? <View style={{ marginTop: space.sm }}>{action}</View> : null}
      </View>
    </View>
  );
}

export function Stars({
  value,
  size = 16,
  onChange,
}: {
  value: number;
  size?: number;
  onChange?: (stars: number) => void;
}) {
  return (
    <Row gap={onChange ? space.sm : 2}>
      {[1, 2, 3, 4, 5].map((n) => {
        const icon: IconName = value >= n ? 'star' : value >= n - 0.5 ? 'star-half' : 'star-outline';
        const star = <Ionicons name={icon} size={size} color={colors.gold} />;
        return onChange ? (
          <Pressable key={n} onPress={() => onChange(n)} accessibilityLabel={`${n} star${n > 1 ? 's' : ''}`} hitSlop={4}>
            {star}
          </Pressable>
        ) : (
          <View key={n}>{star}</View>
        );
      })}
    </Row>
  );
}

export function KeyValue({ label, value, strong, style }: { label: string; value: ReactNode; strong?: boolean; style?: StyleProp<ViewStyle> }) {
  return (
    <Row style={[{ justifyContent: 'space-between', paddingVertical: 6 }, style]}>
      <T v="muted" style={strong ? { color: colors.ink, fontWeight: font.bold } : undefined}>
        {label}
      </T>
      {typeof value === 'string' || typeof value === 'number' ? (
        <T v={strong ? 'bodyStrong' : 'body'} style={strong ? { fontSize: 17 } : { color: colors.ink }}>
          {value}
        </T>
      ) : (
        value
      )}
    </Row>
  );
}

export function ListRow({
  icon,
  iconBg = colors.mist,
  iconColor = colors.ink,
  title,
  subtitle,
  right,
  onPress,
}: {
  icon?: IconName;
  iconBg?: string;
  iconColor?: string;
  title: string;
  subtitle?: string;
  right?: ReactNode;
  onPress?: () => void;
}) {
  const content = (
    <Row gap={space.md} style={{ paddingVertical: space.md }}>
      {icon ? (
        <View style={{ width: 40, height: 40, borderRadius: 20, backgroundColor: iconBg, alignItems: 'center', justifyContent: 'center' }}>
          <Ionicons name={icon} size={19} color={iconColor} />
        </View>
      ) : null}
      <View style={{ flex: 1 }}>
        <T v="bodyStrong" numberOfLines={1}>
          {title}
        </T>
        {subtitle ? (
          <T v="muted" numberOfLines={1} style={{ marginTop: 1 }}>
            {subtitle}
          </T>
        ) : null}
      </View>
      {right}
    </Row>
  );
  return onPress ? (
    <Pressable onPress={onPress} accessibilityRole="button" style={({ pressed }) => pressed && { opacity: 0.6 }}>
      {content}
    </Pressable>
  ) : (
    content
  );
}

export function Loading({ label }: { label?: string }) {
  return (
    <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center', padding: space.xl, gap: space.md }}>
      <ActivityIndicator color={colors.ink} size="large" />
      {label ? <T v="muted">{label}</T> : null}
    </View>
  );
}

export function Empty({ icon = 'file-tray-outline', title, message }: { icon?: IconName; title: string; message?: string }) {
  return (
    <View style={{ alignItems: 'center', padding: space.xxl, gap: space.sm }}>
      <Ionicons name={icon} size={36} color={colors.ink4} />
      <T v="h2">{title}</T>
      {message ? <T v="muted" style={{ textAlign: 'center' }}>{message}</T> : null}
    </View>
  );
}

/** Pickup (green dot) and drop (black square) with the addresses, as on every ride card. */
export function RouteSummary({ pickup, drop, compact }: { pickup: string; drop: string; compact?: boolean }) {
  return (
    <View style={{ flexDirection: 'row', gap: space.md }}>
      <View style={{ alignItems: 'center', paddingTop: 5 }}>
        <View style={{ width: 10, height: 10, borderRadius: 5, backgroundColor: colors.brand }} />
        <View style={{ width: 2, flex: 1, backgroundColor: colors.line, marginVertical: 3, minHeight: compact ? 12 : 18 }} />
        <View style={{ width: 10, height: 10, backgroundColor: colors.ink }} />
      </View>
      <View style={{ flex: 1, gap: compact ? 8 : 14 }}>
        <T v={compact ? 'body' : 'bodyStrong'} numberOfLines={1}>
          {pickup}
        </T>
        <T v={compact ? 'body' : 'bodyStrong'} numberOfLines={1}>
          {drop}
        </T>
      </View>
    </View>
  );
}

export function SectionLabel({ children, style }: { children: ReactNode; style?: StyleProp<TextStyle> }) {
  return (
    <T v="label" style={[{ marginTop: space.xl, marginBottom: space.sm }, style]}>
      {children}
    </T>
  );
}

const styles = StyleSheet.create({
  screenInner: { width: '100%', maxWidth: 560, alignSelf: 'center' },
  footer: { borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: colors.line, backgroundColor: colors.white, paddingHorizontal: space.lg, paddingTop: space.md },
  header: { flexDirection: 'row', alignItems: 'center', paddingVertical: space.sm, marginBottom: space.sm, gap: space.sm },
  card: { backgroundColor: colors.white, borderRadius: radius.lg, padding: space.lg, ...shadow(1) },
  sheet: {
    backgroundColor: colors.white,
    borderTopLeftRadius: radius.xl,
    borderTopRightRadius: radius.xl,
    paddingHorizontal: space.lg,
    paddingTop: space.sm,
    ...shadow(3),
  },
  grabber: { alignSelf: 'center', width: 40, height: 4, borderRadius: 2, backgroundColor: colors.line, marginBottom: space.md },
  button: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: space.sm, borderRadius: radius.md, paddingHorizontal: space.lg },
  input: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space.sm,
    backgroundColor: colors.mist,
    borderRadius: radius.md,
    borderWidth: 1.5,
    borderColor: 'transparent',
    paddingHorizontal: space.md,
    minHeight: 50,
  },
  inputText: { flex: 1, fontSize: 16, color: colors.ink, paddingVertical: 12, outlineWidth: 0 },
  chip: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    paddingHorizontal: space.md,
    paddingVertical: 8,
    borderRadius: radius.pill,
    borderWidth: 1,
    borderColor: colors.line,
    backgroundColor: colors.white,
  },
  badge: { flexDirection: 'row', alignItems: 'center', gap: 4, paddingHorizontal: 8, paddingVertical: 3, borderRadius: radius.pill, alignSelf: 'flex-start' },
  banner: { flexDirection: 'row', gap: space.md, padding: space.md, borderRadius: radius.md, alignItems: 'flex-start' },
});
