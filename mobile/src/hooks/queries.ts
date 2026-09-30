import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { adminApi, deskApi, driverApi, masterApi, notificationApi, riderApi } from '@/api/endpoints';
import type {
  BookingDto,
  BookingScope,
  CreateBookingRequest,
  DriverQueueResponse,
  EndDutyRequest,
  RateBookingRequest,
  ReportIssueRequest,
  ResequenceRequest,
  RideOptionsRequest,
  StartDutyRequest,
  UpdateRouteRulesRequest,
} from '@/api/types';

/**
 * Query keys in one place, so mutations invalidate exactly what they change.
 * Polling intervals follow the deck: live screens refresh every few seconds.
 */
export const keys = {
  stops: ['stops'] as const,
  routes: ['routes'] as const,
  riderHome: ['rider', 'home'] as const,
  riderProfile: ['rider', 'profile'] as const,
  destinations: (fromStopId: number) => ['rider', 'destinations', fromStopId] as const,
  bookings: (scope: BookingScope) => ['rider', 'bookings', scope] as const,
  booking: (id: number) => ['rider', 'booking', id] as const,
  notifications: ['notifications'] as const,
  driverProfile: ['driver', 'profile'] as const,
  driverVehicles: ['driver', 'vehicles'] as const,
  driverQueue: ['driver', 'queue'] as const,
  deskBoard: ['desk', 'board'] as const,
  deskQueue: ['desk', 'queue'] as const,
  candidates: (bookingId: number) => ['desk', 'candidates', bookingId] as const,
  adminRoutes: ['admin', 'routes'] as const,
  compliance: ['admin', 'compliance'] as const,
  audit: ['admin', 'audit'] as const,
  billing: (period: string) => ['admin', 'billing', period] as const,
};

const LIVE_BOOKING = new Set(['SCHEDULED', 'PENDING_APPROVAL', 'REQUESTED', 'ASSIGNED', 'ONBOARD']);

// ------------------------------------------------------------------ master data

export const useStops = () => useQuery({ queryKey: keys.stops, queryFn: masterApi.stops, staleTime: 5 * 60_000 });

// ------------------------------------------------------------------ rider

export const useRiderHome = () =>
  useQuery({ queryKey: keys.riderHome, queryFn: riderApi.home, refetchInterval: 10_000 });

export const useRiderProfile = () =>
  useQuery({ queryKey: keys.riderProfile, queryFn: riderApi.profile, staleTime: 60_000 });

export const useDestinations = (fromStopId: number | null) =>
  useQuery({
    queryKey: keys.destinations(fromStopId ?? 0),
    queryFn: () => riderApi.destinations(fromStopId as number),
    enabled: fromStopId != null,
    staleTime: 5 * 60_000,
  });

export const useRideOptions = (request: RideOptionsRequest | null) =>
  useQuery({
    queryKey: ['rider', 'options', request],
    queryFn: () => riderApi.rideOptions(request as RideOptionsRequest),
    enabled: request != null,
    // ETAs and free seats go stale fast while the rider is deciding.
    refetchInterval: 15_000,
  });

export const useMyBookings = (scope: BookingScope) =>
  useQuery({ queryKey: keys.bookings(scope), queryFn: () => riderApi.bookings(scope) });

/** Polls while the booking is live; stops once it is closed. */
export const useBooking = (id: number) =>
  useQuery({
    queryKey: keys.booking(id),
    queryFn: () => riderApi.booking(id),
    refetchInterval: (query) => (query.state.data && LIVE_BOOKING.has(query.state.data.status) ? 4_000 : false),
  });

function useRiderInvalidation() {
  const qc = useQueryClient();
  return (booking?: BookingDto) => {
    if (booking) qc.setQueryData(keys.booking(booking.id), booking);
    void qc.invalidateQueries({ queryKey: ['rider'] });
    void qc.invalidateQueries({ queryKey: keys.notifications });
  };
}

export const useCreateBooking = () => {
  const refresh = useRiderInvalidation();
  return useMutation({
    mutationFn: ({ body, idempotencyKey }: { body: CreateBookingRequest; idempotencyKey: string }) =>
      riderApi.createBooking(body, idempotencyKey),
    onSuccess: refresh,
  });
};

export const useCancelBooking = () => {
  const refresh = useRiderInvalidation();
  return useMutation({
    mutationFn: ({ id, reason }: { id: number; reason?: string }) => riderApi.cancel(id, reason),
    onSuccess: refresh,
  });
};

export const useRateBooking = () => {
  const refresh = useRiderInvalidation();
  return useMutation({
    mutationFn: ({ id, body }: { id: number; body: RateBookingRequest }) => riderApi.rate(id, body),
    onSuccess: refresh,
  });
};

export const useRiderSos = () =>
  useMutation({ mutationFn: ({ bookingId, note }: { bookingId: number | null; note?: string }) => riderApi.sos(bookingId, note) });

export const useNotifications = () =>
  useQuery({ queryKey: keys.notifications, queryFn: notificationApi.list, refetchInterval: 20_000 });

export const useMarkNotificationsRead = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: notificationApi.readAll,
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: keys.notifications });
      void qc.invalidateQueries({ queryKey: keys.riderHome });
    },
  });
};

// ------------------------------------------------------------------ driver

export const useDriverProfile = () =>
  useQuery({ queryKey: keys.driverProfile, queryFn: driverApi.profile, refetchInterval: 30_000 });

export const useDriverVehicles = (enabled: boolean) =>
  useQuery({ queryKey: keys.driverVehicles, queryFn: driverApi.vehicles, enabled });

