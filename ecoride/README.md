# EcoRide · on-demand ride hailing (demo)

An Uber-style system for Pune, built by SRM ECO TECH as a working demo: **a rider app, a driver app and an admin console**, each with its own accounts, sharing one backend.

| Part | What it does | Stack | Runs on |
|---|---|---|---|
| **Rider app** (`apps/rider`) | Sign up / sign in, GPS pickup, place search, upfront fares for Bike · Auto · Mini · Sedan · SUV, promo codes, UPI / cash / wallet, live tracking of the cab with road distance and ETA, PIN to start, SOS, share trip, receipt with the GPS distance check, rating and tip, ride history, wallet | Expo SDK 57, React Native, Expo Router, TanStack Query | Android / iOS (Expo Go) and web |
| **Driver app** (`apps/driver`) | Apply to drive (personal details → vehicle → licence), wait for approval, go online / offline, ride requests with a 15 s countdown, navigate to pickup, arrive (geofenced), start with the rider's PIN, drive, complete, collect cash, rate the rider, earnings and payouts; real GPS or a demo GPS simulator | Expo SDK 57 | Android / iOS (Expo Go) and web |
| **Admin console** (`apps/admin`) | Full access: dashboard, live map of every driver and ride, rides with planned-route-vs-GPS-trace, dispatch attempts and timeline, cancel and refund, driver approvals / suspensions / payouts, riders (block, wallet credit), fare cards with a calculator, surge zones, promotions, finance, SOS, settings, audit log, demo reset | React 19, Vite, Tailwind, Leaflet | Browser |
| **Backend** (`backend`) | Accounts and JWT per app, quotes and fares, dispatch, GPS ingestion and trip measurement, settlement, payments, ledger, admin APIs, demo data and a simulated fleet | Spring Boot 3.5, Java 21, MySQL 8 | `localhost:9080` |

Maps: **OpenStreetMap** tiles, **OSRM** road routing and **Nominatim** geocoding (all free, no API keys). If they are unreachable the backend falls back to straight-line estimates and curated Pune places, so booking never stops.

---

## How a ride works

1. **Pickup from GPS.** The rider app reads the phone's location (outside Pune, the demo places the rider at Balewadi High Street) and names it by reverse geocoding.
2. **Upfront quote.** The backend plans the road route (OSRM), applies the traffic factor, checks surge at the pickup and any promo, and prices every ride type. The quote is held for 5 minutes, so the price the rider accepts is the price on the ride.
3. **Dispatch.** The ride is offered to **one driver at a time, nearest first**, among free, approved drivers of that type with a fresh GPS fix within 5 km. Each has **15 s** to accept; a decline or timeout moves it on. After 120 s the rider is told no cars are free. A single dispatcher thread means a driver never holds two offers.
4. **Live distances.** Every GPS fix from the driver app is snapped onto the planned road route; the rider sees the distance and ETA *left along the road*, not as the crow flies. The rider shares their own position with the driver while waiting.
5. **Arrival and PIN.** "Arrived" is accepted only within 250 m of the pickup. The trip starts only with the rider's 4-digit PIN. Free waiting (3–5 min by type), then a per-minute waiting charge.
6. **GPS odometer.** With the rider on board, fixes add up the distance actually driven (jumps faster than 150 km/h, poor-accuracy fixes and sub-5 m jitter are ignored).
7. **Final fare.** The upfront fare stands if the GPS distance is within **±20 %** of the planned route (plus any waiting charge); otherwise it is recalculated from the GPS distance and actual time. Payment by UPI (simulated collect), wallet (falls back to cash if short) or cash (the driver confirms collection).
8. **Money.** The driver keeps **80 %** of the fare before tax, tips in full; EcoRide keeps 20 % and funds promos. Every amount lands in the driver's ledger; payouts are made from the console.

### Market rules applied

Researched from current Indian practice (October 2026):

