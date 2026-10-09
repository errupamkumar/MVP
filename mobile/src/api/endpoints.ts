import { api } from './client';
import type {
  AlertDto,
  AuditLogDto,
  AvailabilityDto,
  AvailabilityRequest,
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
  PickupPointDto,
  QueueItemDto,
  RateBookingRequest,
  ReportIssueRequest,
  ResequenceRequest,
  RideOptionsRequest,
  RideOptionsResponse,
  RiderHomeDto,
  RiderLocationAckDto,
  RiderLocationRequest,
  RouteDto,
  SavePickupPointRequest,
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
const del = async (url: string) => {
  await api.delete(url);
};

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
  availability: (body: AvailabilityRequest) => post<AvailabilityDto>('/api/rider/availability', body),
  bookingAvailability: (id: number) => get<AvailabilityDto>(`/api/rider/bookings/${id}/availability`),
  searchAgain: (id: number) => post<BookingDto>(`/api/rider/bookings/${id}/search`),
  createBooking: (body: CreateBookingRequest, idempotencyKey: string) =>
    post<BookingDto>('/api/rider/bookings', body, { 'Idempotency-Key': idempotencyKey }),
  bookings: (scope: BookingScope) => get<BookingSummaryDto[]>('/api/rider/bookings', { scope }),
  booking: (id: number) => get<BookingDto>(`/api/rider/bookings/${id}`),
  cancel: (id: number, reason?: string) => post<BookingDto>(`/api/rider/bookings/${id}/cancel`, { reason: reason ?? null }),
  rate: (id: number, body: RateBookingRequest) => post<BookingDto>(`/api/rider/bookings/${id}/rating`, body),
  shareLocation: (id: number, body: RiderLocationRequest) =>
    post<RiderLocationAckDto>(`/api/rider/bookings/${id}/location`, body),
  stopSharingLocation: (id: number) => del(`/api/rider/bookings/${id}/location`),
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
  stops: () => get<PickupPointDto[]>('/api/admin/stops'),
  createStop: (body: SavePickupPointRequest) => post<PickupPointDto>('/api/admin/stops', body),
  updateStop: (id: number, body: SavePickupPointRequest) => put<PickupPointDto>(`/api/admin/stops/${id}`, body),
  audit: (limit = 50) => get<AuditLogDto[]>('/api/admin/audit', { limit }),
  resetDemo: () => post<{ message: string }>('/api/admin/demo/reset'),
  billing: (period?: string) => get<BillingSummaryDto>('/api/billing/summary', period ? { period } : undefined),
  exportCsv: async (period: string) =>
    (await api.get<string>('/api/billing/export', { params: { period }, responseType: 'text' })).data,
};
