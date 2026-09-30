/**
 * Mirrors the Spring Boot DTOs (package com.srmecotech.plantride.*.dto).
 * Dates: LocalDateTime fields arrive as plant-local wall time without an
 * offset ("2026-09-30T14:05:12.123"); Instant fields end in "Z".
 * Money and kilometres arrive as JSON numbers.
 */

export type Role = 'EMPLOYEE' | 'DRIVER' | 'DESK' | 'ADMIN';

export interface ErrorResponse {
  timestamp: string;
  status: number;
  error: string;
  code: string;
  message: string;
  path: string;
  fieldErrors: { field: string; message: string }[];
}

// ------------------------------------------------------------------ auth

export interface UserSummary {
  id: number;
  username: string;
  fullName: string;
  role: Role;
  email: string | null;
  phone: string | null;
}

export interface LoginResponse {
  token: string;
  expiresAt: string;
  user: UserSummary;
}

// ------------------------------------------------------------------ master data

export interface StopRef {
  id: number;
  code: string;
  name: string;
}

export interface RouteRef {
  id: number;
  code: string;
  name: string;
  label: string;
}

export type VehicleType = 'CAB' | 'SHUTTLE';

export interface VehicleRef {
  id: number;
  code: string;
  registrationNo: string;
  vehicleType: VehicleType;
  seatCapacity: number;
}

export interface StopDto {
  id: number;
  code: string;
  name: string;
  zone: string;
  latitude: number;
  longitude: number;
  geofenceM: number;
}

export interface RouteStopDto {
  seq: number;
  stopId: number;
  stopCode: string;
  stopName: string;
  legMinutes: number;
  legKm: number;
  geofenceM: number;
}

export interface RouteDto {
  id: number;
  code: string;
  name: string;
  label: string;
  routeType: 'LOOP' | 'ONE_WAY';
  serviceNote: string | null;
  frequencyMin: number | null;
  maxPassengers: number;
  maxWaitMin: number;
  maxDetourMin: number;
  speedLimitKmh: number;
  active: boolean;
  version: number;
  updatedAt: string;
  totalMinutes: number;
  totalKm: number;
  stops: RouteStopDto[];
}

export interface DestinationDto {
  stopId: number;
  stopCode: string;
  stopName: string;
  routeId: number;
  routeCode: string;
  routeName: string;
  rideMinutes: number;
  rideKm: number;
}

export type DocState = 'VALID' | 'EXPIRING' | 'EXPIRED';

export interface DocumentStatus {
  document: string;
  reference: string;
  expiresOn: string;
  daysLeft: number;
  state: DocState;
  label: string;
}

// ------------------------------------------------------------------ rider

export type BookingStatus =
  | 'SCHEDULED'
  | 'PENDING_APPROVAL'
  | 'REQUESTED'
  | 'ASSIGNED'
  | 'ONBOARD'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'NO_SHOW'
  | 'REJECTED';

export type RideType = 'SHARED' | 'EXCLUSIVE';

export interface EmployeeProfileDto {
  employeeId: number;
  personnelNo: string;
  fullName: string;
  email: string | null;
  phone: string | null;
  department: string;
  grade: string;
  costCentreCode: string;
  costCentreName: string;
  exclusiveEligible: boolean;
  bookingPaused: boolean;
  noShowsInWindow: number;
}

export interface SavedPlaceDto {
  stopId: number;
  stopName: string;
  hint: string;
  trips: number;
}

export interface ShuttleInfoDto {
  vehicleCode: string;
  routeLabel: string;
  stopId: number;
  stopName: string;
  minutesToNext: number;
  frequencyMin: number;
  seatCapacity: number;
  /** "06:00" when the shuttle has not started for the day yet. */
  firstRunAt: string | null;
}

export interface RiderHomeDto {
  profile: EmployeeProfileDto;
  activeBooking: BookingDto | null;
  upcomingCount: number;
  savedPlaces: SavedPlaceDto[];
  defaultFromStopId: number | null;
  nextShuttle: ShuttleInfoDto | null;
  unreadNotifications: number;
}

