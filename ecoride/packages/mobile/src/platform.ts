import Constants from 'expo-constants';
import { Alert, Linking, Platform } from 'react-native';

/**
 * Where the EcoRide API lives, in priority order:
 *  1. EXPO_PUBLIC_API_URL (any shared or production build)
 *  2. On web: the page's own host, port 9080
 *  3. In Expo Go: the laptop running Metro, port 9080 (a phone on the same Wi-Fi needs no setup)
 */
export function resolveApiUrl(envUrl: string | undefined, port = 9080): string {
  if (envUrl) return envUrl.replace(/\/+$/, '');
  if (Platform.OS === 'web' && typeof window !== 'undefined') {
    return `${window.location.protocol}//${window.location.hostname}:${port}`;
  }
  const host = Constants.expoConfig?.hostUri?.split(':')[0];
  if (host) return `http://${host}:${port}`;
  return Platform.OS === 'android' ? `http://10.0.2.2:${port}` : `http://localhost:${port}`;
}

/** React Native Web's Alert.alert does nothing, so dialogs use the browser's on web. */
export function confirmAction(title: string, message: string, confirmLabel = 'Confirm', destructive = false): Promise<boolean> {
  if (Platform.OS === 'web') {
    return Promise.resolve(typeof window !== 'undefined' && window.confirm(`${title}\n\n${message}`));
  }
  return new Promise((resolve) => {
    Alert.alert(
      title,
      message,
      [
        { text: 'Cancel', style: 'cancel', onPress: () => resolve(false) },
        { text: confirmLabel, style: destructive ? 'destructive' : 'default', onPress: () => resolve(true) },
      ],
      { cancelable: true, onDismiss: () => resolve(false) },
    );
  });
}

export function showMessage(title: string, message: string): void {
  if (Platform.OS === 'web') {
    if (typeof window !== 'undefined') window.alert(`${title}\n\n${message}`);
    return;
  }
  Alert.alert(title, message);
}

export function callPhone(phone: string) {
  void Linking.openURL(`tel:${phone}`).catch(() => showMessage('Call', `Call ${phone}`));
}

/** Opens turn-by-turn navigation in the phone's maps app (Google Maps on web and Android). */
export function openNavigation(lat: number, lng: number) {
  const url =
    Platform.OS === 'ios'
      ? `http://maps.apple.com/?daddr=${lat},${lng}&dirflg=d`
      : `https://www.google.com/maps/dir/?api=1&destination=${lat},${lng}&travelmode=driving`;
  void Linking.openURL(url);
}
