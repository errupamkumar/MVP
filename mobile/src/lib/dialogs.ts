import { Alert, Platform } from 'react-native';

/**
 * React Native Web implements Alert.alert as a no-op, so a confirm dialog
 * built on it silently does nothing on the desk's web console. These helpers
 * use the browser's dialogs on web and native alerts elsewhere.
 */
export function confirmAction(
  title: string,
  message: string,
  confirmLabel = 'Confirm',
  destructive = false,
): Promise<boolean> {
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
