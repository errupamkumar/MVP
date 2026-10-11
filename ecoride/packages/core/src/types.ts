/**
 * The EcoRide API contract, mirrored from the Spring Boot DTOs. Timestamps are ISO-8601 UTC
 * strings; money is rupees as numbers; distances in metres, durations in seconds unless named.
 */

export type ISODate = string;

export interface FieldError {
  field: string;
  message: string;
}

export interface ErrorResponse {
  timestamp: ISODate;
  status: number;
  error: string;
  code: string;
  message: string;
  path: string;
  fieldErrors: FieldError[];
}

// ------------------------------------------------------------------ shared

export type VehicleTypeCode = 'BIKE' | 'AUTO' | 'MINI' | 'SEDAN' | 'SUV' | (string & {});
export type PaymentMethod = 'CASH' | 'UPI' | 'WALLET';
export type PaymentStatus = 'PENDING' | 'PAID' | 'FAILED' | 'REFUNDED';
export type RideStatus = 'SEARCHING' | 'ACCEPTED' | 'ARRIVED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED' | 'NO_DRIVERS';
export type DriverStatus = 'PENDING_APPROVAL' | 'APPROVED' | 'REJECTED' | 'SUSPENDED';
export type Availability = 'OFFLINE' | 'ONLINE' | 'ON_TRIP';

export interface LatLng {
  lat: number;
  lng: number;
}

export interface Place extends LatLng {
  address: string;
}

export interface Position extends LatLng {
  heading: number | null;
  speedKmh: number | null;
  /** Seconds since the fix. */
  ageS: number | null;
}

export interface FareBreakdown {
  vehicleType: string;
  distanceKm: number;
  durationMin: number;
  baseFare: number;
  baseKm: number;
  distanceFare: number;
  timeFare: number;
  minimumFareTopUp: number;
  surgeMultiplier: number;
  surgeFare: number;
  waitingMin: number;
  waitingFare: number;
  fareBeforeDiscount: number;
  promoCode: string | null;
  discount: number;
  taxPct: number;
  tax: number;
  rounding: number;
  total: number;
}

export interface Product {
  code: VehicleTypeCode;
  name: string;
  description: string;
  capacity: number;
  baseFare: number;
  baseKm: number;
  perKm: number;
  perMin: number;
}

export interface PublicConfig {
  city: string;
  centerLat: number;
  centerLng: number;
  serviceRadiusKm: number;
  products: Product[];
}

export interface PlaceResult extends LatLng {
  name: string;
  address: string;
  /** PLACE (curated), OSM (OpenStreetMap) or PIN (coordinates only). */
  source: 'PLACE' | 'OSM' | 'PIN' | string;
  distanceM: number | null;
  category: string;
}

export interface RouteResult {
  distanceM: number;
  durationS: number;
  polyline: string;
  provider: 'OSRM' | 'STRAIGHT' | string;
}

// ------------------------------------------------------------------ rider

export interface RiderProfile {
  id: number;
  name: string;
  phone: string;
  email: string | null;
  rating: number;
  ratingCount: number;
  walletBalance: number;
  completedRides: number;
  memberSince: ISODate;
}

export interface RiderAuthResponse {
  token: string;
  expiresAt: ISODate;
  profile: RiderProfile;
}

export interface SavedPlace {
  id: number;
  label: 'HOME' | 'WORK' | 'OTHER';
  name: string;
  address: string;
  lat: number;
  lng: number;
}

export interface WalletLine {
  id: number;
  rideId: number | null;
  type: string;
  amount: number;
  balanceAfter: number;
  note: string;
  at: ISODate;
}

export interface Wallet {
  balance: number;
  lines: WalletLine[];
}

export interface QuoteOption {
  vehicleType: VehicleTypeCode;
  name: string;
  description: string;
  capacity: number;
  fare: FareBreakdown;
  /** Seconds for the nearest free driver to reach the pickup; null when none is near. */
  etaS: number | null;
  driversNearby: number;
}

export interface QuoteResponse {
  quoteId: string;
  expiresAt: ISODate;
  validForS: number;
  pickup: Place;
  drop: Place;
  route: RouteResult;
  surge: { multiplier: number; zone: string | null; mode: string | null };
  promo: { code: string; valid: boolean; message: string } | null;
  options: QuoteOption[];
}

export interface NearbyCar extends LatLng {
  vehicleType: VehicleTypeCode;
  heading: number | null;
}

export interface Nearby {
  cars: NearbyCar[];
  etaByType: Record<string, number>;
}

export interface VehicleInfo {
  type: VehicleTypeCode;
  typeName: string;
  make: string;
  model: string;
  color: string;
  plate: string;
}

