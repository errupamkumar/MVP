# Plant Ride · MVP

Smart cab booking, pooling, fleet compliance and cost-centre billing for movement inside the LHS plant, built from the SRM ECO TECH proposal decks.

- **Rider app:** book in under 30 seconds, see the cab and driver, share a live tracking link, board with a 4-digit OTP, rate the ride.
- **Driver app:** start-of-duty check with a compliance gate, pickups in route order, OTP boarding, no-show after the wait, issues and SOS, sign-off with an odometer-vs-GPS check.
- **Desk and admin console:** live fleet board with KPIs and alerts, queue with approvals and overrides (the engine explains every decision), billing by cost centre with vendor-bill-vs-GPS reconciliation and a SAP CSV export, route rules and sequence as configuration.

**Verified state:** 43/43 backend tests (unit, web slice, MySQL integration including a concurrent last-seat race); a 43-check API smoke test of the golden path; a headless-browser UI walk of all three roles; strict TypeScript; web and Android bundles build. See [`docs/03-self-test-and-review.md`](docs/03-self-test-and-review.md).

| Docs | |
|---|---|
| [01 · Plan & architecture](docs/01-plan-architecture.md) | Golden path, scope vs the decks, architecture, domain rules, ER diagram, seed data |
| [02 · API contract](docs/02-api-contract.md) | Endpoints, DTOs with real samples, error codes. Generated contract: [`docs/openapi.json`](docs/openapi.json) |
| [03 · Self-test & review](docs/03-self-test-and-review.md) | Test results, 14 review findings with fixes, known limitations |
| [04 · Demo script](docs/04-demo-script.md) | The 5-minute client walkthrough, with credentials and recovery steps |
| [Demo walkthrough](docs/walkthrough/index.html) | Every screen, page by page with screenshots: employee, driver, transport desk, exception paths. Published copy: https://claude.ai/artifact/V7xxt1JRWaBXrDwKfG19Ej |

---

## Repository layout

```
.
├── backend/                      Spring Boot 3.5 · Java 21 · Maven
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/
│       ├── main/java/com/srmecotech/plantride/
│       │   ├── common/           config · JWT security · error envelope · audit · settings
│       │   ├── identity/         users, employees, login
│       │   ├── masterdata/       stops, routes (RoutePlan), fleet, drivers, compliance
│       │   ├── matching/         MatchingEngine · DispatchService · EtaCalculator · 15 s sweep
│       │   ├── trip/             duties, driver queue, arrive / board / no-show, issues
│       │   ├── booking/          rider API, ride options, progress, public tracking
│       │   ├── gps/              ping ingest, overspeed, geofence arrival
│       │   ├── safety/           alerts, document-expiry monitor
│       │   ├── billing/          cost allocation, monthly summary, CSV export
│       │   ├── notification/     in-app notifications
│       │   └── desk/             live board, queue actions, admin routes/rules/audit
│       ├── main/resources/
│       │   ├── application.yml
│       │   ├── schema.sql        19 tables, DB-enforced invariants
│       │   ├── data.sql          demo cast + 30 days of history (dates relative to NOW())
│       │   └── static/track.html shareable tracking page (/t/<token>)
│       └── test/                 unit · @WebMvcTest · @SpringBootTest (MySQL)
├── mobile/                       Expo SDK 57 · React Native 0.86 · TypeScript · NativeWind
│   └── src/
│       ├── app/                  Expo Router screens: login, rider/, driver/, desk/
│       ├── api/                  axios client (base URL, timeout, auth + error interceptors), types, endpoints
│       ├── auth/                 session context, SecureStore / localStorage
│       ├── hooks/                TanStack Query hooks (polling, invalidation), driver GPS
│       ├── components/           UI kit, error boundary, pickers, modals
│       ├── features/             booking draft, driver sign-on and trip queue
│       └── lib/                  plant-time formatting, cross-platform dialogs, theme
├── scripts/smoke-golden-path.mjs end-to-end API check of the golden path (and demo reset)
├── docker-compose.yml            MySQL 8.4 + API
└── docs/
```

---

## Prerequisites

| Tool | Version |
|---|---|
| Java | 21 |
| Maven | 3.9+ |
| MySQL | 8.x on `localhost:3306`, user `root` / `root` (or Docker, below) |
| Node.js | 22.13+ or 20.19.4+ (React Native 0.86 requirement; 22.12 works with engine warnings) |
| Phones | **Expo Go** (SDK 57) from the Play Store / App Store, on the same Wi-Fi as the laptop |

## Run it

### 1 · Database

Nothing to create: the API connects to `localhost:3306` as `root/root` and creates `app_mvp` on first start.

### 2 · API

```bash
cd backend
mvn spring-boot:run
# or: mvn -DskipTests package && java -jar target/plant-ride-api.jar
```

Wait for `Started PlantRideApplication`. Then:

