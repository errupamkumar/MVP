import {
  createClient,
  type AdminAuthResponse,
  type AdminProfile,
  type AuditEntry,
  type Dashboard,
  type DriverDetail,
  type DriverRow,
  type FareBreakdown,
  type Finance,
  type LiveMap,
  type PageDto,
  type PaymentRow,
  type PromoDto,
  type RideDetail,
  type RideRow,
  type RiderDetail,
  type RiderRow,
  type SettingDto,
  type SosRow,
  type SurgeZoneDto,
  type VehicleTypeDto,
} from '@ecoride/core';
import type { AxiosResponse } from 'axios';

export const API_URL: string =
  (import.meta.env.VITE_API_URL as string | undefined)?.replace(/\/+$/, '') ??
  `${window.location.protocol}//${window.location.hostname}:9080`;

const KEY = 'ecoride.admin.session';

export interface AdminSession {
  token: string;
  expiresAt: string;
  admin: AdminProfile;
}

export function loadSession(): AdminSession | null {
  try {
    const raw = localStorage.getItem(KEY);
    const s = raw ? (JSON.parse(raw) as AdminSession) : null;
    return s && new Date(s.expiresAt).getTime() > Date.now() ? s : null;
  } catch {
    return null;
  }
}

export function saveSession(s: AdminSession | null) {
  try {
    if (s) localStorage.setItem(KEY, JSON.stringify(s));
    else localStorage.removeItem(KEY);
  } catch {
    // Storage blocked: the session lasts for this tab only.
  }
}

let current: AdminSession | null = loadSession();
let onUnauthorized: () => void = () => undefined;

export function setSession(s: AdminSession | null) {
  current = s;
  saveSession(s);
}

export function setUnauthorizedHandler(fn: () => void) {
  onUnauthorized = fn;
}

export const http = createClient({
  baseURL: API_URL,
  getToken: () => current?.token ?? null,
  onUnauthorized: () => onUnauthorized(),
});

const data = <T,>(p: Promise<AxiosResponse<T>>) => p.then((r) => r.data);

export interface RideQuery {
  q?: string;
  status?: string;
  vehicleType?: string;
  riderId?: number;
  driverId?: number;
  page?: number;
  size?: number;
}

export const api = {
  login: (email: string, password: string) => data(http.post<AdminAuthResponse>('/api/admin/auth/login', { email, password })),

  dashboard: () => data(http.get<Dashboard>('/api/admin/dashboard')),
  live: () => data(http.get<LiveMap>('/api/admin/live')),

  rides: (params: RideQuery) => data(http.get<PageDto<RideRow>>('/api/admin/rides', { params })),
  ride: (id: number) => data(http.get<RideDetail>(`/api/admin/rides/${id}`)),
  cancelRide: (id: number, reason: string) => data(http.post<RideDetail>(`/api/admin/rides/${id}/cancel`, { reason })),
  refund: (id: number, amount: number, note: string) => data(http.post<RideDetail>(`/api/admin/rides/${id}/refund`, { amount, note })),

  drivers: (params: { q?: string; status?: string; availability?: string; page?: number; size?: number }) =>
    data(http.get<PageDto<DriverRow>>('/api/admin/drivers', { params })),
  driverCounts: () => data(http.get<Record<string, number>>('/api/admin/drivers/counts')),
  driver: (id: number) => data(http.get<DriverDetail>(`/api/admin/drivers/${id}`)),
  driverAction: (id: number, action: 'approve' | 'reactivate' | 'offline' | 'payout') =>
    data(http.post<DriverDetail>(`/api/admin/drivers/${id}/${action}`)),
  driverReason: (id: number, action: 'reject' | 'suspend', reason: string) =>
    data(http.post<DriverDetail>(`/api/admin/drivers/${id}/${action}`, { reason })),

  riders: (params: { q?: string; page?: number; size?: number }) => data(http.get<PageDto<RiderRow>>('/api/admin/riders', { params })),
  rider: (id: number) => data(http.get<RiderDetail>(`/api/admin/riders/${id}`)),
  blockRider: (id: number, reason: string) => data(http.post<RiderDetail>(`/api/admin/riders/${id}/block`, { reason })),
  unblockRider: (id: number) => data(http.post<RiderDetail>(`/api/admin/riders/${id}/unblock`)),
  creditWallet: (id: number, amount: number, note: string) =>
    data(http.post<RiderDetail>(`/api/admin/riders/${id}/wallet-credit`, { amount, note })),

  vehicleTypes: () => data(http.get<VehicleTypeDto[]>('/api/admin/vehicle-types')),
  updateVehicleType: (code: string, body: Omit<VehicleTypeDto, 'code' | 'sortOrder' | 'updatedAt'>) =>
    data(http.put<VehicleTypeDto>(`/api/admin/vehicle-types/${code}`, body)),
  farePreview: (body: { vehicleType: string; distanceKm: number; minutes: number; surge?: number; waitingMin?: number; promoCode?: string }) =>
    data(http.post<FareBreakdown>('/api/admin/fare-preview', body)),

  zones: () => data(http.get<SurgeZoneDto[]>('/api/admin/surge-zones')),
  saveZone: (id: number | null, body: Omit<SurgeZoneDto, 'id' | 'currentMultiplier' | 'demand' | 'supply' | 'updatedAt'>) =>
    data(id == null ? http.post<SurgeZoneDto>('/api/admin/surge-zones', body) : http.put<SurgeZoneDto>(`/api/admin/surge-zones/${id}`, body)),
  deleteZone: (id: number) => http.delete(`/api/admin/surge-zones/${id}`),

  promos: () => data(http.get<PromoDto[]>('/api/admin/promos')),
  savePromo: (body: Omit<PromoDto, 'totalUses' | 'createdAt'>) => data(http.post<PromoDto>('/api/admin/promos', body)),

  settings: () => data(http.get<SettingDto[]>('/api/admin/settings')),
  updateSetting: (key: string, value: string) => data(http.put<SettingDto>(`/api/admin/settings/${key}`, { value })),

  finance: (days: number) => data(http.get<Finance>('/api/admin/finance', { params: { days } })),
  payments: (page = 0) => data(http.get<PageDto<PaymentRow>>('/api/admin/payments', { params: { page, size: 50 } })),
  sos: () => data(http.get<SosRow[]>('/api/admin/sos')),
  resolveSos: (id: number, note: string) => data(http.post<SosRow[]>(`/api/admin/sos/${id}/resolve`, { note })),
  audit: () => data(http.get<AuditEntry[]>('/api/admin/audit')),
  resetDemo: () => data(http.post<{ ok: boolean; tookMs: number }>('/api/admin/demo/reset')),
};
