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
| 409 | Business rules: `MATCH_REJECTED`, `GROUP_TOO_LARGE`, `BOOKING_PAUSED`, `ACTIVE_RIDE_EXISTS`, `DUTY_BLOCKED`, `ALREADY_ON_DUTY`, `VEHICLE_IN_USE`, `VEHICLE_NOT_ALLOWED`, `VEHICLE_OFF_ROAD`, `TRIP_OPEN`, `NOT_AT_PICKUP`, `CANNOT_ARRIVE`, `WRONG_STOP`, `OTP_LOCKED`, `NO_SHOW_TOO_EARLY`, `NOT_CANCELLABLE`, `NOT_WAITING_FOR_CAB`, `ROUTE_IN_USE`, `STALE_DATA`, `DATA_CONFLICT`, `BUSY` |
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
| GET | `/api/stops` | | `StopDto[] { id, code, name, zone, landmark, latitude, longitude, geofenceM }` (landmark: where exactly to wait) |
| GET | `/api/routes` · `/api/routes/{id}` | | `RouteDto { id, code, label, routeType, frequencyMin, maxPassengers, maxWaitMin, maxDetourMin, speedLimitKmh, version, totalMinutes, totalKm, stops[] }` |

### Rider (`EMPLOYEE`)

| Verb | Path | Request | Response |
|---|---|---|---|
| GET | `/api/rider/home` | | `RiderHomeDto { profile, activeBooking?, upcomingCount, savedPlaces[], defaultFromStopId, nextShuttle?, unreadNotifications }` |
| GET | `/api/rider/profile` | | `EmployeeProfileDto { personnelNo, fullName, email, phone, department, grade, costCentreCode, costCentreName, exclusiveEligible, bookingPaused, noShowsInWindow }` |
| GET | `/api/rider/destinations?fromStopId=` | | `DestinationDto[] { stopId, stopName, routeCode, routeName, rideMinutes, rideKm }` (stops reachable forward on a route) |
| POST | `/api/rider/ride-options` | `RideOptionsRequest { fromStopId*, toStopId*, seats (1-12), scheduledAt? }` | `RideOptionsResponse` (below) |
| POST | `/api/rider/availability` | `{ fromStopId*, toStopId*, seats (1-12) }` | `AvailabilityDto` (below): when a ride can reach the pickup even if none is free, every cab on duty (driver, phone, seats, distance, when it frees up), and pickup points within walking distance |
| POST | `/api/rider/bookings` | `CreateBookingRequest` (below) + header `Idempotency-Key` | **201** `BookingDto` |
| GET | `/api/rider/bookings?scope=upcoming\|past\|cancelled` | | `BookingSummaryDto[]` |
| GET | `/api/rider/bookings/{id}` | | `BookingDto` (own bookings only) |
| GET | `/api/rider/bookings/{id}/availability` | | `AvailabilityDto` with `search`: seconds searching, seconds since the last check, next automatic check, check interval, seconds until the request expires, last engine note |
| POST | `/api/rider/bookings/{id}/search` | | `BookingDto`. Search again now: matches a waiting request immediately. A booking that is not waiting, or was checked in the last 3 s, is returned unchanged. |
| POST | `/api/rider/bookings/{id}/location` | `{ latitude*, longitude*, accuracyM? 0-5000, ageSeconds? 0-600 }` | `RiderLocationAckDto { shared, sharedWith, pickupName, metresToPickup?, atPickup, outsideServiceArea, message? }`. Shares the rider's position with the driver coming for them; only while the cab is on its way (`ASSIGNED`), else 409 `NOT_WAITING_FOR_CAB`. See [Live location](#live-location). |
| DELETE | `/api/rider/bookings/{id}/location` | | **204**. Stop sharing: the position is deleted and the driver stops seeing it at once. |
| POST | `/api/rider/bookings/{id}/cancel` | `{ reason? }` | `BookingDto`. Allowed any time before boarding. |
| POST | `/api/rider/bookings/{id}/rating` | `{ rating* 1-5, tags: [ON_TIME\|CLEAN_CAB\|SAFE_DRIVING\|LONG_WAIT\|HARD_TO_FIND], note? }` | `BookingDto` |
| POST | `/api/rider/sos` | `{ bookingId?, latitude?, longitude?, note? }` | `{ alertId, message }` |

`AvailabilityDto` when C-12 is full and N. Gupta asks for Gate 2 → Blast Furnace (trimmed; real response). Cabs that cannot take the rider now show when they could reach the pickup: after the last drop of their current run plus the drive over, or not at all (off-road, blocked, or the shift ends first). The shuttle is listed as walk-up. `search` (last) comes only from `GET /api/rider/bookings/{id}/availability` for a waiting request; `POST /api/rider/availability` returns it as null.