- Swagger UI: http://localhost:8080/swagger-ui.html (log in with `POST /api/auth/login`, then **Authorize**)
- Health: http://localhost:8080/actuator/health

> Every start **drops, recreates and reseeds** `app_mvp` (`DB_INIT_MODE=always`), so each demo starts from the same state. Set `DB_INIT_MODE=never` to keep data between restarts.

### 3 · Check the golden path end to end (optional, 5 s)

```bash
node scripts/smoke-golden-path.mjs          # ends by resetting the demo data
```

### 4 · Mobile app and desk console

```bash
cd mobile
npm install
npx expo start
```

- Press **`w`**: the web build opens at http://localhost:8081. This is the desk console on a laptop, and works for rider and driver too.
- Scan the QR code with **Expo Go**: rider or driver app on a phone.

The app finds the API by itself: on the web it uses the page's host on port 8080, and in Expo Go it uses the laptop running Metro. To point it elsewhere, create `mobile/.env` with `EXPO_PUBLIC_API_URL=http://<host>:8080` (see `.env.example`). The address in use is printed at the bottom of the login screen.

**Phones cannot connect?** Same Wi-Fi as the laptop, and allow Java through the Windows firewall on port 8080 (or try `npx expo start --tunnel` for Metro plus `EXPO_PUBLIC_API_URL` for the API).

### Alternative · Docker

```bash
docker compose up -d --build      # MySQL 8.4 (host port 3307) + API (port 8080)
docker compose logs -f api        # wait for "Started PlantRideApplication"
```

Then run the mobile app as in step 4.

---

## Demo accounts

Password for every account: **`Plant@123`**. The login screen offers one-tap demo accounts.

| Login | Who | Use |
|---|---|---|
| `LHS-40218` | A. Raghavan · Operations · CC-4471 | Rider (golden path) |
| `DRV-2281` | S. Kumar · cab C-12 | Driver (golden path) |
| `admin` | R. Kapoor | Desk + billing + routes & rules |
| `desk` | J. Thomas | Desk only (no billing or setup) |
| `LHS-44190` | N. Gupta | Second rider (full-cab scenario) |
| `LHS-52287` | H. Patel | Booking paused (3 no-shows) |
| `DRV-2310` / `DRV-1466` | K. Joshi / M. Ali | Duty blocked (gate pass / C-09 insurance expired) |

Riders waiting at Gate 2 at start: M. Iyer (OTP **9317**) and S. Banerjee (OTP **5540**).

---

## Tests

```bash
cd backend && mvn test                 # 43 tests; integration tests need MySQL (database app_mvp_test, created automatically)
cd mobile  && npm run typecheck        # strict TypeScript
```

Integration tests read `TEST_DB_URL`, `TEST_DB_USERNAME` and `TEST_DB_PASSWORD` (default: local `root/root`). Each test reloads the seed, so tests are independent.

---

## Configuration

API (environment variables, defaults in `application.yml`):

| Variable | Default | Purpose |
|---|---|---|
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | local `app_mvp`, `root` / `root` | MySQL connection |
| `DB_INIT_MODE` | `always` | `always` reseeds on every start; `never` keeps data |
| `JWT_SECRET` | dev value (logged as a warning) | HS256 key, 32+ characters. **Set it outside local development.** |
| `JWT_TTL_HOURS` | `12` | Token lifetime (one shift) |
| `PUBLIC_BASE_URL` | `http://localhost:8080` | Base of shared tracking links |
| `CORS_ORIGINS` | `*` | Allowed browser origins (comma-separated) |
| `DISPATCH_ENABLED` / `DISPATCH_INTERVAL_MS` | `true` / `15000` | The matching sweep |
| `DEMO_RESET_ENABLED` | `true` | Allows `POST /api/admin/demo/reset`. **Set `false` outside demos.** |
| `SERVER_PORT` | `8080` | |

Plant rules that LHS changes at runtime live in the `app_setting` table: cost sharing rule (`DISTANCE`/`EQUAL`), no-show pause (3 in 30 days), dispatch lead time (20 min), advance booking window (7 days), OTP attempts (5), document alert window (30 days), request expiry (90 min). Per-route rules (occupancy cap, wait, detour, speed limit) are edited in **Routes & rules**.

App (`mobile/.env`): `EXPO_PUBLIC_API_URL`, `EXPO_PUBLIC_SHOW_DEMO_ACCOUNTS=false` to hide the demo accounts.

### Before a pilot with real data

Set `JWT_SECRET`, `CORS_ORIGINS`, `PUBLIC_BASE_URL`, `DEMO_RESET_ENABLED=false` and `DB_INIT_MODE=never`; move the schema to Flyway; serve the API over HTTPS (Android release builds block cleartext HTTP); hide the demo accounts. See the limitations in `docs/03-self-test-and-review.md` §4.