export interface RideOptionsRequest {
  fromStopId: number;
  toStopId: number;
  seats?: number;
  scheduledAt?: string | null;
}

export type RideOptionType = 'SHUTTLE' | 'SHARED' | 'EXCLUSIVE';

export interface RideOptionDto {
  type: RideOptionType;
  bookable: boolean;
  title: string;
  subtitle: string;
  etaMinutes: number | null;
  seatsTaken: number | null;
  seatsFree: number | null;
  capacity: number | null;
  vehicleCode: string | null;
  requiresApproval: boolean;
  note: string | null;
}

export interface RideOptionsResponse {
  route: RouteRef;
  from: StopRef;
  to: StopRef;
  rideKm: number;
  rideMinutes: number;
  occupancyCap: number;
  costCentreCode: string;
  costCentreName: string;
  scheduledAt: string | null;
  options: RideOptionDto[];
}

export interface CreateBookingRequest {
  fromStopId: number;
  toStopId: number;
  rideType: RideType;
  seats: number;
  scheduledAt: string | null;
  riderName: string;
  riderPhone: string;
  riderEmail: string | null;
  forGuest: boolean;
}

export type ProgressStage =
  | 'SCHEDULED'
  | 'AWAITING_APPROVAL'
  | 'FINDING_CAB'
  | 'ON_THE_WAY'
  | 'AT_PICKUP'
  | 'ON_TRIP'
  | 'COMPLETED'
  | 'CLOSED';

export interface ProgressStopDto {
  seq: number;
  stopName: string;
  pickup: boolean;
  drop: boolean;
  state: 'PASSED' | 'HERE' | 'NEXT' | 'AHEAD';
}

export interface RideProgressDto {
  stage: ProgressStage;
  stageLabel: string;
  etaToPickupMinutes: number | null;
  etaToDropMinutes: number | null;
  vehicleAt: string | null;
  coRiders: number;
  seatsTaken: number;
  capacity: number;
  stops: ProgressStopDto[];
}

export interface BookingDto {
  id: number;
  bookingCode: string;
  status: BookingStatus;
  rideType: RideType;
  seats: number;
  forGuest: boolean;
  riderName: string;
  riderPhone: string;
  riderEmail: string | null;
  route: RouteRef;
  fromStop: StopRef;
  toStop: StopRef;
  scheduledAt: string | null;
  createdAt: string;
  assignedAt: string | null;
  boardedAt: string | null;
  droppedAt: string | null;
  cancelledAt: string | null;
  cancelReason: string | null;
  otp: string | null;
  trackingToken: string;
  trackingUrl: string;
  vehicle: VehicleRef | null;
  driver: { name: string; phone: string } | null;
  tripCode: string | null;
  promisedEtaMinutes: number | null;
  promisedPickupAt: string | null;
  progress: RideProgressDto;
  costCentreCode: string;
  costCentreName: string;
  rideKm: number;
  rideMinutes: number;
  distanceKm: number | null;
  costAmount: number | null;
  matchNote: string | null;
  rating: number | null;
  ratingTags: string[];
  ratingNote: string | null;
  canCancel: boolean;
  canRate: boolean;
}

export interface BookingSummaryDto {
  id: number;
  bookingCode: string;
  status: BookingStatus;
  rideType: RideType;
  fromStopName: string;
  toStopName: string;
  when: string;
  distanceKm: number | null;
  vehicleCode: string | null;
  costAmount: number | null;
  cancelReason: string | null;
  rating: number | null;
}

export type BookingScope = 'upcoming' | 'past' | 'cancelled';

export type RatingTag = 'ON_TIME' | 'CLEAN_CAB' | 'SAFE_DRIVING' | 'LONG_WAIT' | 'HARD_TO_FIND';

export interface RateBookingRequest {
  rating: number;
  tags: RatingTag[];
  note: string | null;
}

export interface SosResultDto {
  alertId: number;
  message: string;
}

