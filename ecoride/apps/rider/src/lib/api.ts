import {
  createClient,
  type LatLng,
  type Nearby,
  type PaymentMethod,
  type Place,
  type PlaceResult,
  type PublicConfig,
  type QuoteResponse,
  type RideSummary,
  type RiderAuthResponse,
  type RiderProfile,
  type RiderRide,
  type SavedPlace,
  type Wallet,
} from '@ecoride/core';
import { createAuth, resolveApiUrl } from '@ecoride/mobile';
import type { AxiosResponse } from 'axios';

export const API_URL = resolveApiUrl(process.env.EXPO_PUBLIC_API_URL);

/** Rider sessions are stored apart from driver sessions, even on the same device. */
export const auth = createAuth<RiderProfile>('ecoride.rider.session');

export const http = createClient({ baseURL: API_URL, getToken: auth.getToken, onUnauthorized: auth.notifyUnauthorized });

const data = <T,>(p: Promise<AxiosResponse<T>>) => p.then((r) => r.data);

export const api = {
  config: () => data(http.get<PublicConfig>('/api/public/config')),

  signup: (body: { name: string; phone: string; email?: string; password: string }) =>
    data(http.post<RiderAuthResponse>('/api/rider/auth/signup', body)),
  login: (login: string, password: string) => data(http.post<RiderAuthResponse>('/api/rider/auth/login', { login, password })),
  me: () => data(http.get<RiderProfile>('/api/rider/me')),
  updateMe: (body: { name: string; email?: string | null }) => data(http.put<RiderProfile>('/api/rider/me', body)),

  places: () => data(http.get<SavedPlace[]>('/api/rider/places')),
  savePlace: (body: Omit<SavedPlace, 'id'>) => data(http.post<SavedPlace>('/api/rider/places', body)),
  deletePlace: (id: number) => http.delete(`/api/rider/places/${id}`),
  wallet: () => data(http.get<Wallet>('/api/rider/wallet')),
  topUp: (amount: number) => data(http.post<Wallet>('/api/rider/wallet/topup', { amount })),

  search: (q: string, near: LatLng | null) =>
    data(http.get<PlaceResult[]>('/api/geo/search', { params: { q, lat: near?.lat, lng: near?.lng } })),
  reverse: (p: LatLng) => data(http.get<PlaceResult>('/api/geo/reverse', { params: { lat: p.lat, lng: p.lng } })),
  nearby: (p: LatLng) => data(http.get<Nearby>('/api/rider/nearby', { params: { lat: p.lat, lng: p.lng } })),

  quote: (pickup: Place, drop: Place, promoCode?: string | null) =>
    data(http.post<QuoteResponse>('/api/rider/quotes', { pickup, drop, promoCode: promoCode || undefined })),
  request: (quoteId: string, vehicleType: string, paymentMethod: PaymentMethod) =>
    data(http.post<RiderRide>('/api/rider/rides', { quoteId, vehicleType, paymentMethod })),
  current: () => data(http.get<{ ride: RiderRide | null }>('/api/rider/rides/current')),
  ride: (id: number) => data(http.get<RiderRide>(`/api/rider/rides/${id}`)),
  history: (page = 0) => data(http.get<RideSummary[]>('/api/rider/rides', { params: { page } })),
  cancel: (id: number, reason: string) => data(http.post<RiderRide>(`/api/rider/rides/${id}/cancel`, { reason })),
  shareLocation: (id: number, p: LatLng & { accuracyM?: number | null }) =>
    http.post(`/api/rider/rides/${id}/location`, { lat: p.lat, lng: p.lng, accuracyM: p.accuracyM ?? undefined }),
  rate: (id: number, body: { stars: number; tags: string[]; comment?: string; tip: number }) =>
    data(http.post<RiderRide>(`/api/rider/rides/${id}/rate`, body)),
  sos: (id: number, body: { lat?: number; lng?: number; note?: string }) =>
    data(http.post<{ id: number; message: string }>(`/api/rider/rides/${id}/sos`, body)),
};
