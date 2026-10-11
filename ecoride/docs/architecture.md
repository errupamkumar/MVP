# EcoRide · architecture and decisions

Audience: engineers extending the demo. For running it, see the [README](../README.md).

## Components

```
 Rider app (Expo)        Driver app (Expo)        Admin console (React + Vite)
   /api/rider/**            /api/driver/**            /api/admin/**
   /api/geo/**              /api/geo/**               /api/geo/**
        \                        |                        /
         +-------- Spring Boot API (JWT per role) -------+
                 |                |                 |
           MySQL 8 (ecoride)   OSRM (routes)    Nominatim (places)
```

Each app has its own accounts table (`rider`, `driver`, `admin_user`), sign-in endpoint and JWT role. A rider token is refused by the driver and admin APIs (403) and vice versa. A blocked rider, a rejected driver or a deleted admin loses access on the next request, not when the token expires (`AccountGuard`).

The apps poll: the rider's ride every 2 s, the driver's state every 2 s, the admin live map every 3 s. Countdowns use seconds computed on the server and then count down locally, so phone clocks and time zones never matter.

## Backend packages

| Package | Responsibility |
|---|---|
| `common` | config, JWT security, error envelope, settings (`Setting` enum + cached values), audit, money |
| `geo` | haversine, encoded polylines, `RouteTracker` (snap a fix to a route, distance left), `RoutingService` (OSRM + cache + straight-line fallback), `GeocodingService` (curated places + Nominatim, rate-limited and cached), service area |
| `pricing` | fare cards (`vehicle_type`), `FareCalculator`, promos, surge zones, upfront quotes |
| `rider`, `driver` | accounts, sign-up and sign-in, profiles, wallet, driver ledger and earnings, `DriverLocator` |
| `ride` | ride, offers, events, GPS trace; dispatch; lifecycle; location ingestion; settlement; the views the apps poll |
| `admin` | dashboard, live map, searches, approvals, pricing, settings, finance, audit |
| `demo` | seed data (Pune places, accounts, 30 days of history), simulated fleet and riders, reset |

## Ride states

```
SEARCHING --accept--> ACCEPTED --arrived--> ARRIVED --PIN--> IN_PROGRESS --complete--> COMPLETED
    |                    |   driver cancels: back to SEARCHING   |
    +--> NO_DRIVERS      +-------------> CANCELLED <-------------+  (rider, driver no-show, admin)
```

## Dispatch

- Candidates: drivers `ONLINE` and `APPROVED`, of the ride's type, with a GPS fix newer than 90 s, within 5 km (bounding box in SQL, exact haversine in Java). Drivers that have already seen this ride, or are holding another offer, are skipped.
- Order: nearest first. With `PREFER_REAL_DRIVERS` (demo), real drivers come before simulated ones.
- One offer at a time per ride (`uk_offer_pending_ride`), 15 s to answer; the sweep runs every second on one thread, plus an immediate kick after a request or a decline.
- After 120 s without an accept the ride ends as `NO_DRIVERS`.

## Live distance and the GPS odometer

- When a driver accepts, the road route from the cab to the pickup is planned (outside the database transaction).
- On every poll, the cab's last fix is projected onto the current leg's route (`RouteTracker.locate`). The distance left is measured along the route; the ETA uses that route's own speed. A cab more than 250 m off the route falls back to straight line × 1.35.
- Each driver fix is added to the ride's trace. Moves are summed into `pickup_travel_m` on the way and `actual_distance_m` with the rider on board, except fixes worse than 75 m accuracy, moves under 5 m, and jumps implying more than 150 km/h. Fixes flagged `simulated` (the demo GPS replay) skip the speed check.

## Fares

```
ride     = base fare (covers base km) + per-km beyond base km + per-minute, at least the minimum fare
surge    = ride × (multiplier − 1)                        (surge zones only, capped at SURGE_CAP)
waiting  = per-minute after the free wait at the pickup
gross    = ride + surge + waiting                          (the driver's 80% is taken from this)
discount = promo (percentage with cap, or flat), never more than gross; funded by EcoRide
total    = (gross − discount) + GST, rounded to whole rupees
```

At completion the upfront fare stands unless the GPS distance differs from the planned route by more than `FARE_RECALC_TOLERANCE_PCT` (20 %). Then it is recalculated from the GPS distance and the actual trip time. The receipt and the admin ride page both show the comparison.

