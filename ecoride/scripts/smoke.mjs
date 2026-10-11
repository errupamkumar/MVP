#!/usr/bin/env node
/**
 * End-to-end check of the EcoRide API: the whole journey a rider, a driver and an admin make,
 * with the driver's GPS replayed along the real road route.
 *
 *   node scripts/smoke.mjs                 # against http://localhost:9080
 *   API=http://host:9080 node scripts/smoke.mjs
 *
 * Uses the demo accounts (rider 9876500001, driver 9876600001, admin admin@ecoride.in) and
 * creates a fresh rider and a fresh driver applicant. Ends with nothing in flight.
 */
const API = (process.env.API ?? 'http://localhost:9080').replace(/\/+$/, '');

let passed = 0;
let failed = 0;
const check = (label, ok, detail = '') => {
  if (ok) {
    passed++;
    console.log(`  ok   ${label}${detail ? ` (${detail})` : ''}`);
  } else {
    failed++;
    console.log(`  FAIL ${label}${detail ? ` (${detail})` : ''}`);
  }
};
const step = (title) => console.log(`\n${title}`);
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function call(method, path, token, body) {
  const res = await fetch(API + path, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await res.text();
  let json = null;
  try {
    json = text ? JSON.parse(text) : null;
  } catch {
    json = text;
  }
  return { status: res.status, body: json };
}
const get = (p, t) => call('GET', p, t);
const post = (p, t, b) => call('POST', p, t, b ?? {});

function decode(str) {
  const points = [];
  let index = 0, lat = 0, lng = 0;
  while (index < str.length) {
    let b, shift = 0, result = 0;
    do { b = str.charCodeAt(index++) - 63; result |= (b & 0x1f) << shift; shift += 5; } while (b >= 0x20);
    lat += result & 1 ? ~(result >> 1) : result >> 1;
    shift = 0; result = 0;
    do { b = str.charCodeAt(index++) - 63; result |= (b & 0x1f) << shift; shift += 5; } while (b >= 0x20);
    lng += result & 1 ? ~(result >> 1) : result >> 1;
    points.push([lat / 1e5, lng / 1e5]);
  }
  return points;
}
const hav = (a, b) => {
  const R = 6371000, toR = (x) => (x * Math.PI) / 180;
  const dLat = toR(b[0] - a[0]), dLng = toR(b[1] - a[1]);
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(toR(a[0])) * Math.cos(toR(b[0])) * Math.sin(dLng / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(h));
};
/** Points every `stepM` metres along a polyline (what a phone reports while driving). */
function resample(points, stepM) {
  const out = [points[0]];
  let carry = 0;
  for (let i = 1; i < points.length; i++) {
    const a = points[i - 1], b = points[i];
    const seg = hav(a, b);
    let d = stepM - carry;
    while (d < seg) {
      const t = d / seg;
      out.push([a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t]);
      d += stepM;
    }
    carry = seg - (d - stepM);
  }
  out.push(points[points.length - 1]);
  return out;
}
async function drive(token, points, every = 4) {
  for (let i = 0; i < points.length; i += every) {
    const [lat, lng] = points[i];
    await post('/api/driver/location', token, { lat, lng, speedKmh: 32, accuracyM: 8, simulated: true });
  }
  const [lat, lng] = points[points.length - 1];
  await post('/api/driver/location', token, { lat, lng, speedKmh: 0, accuracyM: 8, simulated: true });
}
async function waitFor(fn, label, timeoutMs = 20000) {
  const until = Date.now() + timeoutMs;
  while (Date.now() < until) {
    const value = await fn();
    if (value) return value;
    await sleep(500);
  }
  throw new Error(`Timed out waiting for ${label}`);
}

const PICKUP = { lat: 18.570719, lng: 73.774308, address: 'Balewadi High Street, Balewadi, Pune' };
const DROP = { lat: 18.552844, lng: 73.826644, address: 'Savitribai Phule Pune University, Ganeshkhind Road' };

async function main() {
  console.log(`EcoRide smoke test against ${API}`);

  step('Public and accounts');
  const config = await get('/api/public/config');
  check('public config lists ride products', config.status === 200 && config.body.products.length >= 5,
    config.body?.products?.map((p) => p.code).join(','));

  const phone = '7' + String(Date.now()).slice(-9);
  const signup = await post('/api/rider/auth/signup', null, { name: 'Smoke Rider', phone, password: 'Smoke@1234' });
  check('rider signs up and is signed in', signup.status === 201 && !!signup.body.token);
  check('new rider gets a welcome wallet credit', Number(signup.body.profile?.walletBalance) === 200,
    `₹${signup.body.profile?.walletBalance}`);
  const dup = await post('/api/rider/auth/signup', null, { name: 'Again', phone, password: 'Smoke@1234' });
  check('same phone cannot sign up twice', dup.status === 409, dup.body?.code);

  const riderLogin = await post('/api/rider/auth/login', null, { login: '9876500001', password: 'Rider@123' });
  check('demo rider signs in', riderLogin.status === 200, riderLogin.body?.profile?.name);
  const R = riderLogin.body.token;
  const badLogin = await post('/api/rider/auth/login', null, { login: '9876500001', password: 'wrong' });
  check('wrong password is refused', badLogin.status === 401, badLogin.body?.code);

  const driverLogin = await post('/api/driver/auth/login', null, { login: '9876600001', password: 'Driver@123' });
  check('demo driver signs in', driverLogin.status === 200, driverLogin.body?.profile?.name);
  const D = driverLogin.body.token;
  const adminLogin = await post('/api/admin/auth/login', null, { email: 'admin@ecoride.in', password: 'Admin@123' });
  check('admin signs in', adminLogin.status === 200, adminLogin.body?.admin?.name);
  const A = adminLogin.body.token;

  const crossRider = await get('/api/driver/state', R);
  check('a rider token cannot use the driver app API', crossRider.status === 403);
  const crossAdmin = await get('/api/admin/dashboard', D);
  check('a driver token cannot use the admin API', crossAdmin.status === 403);
  const riderLoginAsDriver = await post('/api/driver/auth/login', null, { login: '9876500001', password: 'Rider@123' });
  check('rider credentials do not open the driver app', riderLoginAsDriver.status === 401);

  step('Clean start');
  const current0 = await get('/api/rider/rides/current', R);
  if (current0.body?.ride && ['SEARCHING', 'ACCEPTED', 'ARRIVED'].includes(current0.body.ride.status)) {
    await post(`/api/rider/rides/${current0.body.ride.id}/cancel`, R, { reason: 'smoke reset' });
  }
  await post('/api/driver/offline', D);

  step('Driver goes online near the pickup');
  const online = await post('/api/driver/online', D, { lat: 18.5662, lng: 73.7801 });
  check('driver online', online.status === 200 && online.body.profile.availability === 'ONLINE', online.body?.profile?.availability);
  await post('/api/driver/location', D, { lat: 18.5662, lng: 73.7801, accuracyM: 10 });

  step('Rider: location, search, nearby cars, upfront fares');
  const rev = await get(`/api/geo/reverse?lat=${PICKUP.lat}&lng=${PICKUP.lng}`, R);
  check('reverse geocode names the pickup', rev.status === 200 && !!rev.body.name, `${rev.body?.name} · ${rev.body?.source}`);
  const search = await get(`/api/geo/search?q=university&lat=${PICKUP.lat}&lng=${PICKUP.lng}`, R);
  check('place search finds the drop', search.status === 200 && search.body.some((p) => /University/i.test(p.name)),
    search.body?.slice(0, 2).map((p) => p.name).join(' | '));
  const nearby = await get(`/api/rider/nearby?lat=${PICKUP.lat}&lng=${PICKUP.lng}`, R);
  check('nearby free cars are shown', nearby.status === 200 && nearby.body.cars.length > 0,
    `${nearby.body?.cars?.length} cars, MINI eta ${nearby.body?.etaByType?.MINI}s`);
  const quote = await post('/api/rider/quotes', R, { pickup: PICKUP, drop: DROP });
  check('quote has a road route', quote.status === 200 && quote.body.route.distanceM > 3000,
    `${(quote.body?.route?.distanceM / 1000).toFixed(2)} km, ${Math.round(quote.body?.route?.durationS / 60)} min, ${quote.body?.route?.provider}`);
  const mini = quote.body.options.find((o) => o.vehicleType === 'MINI');
  check('every product has an upfront fare', quote.body.options.every((o) => Number(o.fare.total) > 0),
    quote.body.options.map((o) => `${o.vehicleType} ₹${o.fare.total}`).join(', '));
  const f = mini.fare;
  const sum = [f.baseFare, f.distanceFare, f.timeFare, f.minimumFareTopUp, f.surgeFare, f.waitingFare, f.tax, f.rounding]
    .reduce((a, b) => a + Number(b), 0) - Number(f.discount);
  check('fare lines add up to the total', Math.abs(sum - Number(f.total)) < 0.011, `₹${sum.toFixed(2)} = ₹${f.total}`);
  const promoQuote = await post('/api/rider/quotes', R, { pickup: PICKUP, drop: DROP, promoCode: 'eco20' });
  const promoMini = promoQuote.body.options.find((o) => o.vehicleType === 'MINI');
  check('promo ECO20 lowers the fare', promoQuote.body.promo?.valid && Number(promoMini.fare.discount) > 0,
    `-₹${promoMini.fare.discount}`);
  const far = await post('/api/rider/quotes', R, { pickup: PICKUP, drop: { lat: 19.076, lng: 72.8777, address: 'Mumbai' } });
  check('drop outside the service area is refused', far.status === 422, far.body?.code);

  step('Rider books Mini, dispatch offers it to the nearest real driver');
  const booked = await post('/api/rider/rides', R, { quoteId: quote.body.quoteId, vehicleType: 'MINI', paymentMethod: 'UPI' });
  check('ride created, searching', booked.status === 201 && booked.body.status === 'SEARCHING', booked.body?.code);
  const rideId = booked.body.id;
  const again = await post('/api/rider/rides', R, { quoteId: quote.body.quoteId, vehicleType: 'MINI', paymentMethod: 'UPI' });
  check('a second ride while one is in flight is refused', again.status === 409, again.body?.code);
  const offerState = await waitFor(async () => {
    const s = await get('/api/driver/state', D);
    return s.body?.offer ? s.body : null;
  }, 'the offer');
  const offer = offerState.offer;
  check('driver receives the offer with a countdown', offer.rideId === rideId && offer.expiresInS > 0,
    `${offer.expiresInS}s left, pickup ${offer.pickupDistanceM} m away, earns ₹${offer.estEarning}`);
  const searching = await get(`/api/rider/rides/${rideId}`, R);
  check('rider sees the search progress', searching.body.search?.driversContacted >= 1,
    `${searching.body.search?.driversContacted} driver(s) contacted`);

  const accepted = await post(`/api/driver/offers/${offer.offerId}/accept`, D);
  check('driver accepts', accepted.status === 200 && accepted.body.status === 'ACCEPTED',
    `route to pickup ${accepted.body?.live?.toPickupM} m`);
  const riderView = await get(`/api/rider/rides/${rideId}`, R);
  check('rider sees driver, car and OTP', riderView.body.driver?.vehicle?.plate === 'MH12AB4521' && /^\d{4}$/.test(riderView.body.otp),
    `${riderView.body.driver?.name}, ${riderView.body.driver?.vehicle?.plate}, OTP ${riderView.body.otp}`);
  const before = riderView.body.live.toPickupM;

  step('Driver drives to the pickup (GPS replayed along the road route)');
  const toPickup = resample(decode(accepted.body.pickupPolyline).concat([[PICKUP.lat, PICKUP.lng]]), 25);
  const half = toPickup.slice(0, Math.floor(toPickup.length / 2));
  await drive(D, half);
  const mid = await get(`/api/rider/rides/${rideId}`, R);
  check('distance to pickup shrinks as the cab moves', mid.body.live.toPickupM < before,
    `${before} m -> ${mid.body.live.toPickupM} m, ETA ${mid.body.live.toPickupEtaS}s`);
  await post(`/api/rider/rides/${rideId}/location`, R, { lat: PICKUP.lat + 0.0004, lng: PICKUP.lng, accuracyM: 12 });
  const early = await post(`/api/driver/rides/${rideId}/arrived`, D);
  check('cannot mark arrived far from the pickup', early.status === 409 || toPickup.length < 10, early.body?.message);
  await drive(D, toPickup.slice(Math.floor(toPickup.length / 2)));
  const driverMid = await get(`/api/driver/rides/${rideId}`, D);
  check('driver sees the rider\'s shared position', driverMid.body.rider?.position != null,
    `rider ${driverMid.body.live?.riderToPickupM} m from pickup`);
  const arrived = await post(`/api/driver/rides/${rideId}/arrived`, D);
  check('driver arrives', arrived.status === 200 && arrived.body.status === 'ARRIVED', arrived.body?.message);
  const wrong = await post(`/api/driver/rides/${rideId}/start`, D, { otp: '0000' });
  check('wrong OTP is refused', wrong.status === 422, wrong.body?.code);
  const started = await post(`/api/driver/rides/${rideId}/start`, D, { otp: riderView.body.otp });
  check('trip starts with the rider\'s OTP', started.status === 200 && started.body.status === 'IN_PROGRESS');

  step('Trip: GPS distance measured along the way');
  const trip = resample(decode(quote.body.route.polyline).concat([[DROP.lat, DROP.lng]]), 30);
  await drive(D, trip.slice(0, Math.floor(trip.length / 2)), 2);
  const onTrip = await get(`/api/rider/rides/${rideId}`, R);
  check('remaining distance and GPS distance update live', onTrip.body.live.remainingM < quote.body.route.distanceM && onTrip.body.live.travelledM > 0,
    `${onTrip.body.live.travelledM} m driven, ${onTrip.body.live.remainingM} m to go, ETA ${onTrip.body.live.remainingEtaS}s`);
  await drive(D, trip.slice(Math.floor(trip.length / 2)), 2);
  const done = await post(`/api/driver/rides/${rideId}/complete`, D);
  check('trip completes', done.status === 200 && done.body.status === 'COMPLETED');
  const receipt = await get(`/api/rider/rides/${rideId}`, R);
  const fare = receipt.body.fare;
  const deviation = Math.abs(receipt.body.actualDistanceM - quote.body.route.distanceM) / quote.body.route.distanceM;
  check('GPS distance close to the planned route', deviation < 0.2,
    `GPS ${(receipt.body.actualDistanceM / 1000).toFixed(2)} km vs planned ${(quote.body.route.distanceM / 1000).toFixed(2)} km`);
  check('upfront fare stands, paid by UPI', fare.policy === 'UPFRONT' && fare.paymentStatus === 'PAID' && Number(fare.total) >= Number(mini.fare.total),
    `₹${fare.total} (${fare.policy}, ${fare.method} ${fare.paymentStatus})`);
  const rated = await post(`/api/rider/rides/${rideId}/rate`, R, { stars: 5, tags: ['Smooth ride'], tip: 20 });
  check('rider rates and tips', rated.status === 200 && rated.body.myRating === 5 && Number(rated.body.fare.tip) === 20);
  const ratedRider = await post(`/api/driver/rides/${rideId}/rate`, D, { stars: 5 });
  check('driver rates the rider', ratedRider.status === 200 && ratedRider.body.riderRating === 5);
  const earnings = await get('/api/driver/earnings', D);
  check('driver earnings include the fare share and tip', earnings.body.lines.some((l) => l.rideId === rideId && l.type === 'EARNING')
    && earnings.body.lines.some((l) => l.rideId === rideId && l.type === 'TIP'), `today ₹${earnings.body.today.net}`);

  step('Cancel while searching is free');
  const q2 = await post('/api/rider/quotes', R, { pickup: PICKUP, drop: DROP });
  const r2 = await post('/api/rider/rides', R, { quoteId: q2.body.quoteId, vehicleType: 'SEDAN', paymentMethod: 'CASH' });
  const c2 = await post(`/api/rider/rides/${r2.body.id}/cancel`, R, { reason: 'Changed my plans' });
  check('cancelled with no fee', c2.body.status === 'CANCELLED' && Number(c2.body.fare.cancellationFee) === 0);

  step('Decline moves the request to the next driver');
  await post('/api/driver/location', D, { lat: PICKUP.lat + 0.002, lng: PICKUP.lng, accuracyM: 8 });
  const q3 = await post('/api/rider/quotes', R, { pickup: PICKUP, drop: DROP });
  const r3 = await post('/api/rider/rides', R, { quoteId: q3.body.quoteId, vehicleType: 'MINI', paymentMethod: 'CASH' });
  const o3 = await waitFor(async () => (await get('/api/driver/state', D)).body?.offer, 'second offer');
  await post(`/api/driver/offers/${o3.offerId}/decline`, D);
  const taken = await waitFor(async () => {
    const v = await get(`/api/rider/rides/${r3.body.id}`, R);
    return v.body.status !== 'SEARCHING' ? v.body : null;
  }, 'another driver', 30000);
  check('another (simulated) driver accepts', taken.status === 'ACCEPTED' && taken.driver?.id !== driverLogin.body.profile.id,
    `${taken.driver?.name} ${taken.driver?.vehicle?.plate}`);
  const c3 = await post(`/api/rider/rides/${r3.body.id}/cancel`, R, { reason: 'smoke test done' });
  check('rider cancels within the free window', c3.body.status === 'CANCELLED' && Number(c3.body.fare.cancellationFee) === 0);

  step('Driver onboarding: pending until an admin approves');
  const plate = 'MH12ZZ' + String(Date.now()).slice(-4);
  const dPhone = '8' + String(Date.now()).slice(-9);
  const applicant = await post('/api/driver/auth/signup', null, {
    name: 'Smoke Driver', phone: dPhone, password: 'Smoke@1234', licenseNo: 'MH1220200012345', vehicleType: 'MINI',
    vehicleMake: 'Maruti Suzuki', vehicleModel: 'Swift', vehicleColor: 'Red', vehiclePlate: plate, vehicleYear: 2022,
  });
  check('driver applies', applicant.status === 201 && applicant.body.profile.status === 'PENDING_APPROVAL');
  const AD = applicant.body.token;
  const blocked = await post('/api/driver/online', AD, { lat: 18.56, lng: 73.78 });
  check('pending driver cannot go online', blocked.status === 409, blocked.body?.code);
  const approve = await post(`/api/admin/drivers/${applicant.body.profile.id}/approve`, A);
  check('admin approves', approve.status === 200 && approve.body.row.status === 'APPROVED');
  const nowOnline = await post('/api/driver/online', AD, { lat: 18.56, lng: 73.78 });
  check('approved driver goes online', nowOnline.status === 200 && nowOnline.body.profile.availability === 'ONLINE');
  await post('/api/driver/offline', AD);

  step('Admin console');
  const dash = await get('/api/admin/dashboard', A);
  check('dashboard KPIs', dash.status === 200 && dash.body.kpis.completedToday > 0 && dash.body.days.length === 14,
    `${dash.body.kpis.ridesToday} rides today, GMV ₹${dash.body.kpis.gmvToday}, ${dash.body.kpis.onlineDrivers} online`);
  const live = await get('/api/admin/live', A);
  check('live map shows drivers', live.status === 200 && live.body.drivers.length > 5, `${live.body.drivers.length} drivers, ${live.body.rides.length} rides`);
  const detail = await get(`/api/admin/rides/${rideId}`, A);
  check('ride detail has GPS trace, offers and timeline', detail.body.track.length > 10 && detail.body.offers.length >= 1 && detail.body.timeline.length >= 6,
    `${detail.body.track.length} GPS points, ${detail.body.timeline.length} events`);
  const finance = await get('/api/admin/finance?days=30', A);
  check('finance totals', finance.status === 200 && Number(finance.body.totals.gmv) > 0,
    `GMV ₹${finance.body.totals.gmv}, commission ₹${finance.body.totals.commission}`);
  const setting = await call('PUT', '/api/admin/settings/OFFER_TIMEOUT_S', A, { value: '20' });
  check('admin changes a setting', setting.status === 200 && setting.body.value === '20');
  await call('PUT', '/api/admin/settings/OFFER_TIMEOUT_S', A, { value: '15' });
  const badSetting = await call('PUT', '/api/admin/settings/OFFER_TIMEOUT_S', A, { value: '900' });
  check('out-of-range setting refused', badSetting.status === 422, badSetting.body?.message);
  const audit = await get('/api/admin/audit', A);
  check('audit log records admin actions', audit.body.some((e) => e.action === 'DRIVER_APPROVED'));

  await post('/api/driver/offline', D);
  console.log(`\n${passed} passed, ${failed} failed`);
  process.exit(failed ? 1 : 0);
}

main().catch((err) => {
  console.error(`\nFAIL ${err.message}`);
  process.exit(1);
});
