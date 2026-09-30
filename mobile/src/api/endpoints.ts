import { api } from './client';
import type {
  AlertDto,
  AuditLogDto,
  BillingSummaryDto,
  BookingDto,
  BookingScope,
  BookingSummaryDto,
  CandidateDto,
  ComplianceItemDto,
  CreateBookingRequest,
  DeskActionResultDto,
  DestinationDto,
  DriverProfileDto,
  DriverQueueResponse,
  DutyDto,
  DutySummaryDto,
  EmployeeProfileDto,
  EndDutyRequest,
  IssueResultDto,
  LiveBoardDto,
  LocationAckDto,
  LocationPingRequest,
  LoginResponse,
  NotificationDto,
  QueueItemDto,
  RateBookingRequest,
  ReportIssueRequest,
  ResequenceRequest,
  RideOptionsRequest,
  RideOptionsResponse,
  RiderHomeDto,
  RouteDto,
  SosResultDto,
  StartDutyRequest,
  StopDto,
  UpdateRouteRulesRequest,
  UserSummary,
  VehicleOptionDto,
} from './types';

const get = async <T>(url: string, params?: Record<string, unknown>) => (await api.get<T>(url, { params })).data;
const post = async <T>(url: string, body?: unknown, headers?: Record<string, string>) =>
  (await api.post<T>(url, body ?? {}, { headers })).data;
const put = async <T>(url: string, body: unknown) => (await api.put<T>(url, body)).data;

export const authApi = {
  login: (username: string, password: string) => post<LoginResponse>('/api/auth/login', { username, password }),
  me: () => get<UserSummary>('/api/auth/me'),
};

export const masterApi = {
  stops: () => get<StopDto[]>('/api/stops'),
  routes: () => get<RouteDto[]>('/api/routes'),
};

export const riderApi = {
  home: () => get<RiderHomeDto>('/api/rider/home'),
  profile: () => get<EmployeeProfileDto>('/api/rider/profile'),
  destinations: (fromStopId: number) => get<DestinationDto[]>('/api/rider/destinations', { fromStopId }),
  rideOptions: (body: RideOptionsRequest) => post<RideOptionsResponse>('/api/rider/ride-options', body),
  createBooking: (body: CreateBookingRequest, idempotencyKey: string) =>
    post<BookingDto>('/api/rider/bookings', body, { 'Idempotency-Key': idempotencyKey }),
  bookings: (scope: BookingScope) => get<BookingSummaryDto[]>('/api/rider/bookings', { scope }),
  booking: (id: number) => get<BookingDto>(`/api/rider/bookings/${id}`),
  cancel: (id: number, reason?: string) => post<BookingDto>(`/api/rider/bookings/${id}/cancel`, { reason: reason ?? null }),
  rate: (id: number, body: RateBookingRequest) => post<BookingDto>(`/api/rider/bookings/${id}/rating`, body),
  sos: (bookingId: number | null, note?: string) =>
    post<SosResultDto>('/api/rider/sos', { bookingId, latitude: null, longitude: null, note: note ?? null }),
};

export const notificationApi = {
  list: () => get<NotificationDto[]>('/api/notifications', { limit: 50 }),
  readAll: () => post<{ updated: number }>('/api/notifications/read-all'),
};

export const driverApi = {
  profile: () => get<DriverProfileDto>('/api/driver/profile'),
  vehicles: () => get<VehicleOptionDto[]>('/api/driver/vehicles'),
  startDuty: (body: StartDutyRequest) => post<DutyDto>('/api/driver/duty/start', body),
  endDuty: (body: EndDutyRequest) => post<DutySummaryDto>('/api/driver/duty/end', body),
  queue: () => get<DriverQueueResponse>('/api/driver/queue'),
  arrive: (tripId: number, stopSeq: number) =>
    post<DriverQueueResponse>(`/api/driver/trips/${tripId}/arrive`, { stopSeq }),
  board: (tripId: number, bookingId: number, otp: string) =>
    post<DriverQueueResponse>(`/api/driver/trips/${tripId}/board`, { bookingId, otp }),
  noShow: (tripId: number, bookingId: number) =>
    post<DriverQueueResponse>(`/api/driver/trips/${tripId}/no-show`, { bookingId }),
  location: (body: LocationPingRequest) => post<LocationAckDto>('/api/driver/location', body),
  reportIssue: (body: ReportIssueRequest) => post<IssueResultDto>('/api/driver/issues', body),
  sos: (note?: string) => post<IssueResultDto>('/api/driver/sos', { latitude: null, longitude: null, note: note ?? null }),
};

export const deskApi = {
  board: () => get<LiveBoardDto>('/api/desk/board'),
  queue: () => get<QueueItemDto[]>('/api/desk/queue'),
  acknowledge: (alertId: number) => post<AlertDto>(`/api/desk/alerts/${alertId}/ack`),
  resolve: (alertId: number) => post<AlertDto>(`/api/desk/alerts/${alertId}/resolve`),
  candidates: (bookingId: number) => get<CandidateDto[]>(`/api/desk/bookings/${bookingId}/candidates`),
  approve: (bookingId: number) => post<DeskActionResultDto>(`/api/desk/bookings/${bookingId}/approve`),
  reject: (bookingId: number, reason: string) =>
    post<DeskActionResultDto>(`/api/desk/bookings/${bookingId}/reject`, { reason }),
  assign: (bookingId: number, vehicleId: number) =>
    post<DeskActionResultDto>(`/api/desk/bookings/${bookingId}/assign`, { vehicleId }),
  cancel: (bookingId: number, reason: string) =>
    post<DeskActionResultDto>(`/api/desk/bookings/${bookingId}/cancel`, { reason }),
  vehicleStatus: (vehicleId: number, status: 'ACTIVE' | 'OFF_ROAD', note?: string) =>
    post<{ message: string }>(`/api/desk/vehicles/${vehicleId}/status`, { status, note: note ?? null }),
};

export const adminApi = {
  routes: () => get<RouteDto[]>('/api/admin/routes'),
  updateRules: (routeId: number, body: UpdateRouteRulesRequest) => put<RouteDto>(`/api/admin/routes/${routeId}/rules`, body),
  resequence: (routeId: number, body: ResequenceRequest) => put<RouteDto>(`/api/admin/routes/${routeId}/stops`, body),
  compliance: () => get<ComplianceItemDto[]>('/api/admin/compliance'),
  audit: (limit = 50) => get<AuditLogDto[]>('/api/admin/audit', { limit }),
  resetDemo: () => post<{ message: string }>('/api/admin/demo/reset'),
  billing: (period?: string) => get<BillingSummaryDto>('/api/billing/summary', period ? { period } : undefined),
  exportCsv: async (period: string) =>
    (await api.get<string>('/api/billing/export', { params: { period }, responseType: 'text' })).data,
};