export interface NotificationDto {
  id: number;
  category: string;
  title: string;
  body: string;
  bookingId: number | null;
  createdAt: string;
  read: boolean;
}

// ------------------------------------------------------------------ driver

export interface DutyDto {
  id: number;
  status: 'ACTIVE' | 'CLOSED';
  vehicle: VehicleRef;
  startedAt: string;
  minutesOnDuty: number;
  maxDutyMinutes: number;
  startOdometer: number;
  tripsCompleted: number;
  ridersCarried: number;
  distanceKm: number;
}

export interface DriverProfileDto {
  driverId: number;
  driverCode: string;
  fullName: string;
  phone: string | null;
  vendorName: string;
  gatesAllowed: string;
  maxDutyMinutes: number;
  documents: DocumentStatus[];
  blockedReason: string | null;
  defaultVehicleId: number | null;
  duty: DutyDto | null;
}

export interface VehicleOptionDto {
  id: number;
  code: string;
  registrationNo: string;
  vehicleType: VehicleType;
  seatCapacity: number;
  vendorName: string;
  odometerKm: number;
  currentStopName: string | null;
  available: boolean;
  blockedReason: string | null;
  documents: DocumentStatus[];
}

export interface StartDutyRequest {
  vehicleId: number;
  startOdometer: number;
  checklist: string[];
}

export interface EndDutyRequest {
  endOdometer: number;
  fuelLitres: number | null;
}

export interface DutySummaryDto {
  dutyId: number;
  vehicleCode: string;
  startedAt: string;
  endedAt: string;
  dutyMinutes: number;
  tripsCompleted: number;
  ridersCarried: number;
  gpsKm: number;
  odometerKm: number;
  odometerCheck: 'MATCHES' | 'REVIEW';
  fuelLitres: number | null;
}

export interface QueueRiderDto {
  bookingId: number;
  bookingCode: string;
  riderName: string;
  personnelNo: string;
  riderPhone: string;
  seats: number;
  status: BookingStatus;
  fromStopName: string;
  toStopName: string;
  noShowAllowedInSeconds: number | null;
  otpLocked: boolean;
}

export interface QueueStopDto {
  seq: number;
  stopId: number;
  stopName: string;
  state: 'DONE' | 'CURRENT' | 'NEXT' | 'UPCOMING';
  etaMinutes: number;
  summary: string;
  pickups: QueueRiderDto[];
  drops: QueueRiderDto[];
}

export interface TripQueueDto {
  tripId: number;
  tripCode: string;
  status: 'PLANNED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';
  route: RouteRef;
  exclusive: boolean;
  capacity: number;
  onBoard: number;
  seatsLeft: number;
  currentSeq: number | null;
  currentStopName: string | null;
  nextStop: QueueStopDto | null;
  canArriveNext: boolean;
  arriveBlockedReason: string | null;
  speedLimitKmh: number;
  stops: QueueStopDto[];
}

export interface DriverQueueResponse {
  trip: TripQueueDto | null;
  notice: string | null;
}

export interface LocationPingRequest {
  latitude: number;
  longitude: number;
  speedKmh: number | null;
  recordedAt: string | null;
}

export interface LocationAckDto {
  overspeed: boolean;
  speedLimitKmh: number;
  autoArrivedAt: string | null;
}

export type IssueType = 'BREAKDOWN' | 'ACCIDENT' | 'DELAY' | 'FUEL' | 'OTHER';

export interface ReportIssueRequest {
  type: IssueType;
  description: string;
  litres: number | null;
}

export interface IssueResultDto {
  alertId: number;
  message: string;
  ridersRematched: number;
}

// ------------------------------------------------------------------ desk & admin

export type AlertSeverity = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';
export type AlertStatus = 'OPEN' | 'ACKNOWLEDGED' | 'RESOLVED';

export interface AlertDto {
  id: number;
  type: string;
  severity: AlertSeverity;
  status: AlertStatus;
  message: string;
  vehicleCode: string | null;
  bookingCode: string | null;
  raisedBy: string;
  latitude: number | null;
  longitude: number | null;
  createdAt: string;
  minutesAgo: number;
  acknowledgedBy: string | null;
  resolvedBy: string | null;
}

