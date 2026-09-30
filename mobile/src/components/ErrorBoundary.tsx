import { Ionicons } from '@expo/vector-icons';
import { Component, ErrorInfo, ReactNode } from 'react';
import { Pressable, Text, View } from 'react-native';

import { colors } from '@/lib/theme';

interface Props {
  children: ReactNode;
}

interface State {
  error: Error | null;
}

/**
 * Last line of defence for render crashes: a bug in one screen shows a
 * recoverable message instead of a blank app in front of the client.
 */
export class ErrorBoundary extends Component<Props, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    // Swap for a crash reporter (Sentry, Crashlytics) in production builds.
    console.error('Plant Ride render error', error, info.componentStack);
  }

  private reset = () => this.setState({ error: null });

  render() {
    if (!this.state.error) {
      return this.props.children;
    }
    return (
      <View className="flex-1 items-center justify-center bg-mist px-8">
        <View className="mb-4 h-16 w-16 items-center justify-center rounded-full bg-accent-soft">
          <Ionicons name="construct" size={30} color={colors.accent} />
        </View>
        <Text className="text-center text-xl font-bold text-ink">This screen hit a problem</Text>
        <Text className="mt-2 text-center text-base text-ink-2">
          Your bookings are safe on the server. Try again, or go back and reopen the screen.
        </Text>
        <Text className="mt-3 text-center text-xs text-ink-3" numberOfLines={3}>
          {this.state.error.message}
        </Text>
        <Pressable
          onPress={this.reset}
          accessibilityRole="button"
          className="mt-6 min-h-[48px] items-center justify-center rounded-xl bg-accent px-6 active:opacity-80"
        >
          <Text className="text-base font-bold text-white">Try again</Text>
        </Pressable>
      </View>
    );
  }
}
