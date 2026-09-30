# Stage 3 · Self-test & adversarial review

## 1. What was run

| Layer | What | Result |
|---|---|---|
| Unit | `SeatLoadTest`, `TripItineraryTest`, `MatchingEngineTest` (Mockito), `BillingServiceTest` (Mockito) | 19 tests ✅ |
| Web slice (`@WebMvcTest`) | `RiderControllerWebMvcTest`, `AuthControllerWebMvcTest`, through the **real** security chain and JWT filter | 9 tests ✅ |
| Integration (`@SpringBootTest` + MySQL `app_mvp_test`) | `GoldenPathIntegrationTest`, `OccupancyAndConcurrencyIntegrationTest`, `ExceptionPathsIntegrationTest`. Each test reloads the demo seed and commits real transactions. | 15 tests ✅ |
| **Total** `mvn test` | | **43 / 43 passing** |
| API end-to-end | `node scripts/smoke-golden-path.mjs` against the running jar: all 8 golden-path handovers, the cap, idempotency, tracking-link expiry, billing export, audit | 43 checks ✅ |
| UI end-to-end | Headless Chrome drives the **real web build**: desk (1440 px), driver and rider (390 px), desk tablet (820 px). Sign-on, book, board by OTP, drop, rate, billing, setup | 18 screenshots reviewed, 0 console errors ✅ |
| Mobile build | `tsc --noEmit` (strict, `noUnusedLocals`), `expo export` for web and Android (Hermes bytecode) | ✅ |

Hibernate `ddl-auto: validate` runs on every start, so every entity is checked against `schema.sql`.

### What the tests pin down

- **Golden path, end to end.** Sign-on pools the two waiting riders. The rider sees *C-12, 2 of 3*, books, and gets vehicle, driver and OTP. The public link shows no names. Arrival, three OTP boardings, two drops. Trip cost ₹61.20 on 3.4 km. Distance split 15.47 / 22.87 / **22.86**, summing exactly to ₹61.20. Link returns 410, rating, CSV contains the ride, audit row, sign-off odometer check.
- **The occupancy cap is a hard rule.** Tested sequentially, under a two-thread race for the last seat, through a desk override (refused, with the reason), and as configuration (raising the cap to 4 admits the waiting rider immediately, and a stale-version save is refused).
- **Exception paths (deck 2, slide 15).** Expired gate pass and expired insurance block duty. A vendor driver cannot take an own-fleet cab. The check list and odometer are validated. Five wrong OTPs lock boarding. A no-show is refused before 5 minutes and allowed after (test clock). A trip where everyone no-shows closes as CANCELLED and bills nothing. Three no-shows pause booking. A breakdown takes the cab off-road and re-queues its riders. Resequencing is refused while the route is live. Stale requests expire. Access control holds.

---

## 2. Adversarial review: findings and fixes

Severity reflects impact at a client demo or in production. **Found by** records how each issue surfaced. Every fix is in the code and covered by the verification named.

### F1 · Two riders could both take the last seat (overbooking) · **Critical**

- **Found by:** `OccupancyAndConcurrencyIntegrationTest.raceForTheLastSeat`. Its first run returned `["ASSIGNED","ASSIGNED"]`: four riders in a three-rider cab.
- **Root cause:** matching did lock the route row (`SELECT … FOR UPDATE`) before reading seat loads, so the two bookings were serialised. But MySQL's default isolation is **REPEATABLE READ**. The second transaction had already taken its read snapshot (profile, no-show count and route lookups) before it waited for the lock, so after the first rider committed, its seat-load queries still read the old snapshot and saw "2 of 3".
- **Fix:** connections run at `READ COMMITTED` (`spring.datasource.hikari.transaction-isolation`), so reads made after acquiring the lock see the committed state. See `application.yml` and the note in `DispatchService`.
- **Verified:** the race test passes (5 of 5 repeated runs), and the cab never carries more than 3.

### F2 · The losing rider in that race got "409 BUSY" instead of a queued booking · **High**

- **Found by:** the same test, after F1. It returned `["409","ASSIGNED"]`.
- **Root cause:** a deadlock. Each transaction first inserted its booking. The foreign-key check on `booking.route_id` takes a **shared lock on the route row**. Both transactions then asked for the exclusive route lock, each waiting on the other's shared lock, and InnoDB killed one.
- **Fix:** take the route lock **before** inserting the booking (`DispatchService.lockRoute`, called from `RiderBookingService.create`). The second rider now waits at the lock, sees the cab is full, and is queued as `REQUESTED` with the reason recorded.
- **Verified:** race test, 5 of 5.

### F3 · A wrong OTP was not counted, so the 4-digit code could be brute-forced · **High** (design review)