export interface DriverCard {
  id: number;
  name: string;
  phone: string;
  rating: number;
  ratingCount: number;
  trips: number;
  vehicle: VehicleInfo;
  position: Position | null;
}

export interface RiderCard {
  id: number;
  name: string;
  phone: string;
  rating: number;
  ratingCount: number;
  position: Position | null;
}

/** Distances recomputed from the latest GPS fix on every poll. */
export interface Live {
  toPickupM: number | null;
  toPickupEtaS: number | null;
  remainingM: number | null;
  remainingEtaS: number | null;
  travelledM: number;
  progress: number;
  offRoute: boolean;
  waitingS: number | null;
  freeWaitLeftS: number | null;
  riderToPickupM: number | null;
}

export interface SearchInfo {
  elapsedS: number;
  timeoutS: number;
  driversContacted: number;
  offerExpiresInS: number | null;
}

export interface FareInfo {
  quote: FareBreakdown | null;
  fare: FareBreakdown | null;
  policy: 'UPFRONT' | 'RECALCULATED' | null;
  total: number;
  method: PaymentMethod;
  paymentStatus: PaymentStatus;
  tip: number;
  cancellationFee: number;
}

export interface TimelineEntry {
  type: string;
  actor: string;
  message: string;
  at: ISODate;
}

export interface RiderRide {
  id: number;
  code: string;
  status: RideStatus;
  vehicleType: VehicleTypeCode;
  vehicleTypeName: string;
  pickup: Place;
  drop: Place;
  estDistanceM: number;
  estDurationS: number;
  routePolyline: string;
  pickupPolyline: string | null;
  otp: string | null;
  driver: DriverCard | null;
  live: Live;
  search: SearchInfo | null;
  fare: FareInfo;
  actualDistanceM: number;
  requestedAt: ISODate;
  acceptedAt: ISODate | null;
  arrivedAt: ISODate | null;
  startedAt: ISODate | null;
  completedAt: ISODate | null;
  cancelledAt: ISODate | null;
  cancelledBy: string | null;
  cancelReason: string | null;
  myRating: number | null;
  cancelFeeApplies: boolean;
  freeCancelLeftS: number | null;
  cancelFee: number;
  timeline: TimelineEntry[];
}

export interface RideSummary {
  id: number;
  code: string;
  status: RideStatus;
  vehicleType: VehicleTypeCode;
  vehicleTypeName: string;
  pickupAddress: string;
  dropAddress: string;
  requestedAt: ISODate;
  completedAt: ISODate | null;
  total: number | null;
  paymentMethod: PaymentMethod;
  counterpartName: string | null;
  rating: number | null;
  distanceKm: number;
}

// ------------------------------------------------------------------ driver

export interface DriverVehicle extends VehicleInfo {
  year: number;
}

export interface DriverProfile {
  id: number;
  name: string;
  phone: string;
  email: string | null;
  licenseNo: string;
  status: DriverStatus;
  statusReason: string | null;
  availability: Availability;
  rating: number;
  ratingCount: number;
  acceptanceRate: number | null;
  vehicle: DriverVehicle;
  memberSince: ISODate;
  approvedAt: ISODate | null;
}

export interface DriverAuthResponse {
  token: string;
  expiresAt: ISODate;
  profile: DriverProfile;
}

export interface Offer {
  offerId: number;
  rideId: number;
  expiresInS: number;
  timeoutS: number;
  vehicleType: VehicleTypeCode;
  pickup: Place;
  drop: Place;
  pickupDistanceM: number;
  pickupEtaS: number;
  tripDistanceM: number;
  tripDurationS: number;
  fare: number;
  estEarning: number;
  paymentMethod: PaymentMethod;
  surgeMultiplier: number;
  riderName: string;
  riderRating: number;
  routePolyline: string;
}

export interface DriverRide {
  id: number;
  code: string;
  status: RideStatus;
  vehicleType: VehicleTypeCode;
  pickup: Place;
  drop: Place;
  estDistanceM: number;
  estDurationS: number;
  routePolyline: string;
  pickupPolyline: string | null;
  rider: RiderCard;
  live: Live;
  fare: FareInfo;
  estEarning: number;
  actualDistanceM: number;
  pickupTravelM: number;
  acceptedAt: ISODate | null;
  arrivedAt: ISODate | null;
  startedAt: ISODate | null;
  completedAt: ISODate | null;
  cancelledAt: ISODate | null;
  cancelledBy: string | null;
  cancelReason: string | null;
  riderRating: number | null;
  canCancelAsNoShow: boolean;
  noShowInS: number | null;
}

