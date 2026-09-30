# Client demo script · 5 minutes

**Story:** one booking followed across the rider, the driver and the transport desk (deck 2, slide 16). *"One booking, eight handovers, and nobody had to phone anybody."*

---

## Before the demo (T–15 min)

| ✓ | Step | Command / action |
|---|---|---|
| ☐ | MySQL is running (local `root/root` on 3306, or `docker compose up -d`) | |
| ☐ | API running | `cd backend && mvn spring-boot:run` → wait for `Started PlantRideApplication` |
| ☐ | **Pre-flight check.** Runs the whole golden path, then resets the data to the starting state | `node scripts/smoke-golden-path.mjs` → `All checks passed.` |
| ☐ | App running | `cd mobile && npx expo start`, then press `w` for the web console; phones scan the QR code in Expo Go |
| ☐ | Sign in on three screens (password `Plant@123` for all) | **Desk** (laptop, browser): `admin` · **Driver** (phone 1): `DRV-2281` · **Rider** (phone 2): `LHS-40218` |
| ☐ | Park each screen | Desk on **Live board** · Driver on **Start-of-duty check** · Rider on **Home** |
| ☐ | Keep these codes to hand | M. Iyer's OTP **9317** · S. Banerjee's OTP **5540** (the rider's own OTP appears on their phone) |

> Waiting requests expire after 90 minutes. If more than an hour passes after the pre-flight check, reset again: **Routes & rules → Reset demo data** (or rerun the smoke script).

**No phones?** Open two extra browser windows at phone width (Chrome DevTools → device toolbar → 390 × 844) on `http://localhost:8081`. The app behaves the same.

---

## The five minutes

### 0:00 · The problem, on one screen: **Desk**
**Show:** Live board.

> "This is the transport desk's live board: the whole fleet on one screen, refreshed every five seconds. Three of nine vehicles are in service. Seat occupancy, average wait and on-time pickups are measured, not assumed."

**Point at *Needs attention*:** *C-09 insurance expired · blocked from dispatch*, *K. Joshi gate pass expired · blocked from duty*, *C-07 overspeed*.

> "Compliance isn't a report someone reads on Friday. An expired document blocks dispatch outright."

**Open *Queue*:** two riders are waiting at Gate 2. Point at the grey note under M. Iyer.

> "And the engine explains itself. No cab is free: C-21's driver is 8 hours 52 into a 9-hour shift, C-07 is on Route 2, C-44 is off-road. Every decision is auditable."

### 0:45 · The driver signs on: **Driver phone**
**Show:** Start-of-duty check. Documents are green; C-12 is pre-selected and shows *PUC certificate expires in 6 days* in amber.

**Do:** tick the five checks → leave the odometer as filled → **Sign on for duty**.

> "Big targets, usable with gloves on. The check blocks the queue until it's done."

**Result:** the queue appears straight away: *Route 1 · Gate 2: pick up 2 riders · Coke Plant · Blast Furnace*.

> "The two riders who were waiting were matched the moment he signed on. No call to the desk."

*(Desk: the queue is empty and C-12 shows **To pickup**.)*

### 1:30 · The rider books in under 30 seconds: **Rider phone**
**Do:** **Where to?** → From **Gate 2** → To **Blast Furnace** → *Now*.

> "Name, P. No., email and phone come from HR, and stay editable because HR records go stale."

**Do:** **Show ride options**.

> "Three ways to ride. The shuttle is information only, you just walk up. The shared cab C-12 has 2 of 3 seats taken. Exclusive needs approval. And the occupancy rule is right there: pick-up runs carry three riders, maximum."

**Do:** **Confirm ride**.

**Result:** vehicle **MH 12 AB 4412 · C-12**, driver **S. Kumar** with a Call button, a 4-digit **boarding OTP**, and a **live tracking link**.

> "Vehicle number and driver contact are the two things people phone the desk for today. That call is gone."

*(Optional, 10 s: tap the tracking link and show the public page. It shows the vehicle, never the people, and expires when the trip ends.)*

### 2:30 · OTP boarding: **Driver phone**
**Do:** **Arrived at Gate 2**.

*(Rider phone flips to **Your cab has arrived**, with big OTP digits and "3 riders on this pick-up · seat 4 held free by policy".)*

**Do:** type the rider's OTP → **Board**. Then **9317** for M. Iyer and **5540** for S. Banerjee.

> "The code proves the right rider is in the right cab, which is what makes shared rides auditable. Five wrong codes and boarding locks."

*(Optional: type a wrong code first → "That code does not match… 4 tries left".)*

**Result:** *On board 3 of 3 · Seats left 0*. Rider: **Trip in progress**, drop in ~16 min, sharing with 2 colleagues. Desk: C-12 **On trip · 3 of 3**.

### 3:30 · Drop and automatic close: **Driver, then Rider**
**Do (driver):** **Arrived at Coke Plant** (M. Iyer is dropped) → **Arrived at Blast Furnace**.

> "In the plant, a GPS geofence at the stop does this automatically. Here the driver taps."

**Result:** driver: *No pickups yet*. Rider: **Trip complete · Gate 2 to Blast Furnace · 3.4 km · Shared · Charged to CC-4471 Operations · ₹22.86**.

**Do (rider):** 5 stars, *On time*, **Submit rating**.

> "Ratings feed the vendor SLA report. The trip cost ₹61.20 on measured distance, split by kilometres ridden across three cost centres, to the paisa."

### 4:15 · The money: **Desk → Billing**
**Show:** charges by cost centre with budget bars (one is over budget), then **Vendor bill vs GPS**.

> "Vendor B billed 21 kilometres more than GPS recorded: 6.5 percent. That cross-check pays for itself in the first month. GPS kilometres are the contractual source."

**Do:** **Export to SAP (CSV)** → the file downloads, one line per ride.

### 4:45 · Configurable, not coded: **Desk → Routes & rules** (close)
**Show:** Route 1's stop sequence and *Max passengers per pick-up: 3*.

> "The three-rider cap, the wait, the detour and the speed limit are settings per route. LHS changes them here, it's audited, and no software release is needed."

**Close:**
> "One booking, eight handovers, three riders on one vehicle inside the cap, every rupee charged to the right cost centre on measured distance, one audit trail. And nobody had to phone anybody."

---

## If they ask… (Q&A extras)

| Question | Show |
|---|---|
| "What if the cab is full?" | Rider web window, sign in as **`LHS-44190`** (N. Gupta), book Gate 2 → Admin Block while C-12 has 3 on board. They get *"Every cab is busy or full"* and wait in the queue. Desk **Queue → Assign a cab**: C-12 is refused with *"at its 3-rider cap"*. |
| "Can you change the cap?" | **Routes & rules** → Max passengers 3 → 4 → **Save rules**. N. Gupta is matched instantly. Set it back afterwards. |
| "What stops an unfit vehicle or driver?" | Sign in as **`DRV-2310`**: *Duty is blocked, gate pass expired*. Or **`DRV-1466`** with C-09: *insurance expired, dispatch is blocked*. |
| "What about no-shows?" | Driver screen: the *No-show in 4:59* countdown. The desk can see the 3-strikes rule: sign in as **`LHS-52287`** → *Booking is paused*. |
| "Breakdown mid-trip?" | Driver **Issues → Breakdown → Send**. C-12 goes off-road, the riders go back in the queue from the last stop reached, and the desk gets an alert. *(Do this last, or reset afterwards.)* |
| "Exclusive rides?" | Desk **Queue**: P. Sharma's exclusive request → **Approve**. |
| "Where's the API?" | `http://localhost:8080/swagger-ui.html` |

## If something goes wrong

| Symptom | Fix |
|---|---|
| Driver queue stays empty after sign-on | Pull down to refresh. If the Gate 2 riders are gone (expired or used in a rehearsal): **Routes & rules → Reset demo data**, then sign on again. |
| Rider sees a different cab or "every cab is busy" | C-12 isn't on duty, or is already full from a rehearsal. Reset demo data. |
| Phone shows "Cannot reach Plant Ride at http://…" | The phone must be on the laptop's Wi-Fi; allow Java through the Windows firewall on port 8080; or set `EXPO_PUBLIC_API_URL=http://<laptop-ip>:8080` and restart Expo. The server address is printed at the bottom of the login screen. |
| "Your session has expired" | Sign in again (demo tokens last 12 hours). |
| Anything else | Reset demo data, sign the driver on, and restart from 0:45. The whole path takes under 3 minutes. |
