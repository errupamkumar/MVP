# Stage 1 · REST API contract

Base URL `http://<host>:8080`. Machine-readable contract: [`openapi.json`](openapi.json) (generated from the code) and Swagger UI at **`/swagger-ui.html`**. The JSON samples below were captured from the running API during the golden-path test.

## Conventions

| Topic | Rule |
|---|---|
| Auth | `POST /api/auth/login` returns a JWT (HS256, 12 h). Send `Authorization: Bearer <token>`. Deactivated users lose access immediately, not when the token expires. |
| Roles | `EMPLOYEE` → `/api/rider/**` · `DRIVER` → `/api/driver/**` · `DESK`, `ADMIN` → `/api/desk/**` · `ADMIN` → `/api/admin/**`, `/api/billing/**` · any signed-in user → `/api/stops`, `/api/routes`, `/api/notifications` · public → `/api/auth/login`, `/api/public/**`, `/t/**`, Swagger, `/actuator/health` |
| Content | JSON in and out (the CSV export is the one exception). Money and km are numbers (₹, km). |
| Dates | `LocalDateTime` fields are plant-local wall time (IST) with no offset, e.g. `2026-09-30T14:05:12.123`. `Instant` fields (token expiry) end in `Z`. |
| Idempotency | `POST /api/rider/bookings` accepts `Idempotency-Key` (≤ 64 chars). The same key returns the same booking. The app sends one per booking attempt. |
| Live data | The app polls: booking every 4 s while live, driver queue and desk board every 5 s. |
| Concurrency | Admin rule and sequence updates carry the `version` the admin was looking at. A stale version returns `409 STALE_DATA`. |

### Error envelope

Every failure, including 401/403 from the security chain, has one shape:

```json
{
  "timestamp": "2026-09-30T03:01:49.2772685",
  "status": 400,
  "error": "Bad Request",
  "code": "VALIDATION_FAILED",
  "message": "riderPhone: Enter a valid phone number",
  "path": "/api/rider/bookings",
  "fieldErrors": [
    { "field": "riderPhone", "message": "Enter a valid phone number" },
    { "field": "rideType", "message": "Choose a ride type" },
    { "field": "riderName", "message": "Enter the rider's name" },
    { "field": "toStopId", "message": "Choose a drop stop" },
    { "field": "seats", "message": "must be greater than or equal to 1" }
  ]
}
```

`message` is written to be shown to the user as is. `code` is stable, and the app switches on it.