export const useDriverQueue = (enabled: boolean) =>
  useQuery({ queryKey: keys.driverQueue, queryFn: driverApi.queue, enabled, refetchInterval: 5_000 });

function useDriverRefresh() {
  const qc = useQueryClient();
  return (queue?: DriverQueueResponse) => {
    if (queue) qc.setQueryData(keys.driverQueue, queue);
    void qc.invalidateQueries({ queryKey: ['driver'] });
  };
}

export const useStartDuty = () => {
  const refresh = useDriverRefresh();
  return useMutation({ mutationFn: (body: StartDutyRequest) => driverApi.startDuty(body), onSuccess: () => refresh() });
};

export const useEndDuty = () => {
  const refresh = useDriverRefresh();
  return useMutation({ mutationFn: (body: EndDutyRequest) => driverApi.endDuty(body), onSuccess: () => refresh() });
};

export const useArrive = () => {
  const refresh = useDriverRefresh();
  return useMutation({
    mutationFn: ({ tripId, stopSeq }: { tripId: number; stopSeq: number }) => driverApi.arrive(tripId, stopSeq),
    onSuccess: refresh,
  });
};

export const useBoardRider = () => {
  const refresh = useDriverRefresh();
  return useMutation({
    mutationFn: ({ tripId, bookingId, otp }: { tripId: number; bookingId: number; otp: string }) =>
      driverApi.board(tripId, bookingId, otp),
    onSuccess: refresh,
    // A wrong OTP changes the attempt counter on the server: refresh either way.
    onError: () => refresh(),
  });
};

export const useNoShow = () => {
  const refresh = useDriverRefresh();
  return useMutation({
    mutationFn: ({ tripId, bookingId }: { tripId: number; bookingId: number }) => driverApi.noShow(tripId, bookingId),
    onSuccess: refresh,
  });
};

export const useReportIssue = () => {
  const refresh = useDriverRefresh();
  return useMutation({ mutationFn: (body: ReportIssueRequest) => driverApi.reportIssue(body), onSuccess: () => refresh() });
};

export const useDriverSos = () => useMutation({ mutationFn: (note?: string) => driverApi.sos(note) });

// ------------------------------------------------------------------ desk

export const useDeskBoard = () => useQuery({ queryKey: keys.deskBoard, queryFn: deskApi.board, refetchInterval: 5_000 });

export const useDeskQueue = () => useQuery({ queryKey: keys.deskQueue, queryFn: deskApi.queue, refetchInterval: 5_000 });

export const useCandidates = (bookingId: number | null) =>
  useQuery({
    queryKey: keys.candidates(bookingId ?? 0),
    queryFn: () => deskApi.candidates(bookingId as number),
    enabled: bookingId != null,
  });

function useDeskRefresh() {
  const qc = useQueryClient();
  return () => {
    void qc.invalidateQueries({ queryKey: ['desk'] });
    void qc.invalidateQueries({ queryKey: ['admin'] });
  };
}

export const useAlertAction = () => {
  const refresh = useDeskRefresh();
  return useMutation({
    mutationFn: ({ id, action }: { id: number; action: 'ack' | 'resolve' }) =>
      action === 'ack' ? deskApi.acknowledge(id) : deskApi.resolve(id),
    onSuccess: refresh,
  });
};

export const useQueueAction = () => {
  const refresh = useDeskRefresh();
  return useMutation({
    mutationFn: (a:
      | { kind: 'approve'; bookingId: number }
      | { kind: 'reject'; bookingId: number; reason: string }
      | { kind: 'cancel'; bookingId: number; reason: string }
      | { kind: 'assign'; bookingId: number; vehicleId: number }) => {
      switch (a.kind) {
        case 'approve':
          return deskApi.approve(a.bookingId);
        case 'reject':
          return deskApi.reject(a.bookingId, a.reason);
        case 'cancel':
          return deskApi.cancel(a.bookingId, a.reason);
        case 'assign':
          return deskApi.assign(a.bookingId, a.vehicleId);
      }
    },
    onSuccess: refresh,
  });
};

export const useVehicleStatus = () => {
  const refresh = useDeskRefresh();
  return useMutation({
    mutationFn: ({ vehicleId, status, note }: { vehicleId: number; status: 'ACTIVE' | 'OFF_ROAD'; note?: string }) =>
      deskApi.vehicleStatus(vehicleId, status, note),
    onSuccess: refresh,
  });
};

// ------------------------------------------------------------------ admin

export const useBilling = (period: string) =>
  useQuery({ queryKey: keys.billing(period), queryFn: () => adminApi.billing(period), placeholderData: (prev) => prev });

export const useAdminRoutes = () => useQuery({ queryKey: keys.adminRoutes, queryFn: adminApi.routes });

export const useCompliance = () => useQuery({ queryKey: keys.compliance, queryFn: adminApi.compliance });

export const useAudit = () => useQuery({ queryKey: keys.audit, queryFn: () => adminApi.audit(40), refetchInterval: 15_000 });

export const useUpdateRules = () => {
  const refresh = useDeskRefresh();
  return useMutation({
    mutationFn: ({ routeId, body }: { routeId: number; body: UpdateRouteRulesRequest }) => adminApi.updateRules(routeId, body),
    onSuccess: refresh,
  });
};

export const useResequence = () => {
  const refresh = useDeskRefresh();
  return useMutation({
    mutationFn: ({ routeId, body }: { routeId: number; body: ResequenceRequest }) => adminApi.resequence(routeId, body),
    onSuccess: refresh,
  });
};

export const useResetDemo = () => {
  const qc = useQueryClient();
  return useMutation({ mutationFn: adminApi.resetDemo, onSuccess: () => qc.invalidateQueries() });
};