## Concurrency decisions (keep these)

- **One lock order everywhere: the ride row, then the driver row.** Accept, arrive, start, complete, cancel and every GPS ping lock `ride` (`SELECT … FOR UPDATE`) before touching `driver`, so pings during completion cannot deadlock (covered by `ConcurrencyIntegrationTest`).
- **Driver position, availability, rating and counters change by single SQL updates** (compare-and-set for availability), with `@DynamicUpdate` on the entities, so a profile or status save never writes back a stale position.
- **The rider's shared position is a bulk update** on the ride, so a fix every few seconds never collides with a state change.
- **No foreign key from `ride_offer.driver_id` or the GPS trace**: inserting a child row takes a shared lock on the parent, and the driver row is updated by every ping.
- **READ COMMITTED**: after taking the ride lock, a transaction must read the latest committed driver and offer rows.
- **Invariants in the database**: unique keys on generated columns allow one ride in flight per rider, one assigned ride per driver and one pending offer per ride. A `CHECK` lets only approved drivers be online.

## API map

| Area | Endpoints |
|---|---|
| Public | `GET /api/public/config` |
| Rider account | `POST /api/rider/auth/signup`, `POST /api/rider/auth/login`, `GET/PUT /api/rider/me`, `GET/POST/DELETE /api/rider/places`, `GET /api/rider/wallet`, `POST /api/rider/wallet/topup` |
| Rider rides | `GET /api/rider/nearby`, `POST /api/rider/quotes`, `POST /api/rider/rides`, `GET /api/rider/rides/current`, `GET /api/rider/rides/{id}`, `GET /api/rider/rides`, `POST /api/rider/rides/{id}/cancel`, `/location`, `/rate`, `/sos` |
| Driver | `POST /api/driver/auth/signup`, `/login`; `GET /api/driver/me`, `GET /api/driver/state`, `POST /api/driver/online`, `/offline`, `/location`; `POST /api/driver/offers/{id}/accept`, `/decline`; `GET /api/driver/rides/{id}`, `POST …/arrived`, `/start`, `/complete`, `/cash-collected`, `/cancel`, `/rate`, `/sos`; `GET /api/driver/rides`, `GET /api/driver/earnings` |
| Maps | `GET /api/geo/search`, `GET /api/geo/reverse`, `GET /api/geo/route` |
| Admin | `POST /api/admin/auth/login`; `GET /api/admin/dashboard`, `/live`; rides (list, detail, cancel, refund); drivers (list, counts, detail, approve, reject, suspend, reactivate, offline, payout); riders (list, counts, detail, block, unblock, wallet credit); `vehicle-types`, `fare-preview`, `surge-zones`, `promos`, `settings`, `finance`, `payments`, `sos`, `audit`, `demo/reset` |

Full contract with request and response schemas: Swagger UI at `/swagger-ui.html` (OpenAPI at `/v3/api-docs`).

## What a production version adds

Push (WebSocket or FCM) instead of polling; a paid map stack (Google or Ola Maps) with traffic-aware ETAs and turn-by-turn navigation; real payment gateway and UPI collect; document upload and KYC for drivers; OTP sign-in by SMS; batched matching across nearby requests; geospatial indexing (H3 or PostGIS) at scale; and fraud checks on GPS.

## Sources for the market rules

- Motor Vehicle Aggregator Guidelines 2025 (MoRTH): surge cap, 80 % driver share, cancellation penalty, base fare for the first 3 km. Reported by [Entrackr](https://entrackr.com/news/govt-revises-ride-hailing-rules-2x-surge-cap-cancellation-fee-and-push-for-bike-taxis-9455586) and [Outlook Business](https://www.outlookbusiness.com/news/ola-uber-can-now-charge-2x-fare-during-peak-hours-under-new-govt-rules).
- Maharashtra aggregator rules, 1.5× surge cap: [Trak.in](https://trak.in/stories/overcharging-by-ola-uber-rapido-will-stop-after-sep-1-in-maharashtra/).
- Matching and the accept window: [Uber marketplace · matching](https://www.uber.com/us/en/marketplace/matching/), [The Rideshare Guy](https://therideshareguy.com/uber-extends-offer-acceptance-time-ping-for-drivers/).