```json
{
  "checkedAt": "2026-10-01T00:39:01", "route": { "code": "R1", "label": "Route 1 · North corridor" },
  "pickup": { "code": "GATE2", "name": "Gate 2", "zone": "Z1", "landmark": "Security cabin, visitor lane" },
  "vehiclesOnDuty": 6, "availableNow": false,
  "summary": "No cab is free right now. The earliest is C-33 at Gate 2 in about 19 min (after its last drop at Rolling Mill, then 11 min to Gate 2).",
  "upcomingAtPickup": [
    { "kind": "CAB", "vehicleCode": "C-33", "driverName": "V. Rao", "inMinutes": 19, "distanceKm": 3.69, "availableNow": false,
      "note": "After its last drop at Rolling Mill, then 11 min to Gate 2" },
    { "kind": "CAB", "vehicleCode": "C-12", "driverName": "S. Kumar", "inMinutes": 28, "distanceKm": 0, "availableNow": false, "…": "…" }
  ],
  "nearbyCabs": [
    { "vehicleCode": "C-12", "registrationNo": "MH 12 AB 4412", "fleet": "LHS own fleet", "driverName": "S. Kumar",
      "driverPhone": "+91 98220 77201", "status": "FULL", "statusLabel": "Full · Route 1 · North corridor", "at": "Gate 2",
      "distanceKm": 0, "seatsTaken": 3, "capacity": 3, "canTakeYou": false, "availableInMinutes": 28 },
    { "vehicleCode": "C-21", "driverName": "A. Singh", "status": "UNAVAILABLE", "distanceKm": 0.98, "availableInMinutes": null,
      "note": "Driver A. Singh would pass the duty-hour limit (8h52m of 9h00m used)", "…": "…" }
  ],
  "alternativeSpots": [
    { "stop": { "name": "Admin Block", "landmark": "Main porch, facing the flag post" }, "walkMetres": 911, "walkMinutes": 13,
      "rideInMinutes": 20, "readyInMinutes": 20, "vehicleCode": "C-33", "sooner": false }
  ],
  "search": { "searchingForSeconds": 40, "lastCheckedSecondsAgo": 40, "nextCheckInSeconds": 7, "checkEverySeconds": 15,
              "expiresInSeconds": 5360, "expiresAt": "2026-10-01T02:08:21", "lastResult": "No cab free yet: …" }
}
```

