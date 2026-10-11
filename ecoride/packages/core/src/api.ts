import axios, { AxiosError, AxiosInstance, InternalAxiosRequestConfig } from 'axios';

import type { ErrorResponse, FieldError } from './types';

/** One error type for every app. `message` is written by the server to be shown as-is. */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fieldErrors: FieldError[];

  constructor(status: number, code: string, message: string, fieldErrors: FieldError[] = []) {
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

export function toApiError(error: unknown, baseURL = ''): ApiError {
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
    return new ApiError(0, 'NETWORK', `Cannot reach EcoRide at ${baseURL}. Check your connection.`);
  }
  return new ApiError(0, 'UNKNOWN', error instanceof Error ? error.message : 'Something went wrong.');
}

export interface ClientOptions {
  baseURL: string;
  /** Read synchronously on every request, so the client never imports app state. */
  getToken: () => string | null;
  /** Called when the server rejects the token twice in a row. */
  onUnauthorized: () => void;
  timeoutMs?: number;
}

type RetriableConfig = InternalAxiosRequestConfig & { _authRetried?: boolean };

/** How long to wait before re-sending a request the server rejected with 401. */
const AUTH_RETRY_DELAY_MS = 1500;

/**
 * An axios instance with the bearer token, the error envelope turned into {@link ApiError},
 * and one retry on 401: a single rejection (e.g. while the demo data is rebuilt) must not sign
 * the user out.
 */
export function createClient({ baseURL, getToken, onUnauthorized, timeoutMs = 15_000 }: ClientOptions): AxiosInstance {
  const client = axios.create({ baseURL, timeout: timeoutMs });
  client.interceptors.request.use((config) => {
    const token = getToken();
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  });
  client.interceptors.response.use(
    (response) => response,
    async (error: AxiosError<ErrorResponse>) => {
      const apiError = toApiError(error, baseURL);
      const config = error.config as RetriableConfig | undefined;
      const isAuthCall = config?.url?.includes('/auth/');
      if (apiError.status === 401 && !isAuthCall && config && getToken() && !config._authRetried) {
        config._authRetried = true;
        await new Promise((resolve) => setTimeout(resolve, AUTH_RETRY_DELAY_MS));
        return client.request(config);
      }
      if (apiError.status === 401 && !isAuthCall) {
        onUnauthorized();
      }
      return Promise.reject(apiError);
    },
  );
  return client;
}