export interface DriverState {
  profile: DriverProfile;
  position: Position | null;
  today: { earnings: number; trips: number; onlineMinutes: number | null };
  offer: Offer | null;
  ride: DriverRide | null;
  lastTrip: DriverRide | null;
}

export interface EarningsPeriod {
  net: number;
  fares: number;
  tips: number;
  penalties: number;
  cashKept: number;
  trips: number;
  distanceKm: number;
  onlineMinutes: number;
}

export interface LedgerLine {
  id: number;
  rideId: number | null;
  rideCode: string | null;
  type: string;
  amount: number;
  note: string;
  at: ISODate;
}

export interface Earnings {
  today: EarningsPeriod;
  week: EarningsPeriod;
  balance: number;
  days: { date: string; net: number; trips: number }[];
  lines: LedgerLine[];
}

// ------------------------------------------------------------------ admin

export interface AdminProfile {
  id: number;
  name: string;
  email: string;
}

export interface AdminAuthResponse {
  token: string;
  expiresAt: ISODate;
  admin: AdminProfile;
}

export interface PageDto<T> {
  items: T[];
  page: number;
  size: number;
  total: number;
}

export interface Kpis {
  ridesToday: number;
  completedToday: number;
  cancelledToday: number;
  activeNow: number;
  searchingNow: number;
  onlineDrivers: number;
  onTripDrivers: number;
  approvedDrivers: number;
  pendingDrivers: number;
  riders: number;
  newRidersToday: number;
  gmvToday: number;
  commissionToday: number;
  avgFareToday: number;
  avgPickupMinToday: number | null;
  cancelRateToday: number | null;
  avgRatingToday: number | null;
  openSos: number;
}

export interface DayPoint {
  date: string;
  rides: number;
  completed: number;
  cancelled: number;
  gmv: number;
  commission: number;
}

export interface TypeSplit {
  vehicleType: string;
  name: string;
  rides: number;
  gmv: number;
}

export interface RideRow {
  id: number;
  code: string;
  status: RideStatus;
  vehicleType: string;
  riderName: string;
  driverName: string | null;
  pickupAddress: string;
  dropAddress: string;
  requestedAt: ISODate;
  completedAt: ISODate | null;
  fare: number | null;
  paymentMethod: PaymentMethod;
  paymentStatus: PaymentStatus;
  distanceKm: number;
  simulated: boolean;
}

export interface SosRow {
  id: number;
  rideId: number;
  rideCode: string;
  raisedBy: string;
  riderName: string;
  driverName: string | null;
  lat: number | null;
  lng: number | null;
  status: 'OPEN' | 'RESOLVED';
  note: string | null;
  createdAt: ISODate;
  resolvedAt: ISODate | null;
  resolvedBy: string | null;
}

export interface DriverRow {
  id: number;
  name: string;
  phone: string;
  vehicleType: string;
  vehicle: string;
  plate: string;
  status: DriverStatus;
  availability: Availability;
  rating: number;
  ratingCount: number;
  acceptanceRate: number | null;
  trips: number;
  balance: number;
  simulated: boolean;
  createdAt: ISODate;
  locatedAt: ISODate | null;
}

export interface Dashboard {
  kpis: Kpis;
  days: DayPoint[];
  hours: { hour: number; rides: number }[];
  byType: TypeSplit[];
  recent: RideRow[];
  openSos: SosRow[];
  pendingDrivers: DriverRow[];
}

export interface LiveDriver {
  id: number;
  name: string;
  vehicleType: string;
  plate: string;
  availability: Availability;
  lat: number;
  lng: number;
  heading: number | null;
  ageS: number | null;
  simulated: boolean;
  rideId: number | null;
  rideCode: string | null;
  rideStatus: RideStatus | null;
  hasOffer: boolean;
}

export interface LiveRide {
  id: number;
  code: string;
  status: RideStatus;
  vehicleType: string;
  riderName: string;
  driverName: string | null;
  pickup: Place;
  drop: Place;
  cabLat: number | null;
  cabLng: number | null;
  routePolyline: string | null;
  pickupPolyline: string | null;
  simulated: boolean;
  requestedAt: ISODate;
}

export interface LiveZone {
  id: number;
  name: string;
  lat: number;
  lng: number;
  radiusM: number;
  mode: 'AUTO' | 'MANUAL';
  multiplier: number;
  active: boolean;
  demand: number | null;
  supply: number | null;
}

export interface LiveMap {
  drivers: LiveDriver[];
  rides: LiveRide[];
  zones: LiveZone[];
  online: number;
  onTrip: number;
  searching: number;
  inProgress: number;
}

export interface OfferRow {
  driverId: number;
  driverName: string;
  status: string;
  distanceM: number;
  etaS: number;
  offeredAt: ISODate;
  respondedAt: ISODate | null;
  simulated: boolean;
}