Pickup points within `NEARBY_SPOT_RADIUS_M` (setting, default 1200 m straight line) that are on a route to the same drop are suggested; walking is estimated at 75 m/min over 1.25 × the straight line. `sooner` is true when walking there and catching the ride beats waiting at the chosen pickup.

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
  "driver": { "name": "S. Kumar", "phone": "+91 98220 77201", "fleet": "LHS own fleet" },
  "cabLocation": { "vehicleCode": "C-12", "latitude": 18.624, "longitude": 73.8003, "accuracyM": 8, "speedKmh": 14.4,
                   "heading": 205, "updatedAt": "2026-10-01T21:53:57", "secondsAgo": 0, "live": true,
                   "nearStopName": null, "towardsStopName": "Gate 2", "metresToStop": 365 },
  "myLocation": { "latitude": 18.6214, "longitude": 73.7987, "accuracyM": 12, "updatedAt": "2026-10-01T21:53:56",
                  "secondsAgo": 1, "live": true, "pickupName": "Gate 2", "metresToPickup": 31, "atPickup": true,
                  "metresFromCab": 335 },
  "tripCode": "T-0930-VQEH",
  "progress": {
    "stage": "ON_THE_WAY", "stageLabel": "Cab on the way",
    "etaToPickupMinutes": 0, "etaToDropMinutes": 17, "vehicleAt": "Gate 2",
    "coRiders": 2, "seatsTaken": 3, "capacity": 3,
    "stops": [ { "seq": 1, "stopName": "Gate 2", "latitude": 18.6212, "longitude": 73.7985, "pickup": true, "drop": false,
                 "state": "NEXT" }, "…" ]
  },
  "costCentreCode": "CC-4471", "rideKm": 3.4, "rideMinutes": 15, "distanceKm": null, "costAmount": null,
  "matchNote": "C-12 · ETA 0 min · 2 of 3 seats taken · 0.0 empty km · own fleet · score -3.00",
  "canCancel": true, "canRate": false
}
```

`progress.stage`: `SCHEDULED`, `AWAITING_APPROVAL`, `FINDING_CAB`, `ON_THE_WAY`, `AT_PICKUP`, `ON_TRIP`, `COMPLETED`, `CLOSED`. `otp` is `null` once the ride is closed. `cabLocation` is set while the booking is `ASSIGNED` (distance to the pickup) or `ONBOARD` (distance to the drop); `myLocation` only while `ASSIGNED` and the rider shares.

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
| POST | `/api/driver/location` | `{ latitude*, longitude*, speedKmh?, accuracyM? 0-5000, heading? 0-359, ageSeconds?, recordedAt? }` | `LocationAckDto { overspeed, speedLimitKmh, autoArrivedAt?, outsideServiceArea }`. Moves the cab on its riders' screens. Send `ageSeconds` (how old the fix is on the phone) rather than `recordedAt`; `recordedAt` is for AIS-140 boxes that buffer. See [Live location](#live-location). |
| POST | `/api/driver/issues` | `{ type*: BREAKDOWN\|ACCIDENT\|DELAY\|FUEL\|OTHER, description*, litres? }` | `IssueResultDto { alertId, message, ridersRematched }` |
| POST | `/api/driver/sos` | `{ latitude?, longitude?, note? }` | `IssueResultDto` |

`TripQueueDto` (abridged), right after sign-on and the rider's booking:

```json
{
  "tripId": 1481, "tripCode": "T-0930-VQEH", "status": "PLANNED",
  "route": { "code": "R1", "label": "Route 1 · North corridor" },
  "capacity": 3, "onBoard": 0, "seatsLeft": 3, "currentSeq": null,
  "nextStop": { "seq": 1, "stopName": "Gate 2", "latitude": 18.6212, "longitude": 73.7985, "state": "NEXT",
    "etaMinutes": 3, "summary": "Pick up 3 riders",
    "pickups": [ { "bookingId": 4, "riderName": "M. Iyer", "personnelNo": "LHS-31890", "toStopName": "Coke Plant",
                   "status": "ASSIGNED", "noShowAllowedInSeconds": null, "otpLocked": false,
                   "location": { "latitude": 18.6214, "longitude": 73.7987, "accuracyM": 12, "secondsAgo": 1, "live": true,
                                 "pickupName": "Gate 2", "metresToPickup": 31, "atPickup": true, "metresFromCab": 335, "…": "…" } },
                 "…" ],
    "drops": [] },
  "canArriveNext": true, "arriveBlockedReason": null, "speedLimitKmh": 20,
  "cab": { "vehicleCode": "C-12", "latitude": 18.624, "longitude": 73.8003, "live": true, "secondsAgo": 0,
           "towardsStopName": "Gate 2", "metresToStop": 365, "…": "…" },
  "stops": [ "…Gate 2 (pick up 3) · Coke Plant (drop 1) · Blast Furnace (drop 2)…" ]
}
```

### Live location

The driver's phone moves the cab on its riders' screens, and a waiting rider can show the driver where they are.

| Rule | Detail |
|---|---|
| Who sends | The driver app while on duty, every ~10 s, and at least every 30 s while parked. The rider app only while their cab is on its way (`ASSIGNED`), every ~15 s. |
| Who sees | The cab's position: its riders (`BookingDto.cabLocation`) and its driver (`TripQueueDto.cab`). A rider's position: the driver of the cab coming for them (`QueueRiderDto.location`) and the rider (`BookingDto.myLocation`). Not the desk, not the public tracking link. |
| Live | `live` is true when the phone reported in the last 2 minutes. A quiet cab keeps its last fix (or the last stop it confirmed) with `live: false`; speed, heading and accuracy are then null. |
| Retention | Only the rider's latest fix is kept (`rider_location`, no history). It is deleted when they board, stop sharing (`DELETE …/location`) or the booking closes (swept every 5 min), and not shown once older than 15 min. The cab keeps its GPS history in `gps_ping`, as before. |
| Service area | A fix farther than `SERVICE_AREA_RADIUS_M` (setting, default 5000 m) from every active pickup point is ignored: `outsideServiceArea: true`. This stops a GPS glitch, or a laptop running the web app, from moving a cab or a rider. `0` turns the check off. |
| Out of order | A driver fix older than the cab's last one is stored in history but does not move the cab, alert or trigger a geofence arrival. |
| ETAs | A cab starts from the last stop it confirmed, unless its live GPS puts it more than 150 m (or twice the stop's arrival radius) away from it; then it starts from where it is. |
| Distances | `metresToStop`, `metresToPickup`, `metresFromCab` are straight-line metres. |

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
| GET | `/api/admin/stops` | | `PickupPointDto[] { id, code, name, zone, landmark, latitude, longitude, geofenceM, active, routes[], liveBookings }` (in service first) |
| POST | `/api/admin/stops` | `{ code* [A-Z0-9-]{2,20}, name*, zone*, landmark?, latitude*, longitude*, geofenceM* 10-500 }` | **201** `PickupPointDto`. 409 `DUPLICATE_CODE` / `DUPLICATE_NAME`; 422 `FAR_FROM_PLANT` (over 20 km from every other point). Audited. |
| PUT | `/api/admin/stops/{id}` | same body plus `active` (code is fixed) | `PickupPointDto`. Taking a point out of service: 409 `STOP_ON_ROUTE` while a route's sequence uses it, 409 `STOP_IN_USE` while open bookings do. Audited. |
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