| HTTP | Codes |
|---|---|
| 400 | `VALIDATION_FAILED`, `MALFORMED_REQUEST`, `BAD_PARAMETER` |
| 401 | `UNAUTHENTICATED`, `BAD_CREDENTIALS` |
| 403 | `FORBIDDEN` |
| 404 | `NOT_FOUND` (also returned for another rider's booking, so its existence is not revealed) |
| 409 | Business rules: `MATCH_REJECTED`, `GROUP_TOO_LARGE`, `BOOKING_PAUSED`, `ACTIVE_RIDE_EXISTS`, `DUTY_BLOCKED`, `ALREADY_ON_DUTY`, `VEHICLE_IN_USE`, `VEHICLE_NOT_ALLOWED`, `VEHICLE_OFF_ROAD`, `TRIP_OPEN`, `NOT_AT_PICKUP`, `CANNOT_ARRIVE`, `WRONG_STOP`, `OTP_LOCKED`, `NO_SHOW_TOO_EARLY`, `NOT_CANCELLABLE`, `ROUTE_IN_USE`, `STALE_DATA`, `DATA_CONFLICT`, `BUSY` |
| 410 | `LINK_EXPIRED` (tracking link after the ride closed) |
| 422 | `OTP_INVALID`, `CHECKLIST_INCOMPLETE`, `ODOMETER_BELOW_LAST`, `ODOMETER_BELOW_START`, `NO_ROUTE`, `SAME_STOP`, `UNKNOWN_STOP`, `BAD_TIME`, `BAD_PERIOD` |
| 500 | `INTERNAL_ERROR` (logged server-side, no stack trace to the client) |

---

## Endpoints

### Auth & master data

| Verb | Path | Request | Response |
|---|---|---|---|
| POST | `/api/auth/login` | `{ username, password }` | `LoginResponse { token, expiresAt, user: UserSummary }` |
| GET | `/api/auth/me` | | `UserSummary { id, username, fullName, role, email, phone }` |
| GET | `/api/stops` | | `StopDto[] { id, code, name, zone, latitude, longitude, geofenceM }` |
| GET | `/api/routes` · `/api/routes/{id}` | | `RouteDto { id, code, label, routeType, frequencyMin, maxPassengers, maxWaitMin, maxDetourMin, speedLimitKmh, version, totalMinutes, totalKm, stops[] }` |

### Rider (`EMPLOYEE`)

| Verb | Path | Request | Response |
|---|---|---|---|
| GET | `/api/rider/home` | | `RiderHomeDto { profile, activeBooking?, upcomingCount, savedPlaces[], defaultFromStopId, nextShuttle?, unreadNotifications }` |
| GET | `/api/rider/profile` | | `EmployeeProfileDto { personnelNo, fullName, email, phone, department, grade, costCentreCode, costCentreName, exclusiveEligible, bookingPaused, noShowsInWindow }` |
| GET | `/api/rider/destinations?fromStopId=` | | `DestinationDto[] { stopId, stopName, routeCode, routeName, rideMinutes, rideKm }` (stops reachable forward on a route) |
| POST | `/api/rider/ride-options` | `RideOptionsRequest { fromStopId*, toStopId*, seats (1-12), scheduledAt? }` | `RideOptionsResponse` (below) |
| POST | `/api/rider/bookings` | `CreateBookingRequest` (below) + header `Idempotency-Key` | **201** `BookingDto` |
| GET | `/api/rider/bookings?scope=upcoming\|past\|cancelled` | | `BookingSummaryDto[]` |
| GET | `/api/rider/bookings/{id}` | | `BookingDto` (own bookings only) |
| POST | `/api/rider/bookings/{id}/cancel` | `{ reason? }` | `BookingDto`. Allowed any time before boarding. |
| POST | `/api/rider/bookings/{id}/rating` | `{ rating* 1-5, tags: [ON_TIME\|CLEAN_CAB\|SAFE_DRIVING\|LONG_WAIT\|HARD_TO_FIND], note? }` | `BookingDto` |
| POST | `/api/rider/sos` | `{ bookingId?, latitude?, longitude?, note? }` | `{ alertId, message }` |

`CreateBookingRequest`:

```json
{
  "fromStopId": 1, "toStopId": 4, "rideType": "SHARED", "seats": 1, "scheduledAt": null,
  "riderName": "A. Raghavan", "riderPhone": "+91 98450 41227", "riderEmail": "a.raghavan@lhs.co.in",
  "forGuest": false
}
```

`RideOptionsResponse` (C-12 has two riders; the shuttle has not started for the day):

```json
{
  "route": { "id": 1, "code": "R1", "name": "North corridor", "label": "Route 1 · North corridor" },
  "from": { "id": 1, "code": "GATE2", "name": "Gate 2" },
  "to": { "id": 4, "code": "BF", "name": "Blast Furnace" },
  "rideKm": 3.4, "rideMinutes": 15, "occupancyCap": 3,
  "costCentreCode": "CC-4471", "costCentreName": "Operations", "scheduledAt": null,
  "options": [
    { "type": "SHUTTLE", "bookable": false, "title": "Shuttle S-03",
      "subtitle": "First run 06:00 at Gate 2, then every 20 min", "etaMinutes": null, "capacity": 12,
      "vehicleCode": "S-03", "requiresApproval": false, "note": "Walk to Gate 2; the shuttle runs to a timetable." },
    { "type": "SHARED", "bookable": true, "title": "Shared cab C-12", "subtitle": "2 of 3 seats taken",
      "etaMinutes": 0, "seatsTaken": 2, "seatsFree": 1, "capacity": 3, "vehicleCode": "C-12",
      "requiresApproval": false, "note": null },
    { "type": "EXCLUSIVE", "bookable": true, "title": "Exclusive cab", "subtitle": "Needs approval from your HOD",
      "etaMinutes": null, "requiresApproval": true,
      "note": "No empty cab right now; it is matched when one frees up." }
  ]
}
```

`BookingDto` (abridged):

```json
{
  "id": 14803, "bookingCode": "PR-V39JAK", "status": "ASSIGNED", "rideType": "SHARED", "seats": 1,
  "riderName": "A. Raghavan", "riderPhone": "+91 98450 41227",
  "route": { "id": 1, "code": "R1", "label": "Route 1 · North corridor" },
  "fromStop": { "id": 1, "name": "Gate 2" }, "toStop": { "id": 4, "name": "Blast Furnace" },
  "createdAt": "2026-09-30T03:01:49.1565132", "assignedAt": "2026-09-30T03:01:49.1894569",
  "otp": "9846", "trackingToken": "WRPVHGG5", "trackingUrl": "http://localhost:8080/t/WRPVHGG5",
  "vehicle": { "code": "C-12", "registrationNo": "MH 12 AB 4412", "vehicleType": "CAB", "seatCapacity": 4 },
  "driver": { "name": "S. Kumar", "phone": "+91 98220 77201" },
  "tripCode": "T-0930-VQEH",
  "progress": {
    "stage": "ON_THE_WAY", "stageLabel": "Cab on the way",
    "etaToPickupMinutes": 0, "etaToDropMinutes": 17, "vehicleAt": "Gate 2",
    "coRiders": 2, "seatsTaken": 3, "capacity": 3,
    "stops": [ { "seq": 1, "stopName": "Gate 2", "pickup": true, "drop": false, "state": "NEXT" }, "…" ]
  },
  "costCentreCode": "CC-4471", "rideKm": 3.4, "rideMinutes": 15, "distanceKm": null, "costAmount": null,
  "matchNote": "C-12 · ETA 0 min · 2 of 3 seats taken · 0.0 empty km · own fleet · score -3.00",
  "canCancel": true, "canRate": false
}
```

`progress.stage`: `SCHEDULED`, `AWAITING_APPROVAL`, `FINDING_CAB`, `ON_THE_WAY`, `AT_PICKUP`, `ON_TRIP`, `COMPLETED`, `CLOSED`. `otp` is `null` once the ride is closed.

### Driver (`DRIVER`)

| Verb | Path | Request | Response |
|---|---|---|---|
| GET | `/api/driver/profile` | | `DriverProfileDto { driverCode, fullName, vendorName, gatesAllowed, maxDutyMinutes, documents[DocumentStatus], blockedReason?, defaultVehicleId, duty? }` |
| GET | `/api/driver/vehicles` | | `VehicleOptionDto[] { id, code, registrationNo, seatCapacity, vendorName, odometerKm, currentStopName, available, blockedReason?, documents[] }` |
| POST | `/api/driver/duty/start` | `{ vehicleId*, startOdometer*, checklist*: [TYRES, LIGHTS, BELTS, FUEL, FIRST_AID] }` | `DutyDto` |
| POST | `/api/driver/duty/end` | `{ endOdometer*, fuelLitres? }` | `DutySummaryDto { tripsCompleted, ridersCarried, gpsKm, odometerKm, odometerCheck: MATCHES\|REVIEW, … }` |
| GET | `/api/driver/queue` | | `DriverQueueResponse { trip: TripQueueDto?, notice? }` |
| POST | `/api/driver/trips/{tripId}/arrive` | `{ stopSeq* }` (must be the next stop; repeat taps are harmless) | `DriverQueueResponse` (`trip: null` when the last drop closes the trip) |
| POST | `/api/driver/trips/{tripId}/board` | `{ bookingId*, otp*: "\\d{4}" }` | `DriverQueueResponse`. Wrong code: 422 `OTP_INVALID`; after 5, 409 `OTP_LOCKED`. |
| POST | `/api/driver/trips/{tripId}/no-show` | `{ bookingId* }` | `DriverQueueResponse`. Only after the route's max wait. |
| POST | `/api/driver/location` | `{ latitude*, longitude*, speedKmh?, recordedAt? }` | `LocationAckDto { overspeed, speedLimitKmh, autoArrivedAt? }` |
| POST | `/api/driver/issues` | `{ type*: BREAKDOWN\|ACCIDENT\|DELAY\|FUEL\|OTHER, description*, litres? }` | `IssueResultDto { alertId, message, ridersRematched }` |
| POST | `/api/driver/sos` | `{ latitude?, longitude?, note? }` | `IssueResultDto` |

`TripQueueDto` (abridged), right after sign-on and the rider's booking:

```json
{
  "tripId": 1481, "tripCode": "T-0930-VQEH", "status": "PLANNED",
  "route": { "code": "R1", "label": "Route 1 · North corridor" },
  "capacity": 3, "onBoard": 0, "seatsLeft": 3, "currentSeq": null,
  "nextStop": { "seq": 1, "stopName": "Gate 2", "state": "NEXT", "etaMinutes": 0, "summary": "Pick up 3 riders",
    "pickups": [ { "bookingId": 4, "riderName": "M. Iyer", "personnelNo": "LHS-31890", "toStopName": "Coke Plant",
                   "status": "ASSIGNED", "noShowAllowedInSeconds": null, "otpLocked": false }, "…" ],
    "drops": [] },
  "canArriveNext": true, "arriveBlockedReason": null, "speedLimitKmh": 20,
  "stops": [ "…Gate 2 (pick up 3) · Coke Plant (drop 1) · Blast Furnace (drop 2)…" ]
}
```

### Desk (`DESK`, `ADMIN`)

| Verb | Path | Request | Response |
|---|---|---|---|
| GET | `/api/desk/board` | | `LiveBoardDto { generatedAt, kpis, vehicles[], alerts[], queue[] }` |
| GET | `/api/desk/queue` | | `QueueItemDto[]` (waiting for a cab or for approval) |
| GET | `/api/desk/alerts` | | `AlertDto[]` (open and acknowledged, most severe first) |
| POST | `/api/desk/alerts/{id}/ack` · `/resolve` | | `AlertDto` |
| GET | `/api/desk/bookings/{id}/candidates` | | `CandidateDto[] { vehicleCode, feasible, etaMinutes, seatsTaken, capacity, score, reason }` |
| POST | `/api/desk/bookings/{id}/approve` | | `DeskActionResultDto { message, booking }` |
| POST | `/api/desk/bookings/{id}/reject` · `/cancel` | `{ reason* }` | `DeskActionResultDto` |
| POST | `/api/desk/bookings/{id}/assign` | `{ vehicleId* }` | `DeskActionResultDto`. Hard rules still apply: 409 `MATCH_REJECTED` with the reason. |
| POST | `/api/desk/vehicles/{id}/status` | `{ status*: ACTIVE\|OFF_ROAD, note? }` | `{ message }`. Off-road re-matches its riders. |

```json
"kpis": { "vehiclesOnTrip": 4, "fleetSize": 9, "seatOccupancyPct": 60, "avgWaitMinutes": 6.2,
          "onTimePickupPct": 67, "ridesToday": 9, "openAlerts": 7, "waitingBookings": 1 }

"candidates": [
  { "vehicleCode": "C-12", "feasible": true, "etaMinutes": 0, "seatsTaken": 2, "capacity": 3, "score": -3,
    "reason": "C-12 · ETA 0 min · 2 of 3 seats taken · 0.0 empty km · own fleet · score -3.00" },
  { "vehicleCode": "S-03", "feasible": false, "reason": "S-03 runs the shuttle timetable and is not dispatched" },
  { "vehicleCode": "C-07", "feasible": false, "reason": "C-07 is on Route 2 · Mill corridor" }
]
```

### Admin & billing (`ADMIN`)

| Verb | Path | Request | Response |
|---|---|---|---|
| GET | `/api/admin/routes` | | `RouteDto[]` (including inactive) |
| PUT | `/api/admin/routes/{id}/rules` | `{ maxPassengers* 1-12, maxWaitMin* 1-30, maxDetourMin* 0-60, speedLimitKmh* 5-80, frequencyMin? 5-240, version* }` | `RouteDto`. Audited; triggers dispatch. |
| PUT | `/api/admin/routes/{id}/stops` | `{ version*, stops*: [{ stopId, legMinutes, legKm }] }` (2-30 stops; a loop must end where it starts) | `RouteDto`. 409 `ROUTE_IN_USE` while rides are live. |
| GET | `/api/admin/compliance` | | `ComplianceItemDto[]` (expiring or expired documents, soonest first) |
| GET | `/api/admin/audit?limit=50` | | `AuditLogDto[]` |
| POST | `/api/admin/demo/reset` | | `{ message }` (only when `DEMO_RESET_ENABLED=true`) |
| GET | `/api/billing/summary?period=yyyy-MM` | | `BillingSummaryDto` (below) |
| GET | `/api/billing/export?period=yyyy-MM` | | `text/csv` attachment, one line per ride, for the ERP import |

```json
{
  "period": "2026-09", "draft": true, "sharingRule": "DISTANCE",
  "totals": { "trips": 467, "rides": 947, "gpsKm": 1370.3, "amount": 27171.25, "ridersPerTrip": 2.03 },
  "costCentres": [ { "code": "CC-2210", "name": "Maintenance", "trips": 240, "rides": 240, "km": 701.5,
                     "amount": 8322.44, "budget": 11700, "budgetUsedPct": 71, "budgetStatus": "OK" }, "…" ],
  "vendors": [
    { "vendorName": "Vendor A (Shree Sai Travels)", "claimedKm": 344.7, "gpsKm": 342.7, "gapKm": 2, "gapPct": 0.6, "status": "MATCHED" },
    { "vendorName": "Vendor B (Deccan Fleet Services)", "claimedKm": 347.83, "gpsKm": 326.6, "gapKm": 21.23, "gapPct": 6.5,
      "claimedAmount": 7652.24, "gpsAmount": 7185.2, "status": "DISPUTE" }
  ],
  "compliance": { "documentsExpiringSoon": 2, "expiredDocuments": 2, "blockedVehicles": 1, "blockedDrivers": 1,
                  "fullyCompliantVehicles": 6, "totalVehicles": 9 }
}
```

### Notifications & public tracking

| Verb | Path | Auth | Response |
|---|---|---|---|
| GET | `/api/notifications?limit=50` | any role | `NotificationDto[] { id, category, title, body, bookingId?, createdAt, read }` |
| POST | `/api/notifications/read-all` | any role | `{ updated }` |
| GET | `/api/public/track/{token}` | **none** | `TrackingDto` (vehicle, route, stops, ETAs; no names or phones); **410** once the ride closes |
| GET | `/t/{token}` | **none** | The shareable HTML page, which polls the endpoint above every 5 s |

```json
{
  "token": "WRPVHGG5", "stage": "ON_THE_WAY", "stageLabel": "Cab on the way",
  "vehicleCode": "C-12", "registrationNo": "MH 12 AB 4412", "routeLabel": "Route 1 · North corridor",
  "fromStopName": "Gate 2", "toStopName": "Blast Furnace", "vehicleAt": "Gate 2",
  "etaToPickupMinutes": 0, "etaToDropMinutes": 17,
  "stops": [ { "seq": 1, "stopName": "Gate 2", "pickup": true, "drop": false, "state": "NEXT" },
             { "seq": 3, "stopName": "Coke Plant", "pickup": false, "drop": false, "state": "AHEAD" },
             { "seq": 4, "stopName": "Blast Furnace", "pickup": false, "drop": true, "state": "AHEAD" } ],
  "updatedAt": "2026-09-30T03:01:49.2944408"
}
```
