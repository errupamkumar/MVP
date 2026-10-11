import {
  createClient,
  type DriverAuthResponse,
  type DriverProfile,
  type DriverRide,
  type DriverState,
  type Earnings,
  type PublicConfig,
  type RideSummary,
} from '@ecoride/core';
import { createAuth, resolveApiUrl } from '@ecoride/mobile';
import type { AxiosResponse } from 'axios';

export const API_URL = resolveApiUrl(process.env.EXPO_PUBLIC_API_URL);

/** Driver sessions are stored apart from rider sessions, even on the same device. */
export const auth = createAuth<DriverProfile>('ecoride.driver.session');

export const http = createClient({ baseURL: API_URL, getToken: auth.getToken, onUnauthorized: auth.notifyUnauthorized });

const data = <T,>(p: Promise<AxiosResponse<T>>) => p.then((r) => r.data);

export interface DriverSignup {
  name: string;
  phone: string;
  email?: string;
  password: string;
  licenseNo: string;
  vehicleType: string;
  vehicleMake: string;
  vehicleModel: string;
  vehicleColor: string;
  vehiclePlate: string;
  vehicleYear: number;
}

export interface Ping {
  lat: number;
  lng: number;
  heading?: number | null;
  speedKmh?: number | null;
  accuracyM?: number | null;
  simulated?: boolean;
}

export const api = {
  config: () => data(http.get<PublicConfig>('/api/public/config')),
  signup: (body: DriverSignup) => data(http.post<DriverAuthResponse>('/api/driver/auth/signup', body)),
  login: (login: string, password: string) => data(http.post<DriverAuthResponse>('/api/driver/auth/login', { login, password })),
  me: () => data(http.get<DriverProfile>('/api/driver/me')),
  state: () => data(http.get<DriverState>('/api/driver/state')),
  online: (at: { lat: number; lng: number } | null) => data(http.post<DriverState>('/api/driver/online', at ?? {})),
  offline: () => data(http.post<DriverState>('/api/driver/offline')),
  ping: (p: Ping) => http.post('/api/driver/location', p),
  accept: (offerId: number) => data(http.post<DriverRide>(`/api/driver/offers/${offerId}/accept`)),
  decline: (offerId: number) => http.post(`/api/driver/offers/${offerId}/decline`),
  ride: (id: number) => data(http.get<DriverRide>(`/api/driver/rides/${id}`)),
  arrived: (id: number) => data(http.post<DriverRide>(`/api/driver/rides/${id}/arrived`)),
  start: (id: number, otp: string) => data(http.post<DriverRide>(`/api/driver/rides/${id}/start`, { otp })),
  complete: (id: number) => data(http.post<DriverRide>(`/api/driver/rides/${id}/complete`)),
  cashCollected: (id: number) => data(http.post<DriverRide>(`/api/driver/rides/${id}/cash-collected`)),
  cancel: (id: number, reason: string) => data(http.post<DriverRide>(`/api/driver/rides/${id}/cancel`, { reason })),
  rateRider: (id: number, stars: number) => data(http.post<DriverRide>(`/api/driver/rides/${id}/rate`, { stars })),
  sos: (id: number, body: { lat?: number; lng?: number; note?: string }) =>
    data(http.post<{ id: number; message: string }>(`/api/driver/rides/${id}/sos`, body)),
  history: (page = 0) => data(http.get<RideSummary[]>('/api/driver/rides', { params: { page } })),
  earnings: () => data(http.get<Earnings>('/api/driver/earnings')),
};
