import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios';
import Constants from 'expo-constants';
import { Platform } from 'react-native';

import type { ErrorResponse } from './types';

/**
 * Where the API lives, in priority order:
 *  1. EXPO_PUBLIC_API_URL (set it for any shared or production build)
 *  2. On web: the page's own host, port 8080
 *  3. In Expo Go / a dev build: the machine running Metro (so a phone on the
 *     same Wi-Fi reaches the laptop without any configuration)
 *  4. Emulator fallbacks
 */
function resolveBaseUrl(): string {
  const fromEnv = process.env.EXPO_PUBLIC_API_URL;
  if (fromEnv) {
    return fromEnv.replace(/\/+$/, '');
  }
  if (Platform.OS === 'web' && typeof window !== 'undefined') {
    return `${window.location.protocol}//${window.location.hostname}:8080`;
  }
  const hostUri = Constants.expoConfig?.hostUri;
  const host = hostUri?.split(':')[0];
  if (host) {
    return `http://${host}:8080`;
  }
  return Platform.OS === 'android' ? 'http://10.0.2.2:8080' : 'http://localhost:8080';
}

export const API_BASE_URL = resolveBaseUrl();

/** One error type for the whole app. `message` is written by the server to be shown as-is. */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fieldErrors: { field: string; message: string }[];

  constructor(status: number, code: string, message: string, fieldErrors: { field: string; message: string }[] = []) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
  }

  get isNetwork(): boolean {
    return this.status === 0;
  }

  fieldError(field: string): string | undefined {
    return this.fieldErrors.find((f) => f.field === field)?.message;
  }
}

export const api = axios.create({
  baseURL: API_BASE_URL,
  timeout: 15_000,
  // Keep axios' default Accept ("application/json, text/plain, */*"): the CSV export
  // must stay reachable, and a JSON-only Accept header earns a 406 from it.
});

let getToken: () => string | null = () => null;
let onUnauthorized: () => void = () => undefined;

/** Called once by the AuthProvider so the client never imports React state. */
export function configureAuth(tokenGetter: () => string | null, unauthorizedHandler: () => void) {
  getToken = tokenGetter;
  onUnauthorized = unauthorizedHandler;
}

api.interceptors.request.use((config) => {
  const token = getToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

type RetriableConfig = InternalAxiosRequestConfig & { _authRetried?: boolean };

/** How long to wait before re-sending a request the server rejected with 401. */
const AUTH_RETRY_DELAY_MS = 1500;

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ErrorResponse>) => {
    const apiError = toApiError(error);
    const config = error.config as RetriableConfig | undefined;
    const isLogin = config?.url?.includes('/api/auth/login');
    if (apiError.status === 401 && !isLogin && config && getToken() && !config._authRetried) {
      // One 401 is not proof the session ended: while the server reloads user data
      // (a demo reset, a failover) a valid token is briefly rejected, and signing out
      // on that blip logged the whole desk out. A 401 means the request was refused
      // before any work was done, so re-sending it once is safe even for actions.
      config._authRetried = true;
      await new Promise((resolve) => setTimeout(resolve, AUTH_RETRY_DELAY_MS));
      return api.request(config);
    }
    if (apiError.status === 401 && !isLogin) {
      onUnauthorized();
    }
    return Promise.reject(apiError);
  },
);

export function toApiError(error: unknown): ApiError {
  if (error instanceof ApiError) {
    return error;
  }
  const axiosError = error as AxiosError<ErrorResponse>;
  if (axiosError?.response) {
    const { status, data } = axiosError.response;
    if (data && typeof data === 'object' && 'code' in data && 'message' in data) {
      return new ApiError(status, data.code, data.message, data.fieldErrors ?? []);
    }
    return new ApiError(status, `HTTP_${status}`, `The server answered with an unexpected error (${status}).`);
  }
  if (axiosError?.code === 'ECONNABORTED') {
    return new ApiError(0, 'TIMEOUT', 'The server took too long to answer. Check your connection and try again.');
  }
  if (axiosError?.isAxiosError) {
    return new ApiError(0, 'NETWORK', `Cannot reach Plant Ride at ${API_BASE_URL}. Check you are on the plant network.`);
  }
  return new ApiError(0, 'UNKNOWN', error instanceof Error ? error.message : 'Something went wrong.');
}

/**
 * The server builds tracking links from its configured public URL, which is
 * "localhost" on a laptop demo and unreachable from a phone. The app knows an
 * address that works, so it rebuilds the link from the token.
 */
export function trackingLink(token: string): string {
  return `${API_BASE_URL}/t/${token}`;
}