export interface PaymentRow {
  id: number;
  rideId: number;
  rideCode: string;
  riderName: string;
  kind: string;
  method: string;
  amount: number;
  status: string;
  reference: string | null;
  createdAt: ISODate;
}

export interface TrackPoint extends LatLng {
  phase: 'PICKUP' | 'TRIP';
  speedKmh: number | null;
  at: ISODate;
}

export interface PersonBrief {
  id: number;
  name: string;
  phone: string;
  rating: number;
  extra: string | null;
}

export interface RideDetail {
  row: RideRow;
  pickup: Place;
  drop: Place;
  otp: string;
  estDistanceM: number;
  estDurationS: number;
  actualDistanceM: number;
  pickupTravelM: number;
  routePolyline: string;
  pickupPolyline: string | null;
  routeProvider: string;
  surgeMultiplier: number;
  promoCode: string | null;
  quote: FareBreakdown | null;
  fare: FareBreakdown | null;
  farePolicy: string | null;
  commission: number | null;
  driverEarning: number | null;
  tip: number;
  cancellationFee: number;
  cancelledBy: string | null;
  cancelReason: string | null;
  driverRating: number | null;
  driverFeedback: string | null;
  riderRating: number | null;
  acceptedAt: ISODate | null;
  arrivedAt: ISODate | null;
  startedAt: ISODate | null;
  cancelledAt: ISODate | null;
  rider: PersonBrief;
  driver: PersonBrief | null;
  timeline: TimelineEntry[];
  offers: OfferRow[];
  track: TrackPoint[];
  payments: PaymentRow[];
  sos: SosRow[];
}

export interface DriverDetail {
  row: DriverRow;
  email: string | null;
  licenseNo: string;
  vehicleYear: number;
  statusReason: string | null;
  approvedAt: ISODate | null;
  lastLoginAt: ISODate | null;
  lat: number | null;
  lng: number | null;
  earnedThisWeek: number;
  tripsThisWeek: number;
  recentRides: RideRow[];
  ledger: LedgerLine[];
}

export interface RiderRow {
  id: number;
  name: string;
  phone: string;
  email: string | null;
  status: 'ACTIVE' | 'BLOCKED';
  rating: number;
  ratingCount: number;
  wallet: number;
  rides: number;
  simulated: boolean;
  createdAt: ISODate;
  lastLoginAt: ISODate | null;
}

export interface RiderDetail {
  row: RiderRow;
  statusReason: string | null;
  recentRides: RideRow[];
  wallet: WalletLine[];
  places: SavedPlace[];
}

export interface VehicleTypeDto {
  code: string;
  name: string;
  description: string;
  capacity: number;
  baseFare: number;
  baseKm: number;
  perKm: number;
  perMin: number;
  minFare: number;
  waitFreeMin: number;
  waitPerMin: number;
  cancelFee: number;
  sortOrder: number;
  active: boolean;
  updatedAt: ISODate;
}

export interface SurgeZoneDto {
  id: number;
  name: string;
  lat: number;
  lng: number;
  radiusM: number;
  mode: 'AUTO' | 'MANUAL';
  manualMultiplier: number;
  active: boolean;
  currentMultiplier: number;
  demand: number | null;
  supply: number | null;
  updatedAt: ISODate;
}

export interface PromoDto {
  code: string;
  description: string;
  percentOff: number | null;
  flatOff: number | null;
  maxDiscount: number | null;
  minFare: number;
  usesPerRider: number;
  validUntil: string | null;
  active: boolean;
  totalUses: number;
  createdAt: ISODate;
}

export interface SettingDto {
  key: string;
  category: string;
  label: string;
  description: string;
  type: 'INT' | 'DECIMAL' | 'BOOL';
  value: string;
  defaultValue: string;
  min: number;
  max: number;
  unit: string;
}

export interface FinanceTotals {
  trips: number;
  gmv: number;
  tax: number;
  discounts: number;
  commission: number;
  driverEarnings: number;
  tips: number;
  cancellationFees: number;
  platformNet: number;
  cash: number;
  digital: number;
}

export interface PayoutRow {
  driverId: number;
  name: string;
  plate: string;
  balance: number;
  tripsThisWeek: number;
  earnedThisWeek: number;
  simulated: boolean;
}

export interface Finance {
  days: number;
  totals: FinanceTotals;
  daily: DayPoint[];
  byMethod: { method: string; trips: number; amount: number }[];
  byType: TypeSplit[];
  payouts: PayoutRow[];
}

export interface AuditEntry {
  id: number;
  actor: string;
  action: string;
  target: string;
  details: string;
  at: ISODate;
}