- **Risk:** the obvious implementation increments `otp_attempts` and then throws. The exception rolls the transaction back, the increment disappears, and the attempt limit never trips (10,000 guesses).
- **Fix:** `TripService.board` is `@Transactional(noRollbackFor = OtpMismatchException.class)`: the counter commits even though the request fails. The comparison is constant-time, and 5 failures lock boarding for that rider.
- **Verified:** `ExceptionPathsIntegrationTest.otpBruteForceIsCapped` asserts the counter reads 1, 2, 3, 4, 5 in the database after each failed call, then `OTP_LOCKED`.

### F4 · Tracking links pointed at `localhost` and failed on a phone · **High** for the demo

- **Found by:** review of the rider confirmation screen. The server builds the link from `PUBLIC_BASE_URL`, which is `http://localhost:8080` on a laptop, and "localhost" on a phone is the phone.
- **Fix:** the app rebuilds the link from the token and the address it already uses to reach the API (`trackingLink()` in `mobile/src/api/client.ts`). `PUBLIC_BASE_URL` is documented for production.
- **Verified:** UI walk: the link on the booking screen uses the resolved host.

### F5 · CSV export returned 500 · **Medium**

- **Found by:** `smoke-golden-path.mjs` (the export check failed with `INTERNAL_ERROR`).
- **Root cause:** the endpoint declared `produces = "text/csv"`, so any client that did not send `Accept: text/csv` got `HttpMediaTypeNotAcceptableException`. The global handler had no mapping for it and reported a 500.
- **Fix:** removed the `produces` restriction (the response sets its own content type); added explicit 406 and 415 handlers so framework errors never masquerade as server faults. The app keeps axios' default `Accept` header.
- **Verified:** smoke test and `GoldenPathIntegrationTest` (export contains the booking).

### F6 · After-commit dispatch could turn a successful sign-on into a 500 · **Medium**

- **Found by:** code review of `DispatchTrigger`.
- **Risk:** dispatch runs after the sign-on transaction commits. If it threw (a DB blip), the exception reached the HTTP response. The driver would see an error for a sign-on that had succeeded, retry, and be told "already on duty": the client and server out of sync.
- **Fix:** the listener catches and logs. The 15-second sweep retries anything missed.

### F7 · Unserved requests never expired · **Medium**

- **Found by:** code review. A request no cab could serve stayed `REQUESTED` forever and could be matched hours later.
- **Fix:** the dispatcher closes requests older than `REQUEST_EXPIRY_MINUTES` (90, a setting) and tells the rider to book again.
- **Verified:** `ExceptionPathsIntegrationTest.staleRequestsExpire` (test clock +95 min; scheduled rides and approvals untouched).

### F8 · Driver's trip header rendered white-on-white · **High** UI

- **Found by:** UI-walk screenshot `03-driver-queue.png`: the dark header card was blank.
- **Root cause:** `Card` always added `bg-white`, and callers passed `bg-ink`. Tailwind resolves conflicting utilities by stylesheet order, not class order, so `bg-white` won. The same hit every tinted card and every `border-accent` override.
- **Fix:** `Card` only adds its default background and border colour when the caller does not override them.
- **Verified:** screenshots `03`, `06` and `08` after the fix.

### F9 · OTP entry overflowed sideways on narrow screens (web) · **Medium** UI

- **Found by:** UI-walk failure screenshot: the page scrolled horizontally at 390 px.
- **Root cause:** a web `TextInput` has an intrinsic minimum width, and `flex-1` alone does not let it shrink.
- **Fix:** `min-w-0` on the OTP and stop-search inputs.

### F10 · Confirmation dialogs silently did nothing on the web console · **High** UI (design review)

- **Risk:** React Native Web implements `Alert.alert` as a no-op. Every "Take off-road?", "Reset demo?" or "Sign out?" confirmation on the desk console would have done nothing, and `Alert.prompt` (reject reason) is iOS-only.
- **Fix:** `lib/dialogs.ts` uses `window.confirm` on web and native alerts elsewhere; `ReasonModal` replaces `Alert.prompt`.
- **Verified:** UI walk drives the desk on web.

### F11 · Background refetch overwrote the driver's odometer entry · **Medium** UI

- **Found by:** code review of `SignOnPanel`. An effect keyed on the vehicle object re-filled the field every time the vehicle list refetched.
- **Fix:** the reading is pre-filled only when a vehicle is chosen.

### F12 · Server timestamps could shift or fail to parse on phones · **Medium** (design review)

- **Risk:** `LocalDateTime` arrives with no offset and up to 7 fractional digits. `new Date(...)` interprets it in the phone's zone (wrong if the phone is not on IST), and some engines reject more than 3 fractional digits.
- **Fix:** `lib/format.ts` reads the digits straight from the string and never converts zones. Scheduled times are built in plant time (IST) regardless of the phone's zone. `Instant`s are parsed after trimming to milliseconds.

### F13 · Resequencing a route would hit its own unique key · **Medium** (design review)