- **MoRTH Motor Vehicle Aggregator Guidelines 2025**: the base fare covers the first 3 km; drivers receive at least 80 % of the fare; a driver cancelling without cause pays 10 % of the fare, capped at ₹100; surge up to 2× nationally.
- **Maharashtra rules**: surge capped at **1.5×** the base fare (EcoRide's default `SURGE_CAP`).
- **Ride-hailing practice**: upfront pricing, sequential offers with a short accept window (10–15 s), free cancellation for a couple of minutes after acceptance, a cancellation fee after that or for a no-show, waiting charges after a free wait, 5 % GST on rides.

Every one of these numbers is a **setting** the admin console changes without a release.

---

## Run it

### Prerequisites

| Tool | Version |
|---|---|
| Java | 21 |
| Maven | 3.9+ |
| MySQL | 8.x on `localhost:3306`, user `root` / `root` (database `ecoride` is created automatically) |
| Node.js | 22.13+ (22.12 works with engine warnings) |
| Phone (optional) | **Expo Go** (SDK 57) on the same Wi-Fi as the laptop |

### 1 · Backend (port 9080)

```bash
cd backend
mvn -DskipTests package
java -jar target/ecoride-api.jar
```

Every start **drops, recreates and reseeds** the database (`DB_INIT_MODE=always`): demo accounts, 44 Pune places, fare cards, promos, surge zones, 25 drivers and 30 days of ride history. Set `DB_INIT_MODE=never` to keep data. Swagger UI: http://localhost:9080/swagger-ui.html

A **simulated fleet** (22 drivers, about 36 riders) keeps the city alive: simulated drivers cruise real roads, and simulated riders book every ~40 s. Simulated rides go only to simulated drivers. A real rider's request reaches a simulated driver only when no real driver nearby accepts. Turn it off with `DEMO_SIMULATION=false` or in Settings.

### 2 · Apps, from the `ecoride/` folder

```bash
npm install                 # once: installs all three apps and the shared packages (npm workspaces)

npm run rider               # Expo for the rider app   (press w for web, or scan the QR with Expo Go)
npm run driver              # Expo for the driver app
npm run admin               # Vite dev server for the admin console: http://localhost:5174
```

The apps find the API by themselves: in the browser they call the page's host on port 9080; in Expo Go they call the laptop running Metro. To point elsewhere, set `EXPO_PUBLIC_API_URL` (apps) or `VITE_API_URL` (admin).

**Production-style web builds** (what the demo recording uses):

```bash
npm run build:web           # exports both apps and builds the admin console
npm run serve:web           # rider http://localhost:9081 · driver http://localhost:9082 · admin http://localhost:9083
```

**On a laptop**, the driver app's *Demo GPS* (on by default on web) drives the cab along the road route, 8× real time; change the speed or switch to real GPS in the driver app's Profile. To test the rider app's GPS in Chrome: DevTools → Sensors → Location → e.g. `18.5705, 73.7745` (Balewadi High Street).

### Demo accounts

| App | Sign in | Password | Who |
|---|---|---|---|
| Rider | `9876500001` | `Rider@123` | Ananya Rao (₹500 wallet, Home and Work saved) |
| Rider | `9876500002` | `Rider@123` | Rohan Mehta |
| Driver | `9876600001` | `Driver@123` | Rahul Patil, Mini, MH12AB4521 (approved, starts offline near Baner) |
| Driver | `9876600002` | `Driver@123` | Sandeep Kulkarni: application **pending approval** |
| Driver | `9876600003` | `Driver@123` | Imran Shaikh: **suspended** (insurance expired) |
| Admin | `admin@ecoride.in` | `Admin@123` | Neha Joshi (full access) |

Promo codes: `FIRST50` (50 % off the first ride, up to ₹75), `ECO20` (20 %, up to ₹60, 5 rides), `WEEKEND25` (₹25 off above ₹150).

---

## Verify

```bash
cd backend && mvn test            # 35 tests: fares, polyline/route tracking, full ride lifecycle,
                                  # dispatch cascade, cancellations, no-show, GPS filtering,
                                  # role separation, concurrency (needs MySQL; uses ecoride_test)
node scripts/smoke.mjs            # 53 API checks of the whole journey against a running API
npm run typecheck                 # strict TypeScript for all three apps
```

Database invariants (`backend/src/main/resources/schema.sql`): one ride in flight per rider and one assigned ride per driver (unique keys on generated columns), one open offer per ride, only approved drivers online, valid statuses, ratings and fare-card values.

## Layout

```
ecoride/
├── backend/            Spring Boot API (common · geo · pricing · rider · driver · ride · admin · demo)
├── apps/rider/         Expo rider app            (src/app = screens, src/lib = API, queries, booking, position)
├── apps/driver/        Expo driver app           (src/lib/gps.ts = real GPS or the route simulator)
├── apps/admin/         React + Vite admin console (src/pages)
├── packages/core/      API types, client, polyline/geo, formatting (shared by all three apps)
├── packages/mobile/    UI kit, live map (Leaflet on web, react-native-maps on phones), location, auth
├── scripts/            smoke.mjs (API journey), serve-web.mjs (serve the web builds)
└── docs/               architecture and API notes, step-by-step demo
```

See [`docs/architecture.md`](docs/architecture.md) and the [step-by-step demo](docs/demo/index.html): a separate video for each app (rider and driver as phone screens, admin as a desktop page) plus a screenshot per step.