export interface KpiDto {
  vehiclesOnTrip: number;
  fleetSize: number;
  seatOccupancyPct: number;
  avgWaitMinutes: number;
  onTimePickupPct: number;
  ridesToday: number;
  openAlerts: number;
  waitingBookings: number;
}

export type VehicleLiveStatus = 'ON_TRIP' | 'ASSIGNED' | 'SHUTTLE' | 'IDLE' | 'OFF_DUTY' | 'BLOCKED' | 'OFF_ROAD';

export interface VehicleLiveDto {
  id: number;
  code: string;
  registrationNo: string;
  vehicleType: VehicleType;
  status: VehicleLiveStatus;
  statusLabel: string;
  flags: string[];
  routeLabel: string | null;
  locationLabel: string;
  riders: number;
  capacity: number;
  driverName: string | null;
  driverPhone: string | null;
  tripId: number | null;
  tripCode: string | null;
  idleMinutes: number | null;
  lastPingAt: string | null;
}

export interface QueueItemDto {
  bookingId: number;
  bookingCode: string;
  status: BookingStatus;
  rideType: RideType;
  riderName: string;
  personnelNo: string;
  costCentreCode: string;
  routeCode: string;
  fromStopName: string;
  toStopName: string;
  seats: number;
  scheduledAt: string | null;
  createdAt: string;
  waitingMinutes: number;
  vehicleCode: string | null;
  note: string | null;
}

export interface LiveBoardDto {
  generatedAt: string;
  kpis: KpiDto;
  vehicles: VehicleLiveDto[];
  alerts: AlertDto[];
  queue: QueueItemDto[];
}

export interface CandidateDto {
  vehicleId: number;
  vehicleCode: string;
  feasible: boolean;
  etaMinutes: number | null;
  seatsTaken: number | null;
  capacity: number | null;
  score: number | null;
  reason: string;
}

export interface DeskActionResultDto {
  message: string;
  booking: QueueItemDto;
}

export interface CostCentreCharge {
  code: string;
  name: string;
  trips: number;
  rides: number;
  km: number;
  amount: number;
  budget: number;
  budgetUsedPct: number;
  budgetStatus: 'OK' | 'WARN' | 'OVER';
}

export interface VendorReconciliation {
  vendorCode: string;
  vendorName: string;
  claimedKm: number | null;
  gpsKm: number;
  gapKm: number | null;
  gapPct: number | null;
  claimedAmount: number | null;
  gpsAmount: number;
  status: 'MATCHED' | 'DISPUTE' | 'NO_CLAIM';
}

export interface BillingSummaryDto {
  period: string;
  draft: boolean;
  sharingRule: string;
  totals: { trips: number; rides: number; gpsKm: number; amount: number; ridersPerTrip: number };
  costCentres: CostCentreCharge[];
  vendors: VendorReconciliation[];
  compliance: {
    documentsExpiringSoon: number;
    expiredDocuments: number;
    blockedVehicles: number;
    blockedDrivers: number;
    fullyCompliantVehicles: number;
    totalVehicles: number;
  };
}

export interface ComplianceItemDto {
  entityType: 'VEHICLE' | 'DRIVER';
  entityId: number;
  code: string;
  name: string;
  document: string;
  reference: string;
  expiresOn: string;
  daysLeft: number;
  state: DocState;
  label: string;
  blocksDispatch: boolean;
}

export interface AuditLogDto {
  id: number;
  actorName: string;
  action: string;
  entityType: string;
  entityId: number | null;
  details: string | null;
  createdAt: string;
}

export interface UpdateRouteRulesRequest {
  maxPassengers: number;
  maxWaitMin: number;
  maxDetourMin: number;
  speedLimitKmh: number;
  frequencyMin: number | null;
  version: number;
}

export interface ResequenceRequest {
  version: number;
  stops: { stopId: number; legMinutes: number; legKm: number }[];
}