- **Risk:** replacing a route's stops by clearing and re-adding the collection makes Hibernate insert the new rows **before** deleting the old ones, which violates `uk_route_stop_seq`.
- **Fix:** an explicit bulk delete, flushed, before the inserts (`RouteStopRepository.deleteByRouteId`). Resequencing is also refused while the route has live rides, because bookings and trips reference positions in the old sequence.
- **Verified:** `ExceptionPathsIntegrationTest.resequenceRules`.

### F14 · A brief server blip signed everyone out (including during "Reset demo data") · **High** for the demo

- **Found by:** a stray 401 in the browser console while capturing the walkthrough screenshots. A probe then hammered an authenticated endpoint during a demo reset: in a reset of about 1.2 s it answered 471 × 401 and 41 × 500, because the user table is briefly empty or missing while `data.sql` reloads.
- **Root cause:** the app treated *any* 401 as an expired session and signed the person out. One poll landing in that window logged out the desk console, the driver or the rider. The same happens on any short auth outage, such as a database failover.
- **Fix:** the API client re-sends a 401'd request once after 1.5 s and signs out only if the retry is also refused (`mobile/src/api/client.ts`). A 401 is returned before any work is done, so re-sending is safe even for actions.
- **Verified:** a browser test serves exactly one 401 on the live-board poll. Before the fix the desk ended on `/login`; after it, the board stays up. Two consecutive 401s (a genuinely dead session) still sign out.

### Smaller fixes from the UI walk

| Issue | Fix |
|---|---|
| Shuttle shown as "203 min away" before its first run of the day | API returns `firstRunAt`; the app says "First run 06:00 at Gate 2" |
| "Arriving at Gate 2 in now" | "Your cab is at or near Gate 2 · Any moment" |
| Breakdown pill shown twice on the board | Flag suppressed when the status already says it |
| Empty queue repeated "No pickups yet" in title and body | Notice text reworded |
| Document names read "puc certificate" and months "Sept" | Acronym-aware wording; English locale for dates |
| Public tracking page said "0 min to Gate 2" | "Arriving at Gate 2 any moment" |
| Blocked cab showed both "Doc expired" and "Doc expiry" on the board | Flag suppressed when the status already says it |
| Tracking page's automatic `/favicon.ico` request answered 401 | Path is public |

---

## 3. Friction points designed out up front

| Friction point | How it is handled |
|---|---|
| Double tap on "Confirm ride" | `Idempotency-Key` per attempt, backed by `uk_booking_client_req` (test: `idempotentBooking`) |
| Two drivers taking one cab, or one driver on two cabs | Service checks plus generated-column unique keys on active duties |
| Two routes grabbing one idle cab | Vehicle row lock, re-check under the lock, plus `uk_trip_open_vehicle` |
| Double billing | Allocation per booking is idempotent, plus `uk_alloc_booking` |
| Rounding drift in cost splits | Residue on the last share; shares always sum to the trip cost (unit tested) |
| Admin overwriting another admin's change | `version` in the request; stale → 409 `STALE_DATA` (tested) |
| Driver leaving a stop with a rider still waiting | `CANNOT_ARRIVE` until boarded or no-show (tested) |
| Stale GPS pings flushed after a network gap | Stored for history but ignored for live position, overspeed and geofence if older than 2 minutes or out of order |
| Contract drift between API and app | Single `types.ts` mirroring the DTOs; OpenAPI generated from code (`docs/openapi.json`); the web slice test pins JSON shapes (e.g. dates as ISO strings) |

---

## 4. Known limitations and next steps

Honest boundaries of this MVP:

| Area | Status | Next step |
|---|---|---|
| Notifications | In-app only (logged with delivery status) | SMS/WhatsApp/push gateways behind `NotificationService` |
| Driver app offline buffering | GPS pings and actions need a connection; the server already accepts late, out-of-order pings | Local queue in the app (deck slide 10, "offline stretch") |
| Maps | Schematic stop progress, plus one-tap navigation to Google Maps | Plant road network map with live position |
| Language | English only | Hindi strings (deck requirement) |
| Integrations | HRMS, SSO and SAP are simulated (seeded employees, password login, CSV export) | SSO/AD login, HRMS sync, SAP posting |
| Security hardening | JWT in `localStorage` on web; no refresh token; CORS `*` by default; demo reset endpoint enabled by default | Set `CORS_ORIGINS`, `JWT_SECRET`, `DEMO_RESET_ENABLED=false`; httpOnly cookie for web; refresh tokens |
| Schema migrations | `schema.sql` recreates the demo database on start (`DB_INIT_MODE=always`) | Flyway before any pilot data must be kept |
| Scale | Single MySQL; GPS pings in a MySQL table | ClickHouse for raw GPS and Redis for live positions, as the deck plans |
| Engine | Rules + score on configured leg times (deck Phase 1) | Learned ETAs, demand forecasting, route solver for scheduled batches (deck Phase 4) |
| Load testing | Not done | Gatling run at shift-change peak volumes before the pilot |
