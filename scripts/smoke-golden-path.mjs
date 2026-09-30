#!/usr/bin/env node
/**
 * Plant Ride · golden-path smoke test (deck 2, slide 16: "one trip, three journeys, eight handovers").
 *
 * Drives the real API end to end:
 *   driver signs on -> waiting riders pooled onto C-12 -> rider books the last seat (3-rider cap)
 *   -> a 4th rider is refused that cab -> OTP boarding (a wrong code is counted) -> drops close the trip
 *   -> cost lands on CC-4471 -> tracking link expires -> rating -> sign-off -> billing export.
 *
 * Finishes by resetting the demo data, so it doubles as a pre-demo check.
 *
 *   node scripts/smoke-golden-path.mjs                 # against http://localhost:8080
 *   API_URL=http://10.0.0.5:8080 node scripts/smoke-golden-path.mjs
 *   KEEP_DATA=1 node scripts/smoke-golden-path.mjs     # skip the final reset
 *
 * Needs Node 18+ (global fetch). Exit code 0 = every check passed.
 */
import { randomUUID } from 'node:crypto';

const API = (process.env.API_URL ?? 'http://localhost:8080').replace(/\/+$/, '');
const PASSWORD = process.env.DEMO_PASSWORD ?? 'Plant@123';
let failures = 0;
let step = 0;

const green = (s) => `\x1b[32m${s}\x1b[0m`;
const red = (s) => `\x1b[31m${s}\x1b[0m`;
const dim = (s) => `\x1b[2m${s}\x1b[0m`;

function check(condition, label, detail) {
  if (condition) {
    console.log(`  ${green('✔')} ${label}`);
  } else {
    failures++;
    console.log(`  ${red('✘')} ${label}${detail ? dim('  ' + JSON.stringify(detail).slice(0, 400)) : ''}`);
  }
}

function section(title) {
  step++;
  console.log(`\n${step}. ${title}`);
}

async function call(method, path, { token, body, headers = {}, raw = false } = {}) {
  const res = await fetch(API + path, {
    method,
    headers: {
      Accept: 'application/json',
      ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...headers,
    },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  let data = text;
  if (!raw) {
    try {
      data = text ? JSON.parse(text) : null;
    } catch {
      data = text;
    }
  }
  return { status: res.status, data };
}

async function login(username) {
  const { status, data } = await call('POST', '/api/auth/login', { body: { username, password: PASSWORD } });
  if (status !== 200) throw new Error(`Login failed for ${username}: ${status} ${JSON.stringify(data)}`);
  return data.token;
}

async function main() {
  console.log(`Plant Ride golden-path smoke test against ${API}`);

  section('Health and sign-in');
  const health = await call('GET', '/actuator/health');
  check(health.status === 200 && health.data?.status === 'UP', 'API is up', health.data);
  const [driver, rider, admin, secondRider] = await Promise.all([
    login('DRV-2281'), login('LHS-40218'), login('admin'), login('LHS-44190'),
  ]);
  check(true, 'Signed in as S. Kumar (driver), A. Raghavan (rider), R. Kapoor (admin), N. Gupta (rider)');

  section('Desk sees two riders waiting at Gate 2 and no cab for them');
  let board = (await call('GET', '/api/desk/board', { token: admin })).data;
  const waiting = board.queue.filter((q) => q.status === 'REQUESTED' && q.fromStopName === 'Gate 2');
  check(waiting.length === 2, 'M. Iyer and S. Banerjee are waiting at Gate 2', board.queue);
  const c12Before = board.vehicles.find((v) => v.code === 'C-12');
  check(c12Before?.status === 'OFF_DUTY', 'C-12 is off duty', c12Before);

  section('Driver signs on with C-12 (vehicle check + odometer)');
  const vehicles = (await call('GET', '/api/driver/vehicles', { token: driver })).data;
  const c12 = vehicles.find((v) => v.code === 'C-12');
  check(c12?.available === true, 'C-12 is offered and available', c12);
  const blockedCheck = await call('POST', '/api/driver/duty/start', {
    token: driver, body: { vehicleId: c12.id, startOdometer: c12.odometerKm, checklist: ['TYRES', 'LIGHTS'] },
  });
  check(blockedCheck.status === 422 && blockedCheck.data.code === 'CHECKLIST_INCOMPLETE',
    'An incomplete vehicle check is refused', blockedCheck.data);
  const duty = await call('POST', '/api/driver/duty/start', {
    token: driver,
    body: { vehicleId: c12.id, startOdometer: c12.odometerKm, checklist: ['TYRES', 'LIGHTS', 'BELTS', 'FUEL', 'FIRST_AID'] },
  });
  check(duty.status === 200 && duty.data.status === 'ACTIVE', 'Duty started', duty.data);

  section('Engine pools the waiting riders onto C-12');
  let queue = (await call('GET', '/api/driver/queue', { token: driver })).data;
  const trip = queue.trip;
  check(trip && trip.route.code === 'R1', 'C-12 now has a Route 1 trip', queue);
  const gate2 = trip?.stops.find((s) => s.stopName === 'Gate 2');
  check(gate2?.pickups.length === 2, 'Gate 2: pick up 2 riders', gate2);

  section('Rider books Gate 2 → Blast Furnace and gets the last seat');
  const stops = (await call('GET', '/api/stops', { token: rider })).data;
  const stopId = (name) => stops.find((s) => s.name === name).id;
  const options = (await call('POST', '/api/rider/ride-options', {
    token: rider, body: { fromStopId: stopId('Gate 2'), toStopId: stopId('Blast Furnace'), seats: 1 },
  })).data;
  const shared = options.options.find((o) => o.type === 'SHARED');
  check(shared?.vehicleCode === 'C-12' && shared.seatsTaken === 2 && shared.capacity === 3,
    'Shared option: C-12, 2 of 3 seats taken', shared);
  check(options.options.some((o) => o.type === 'SHUTTLE'), 'Shuttle S-03 is offered as timetable information');
  check(options.options.find((o) => o.type === 'EXCLUSIVE')?.requiresApproval === true, 'Exclusive needs approval');

  const profile = (await call('GET', '/api/rider/profile', { token: rider })).data;
  const key = randomUUID();
  const bookingBody = {
    fromStopId: stopId('Gate 2'), toStopId: stopId('Blast Furnace'), rideType: 'SHARED', seats: 1,
    riderName: profile.fullName, riderPhone: profile.phone, riderEmail: profile.email, forGuest: false,
  };
  const booked = await call('POST', '/api/rider/bookings', { token: rider, body: bookingBody, headers: { 'Idempotency-Key': key } });
  const booking = booked.data;
  check(booked.status === 201 && booking.status === 'ASSIGNED' && booking.vehicle?.code === 'C-12',
    'Booking confirmed on C-12 with vehicle number and driver contact', booking);
  check(/^\d{4}$/.test(booking.otp ?? ''), `Boarding OTP issued (${booking.otp})`);
  check(booking.progress?.seatsTaken === 3 && booking.progress?.capacity === 3, 'C-12 is now at its 3-rider cap', booking.progress);
  const retried = await call('POST', '/api/rider/bookings', { token: rider, body: bookingBody, headers: { 'Idempotency-Key': key } });
  check(retried.data.id === booking.id, 'A retried "Confirm ride" (same Idempotency-Key) returns the same booking', retried.data);

  section('A fourth rider is never offered the full cab');
  const fourth = await call('POST', '/api/rider/bookings', {
    token: secondRider,
    body: { ...bookingBody, toStopId: stopId('Admin Block'), riderName: 'N. Gupta', riderPhone: '+91 98456 44190', riderEmail: null },
    headers: { 'Idempotency-Key': randomUUID() },
  });
  check(fourth.status === 201 && fourth.data.status === 'REQUESTED' && !fourth.data.vehicle,
    'N. Gupta waits in the queue instead of joining C-12', fourth.data);
  check(/C-12 is at its 3-rider cap/.test(fourth.data.matchNote ?? ''), 'The engine records why C-12 was skipped', fourth.data.matchNote);
  const cancelled = await call('POST', `/api/rider/bookings/${fourth.data.id}/cancel`, { token: secondRider, body: { reason: 'Smoke test' } });
  check(cancelled.data.status === 'CANCELLED', 'N. Gupta cancels (keeps the queue clean)', cancelled.data);

  section('Public tracking link (no login, no personal data)');
  const track = await call('GET', `/api/public/track/${booking.trackingToken}`);
  check(track.status === 200 && track.data.vehicleCode === 'C-12', `Tracking works: ${track.data.stageLabel}`, track.data);
  check(!JSON.stringify(track.data).includes('Raghavan') && !JSON.stringify(track.data).includes('+91'),
    'Tracking view contains no names or phone numbers');
  const page = await call('GET', `/t/${booking.trackingToken}`, { raw: true });
  check(page.status === 200 && String(page.data).includes('Plant Ride'), 'Shareable page /t/<token> is served');

  section('Driver arrives at Gate 2 and boards riders by OTP');
  const tripId = trip.tripId;
  let res = await call('POST', `/api/driver/trips/${tripId}/arrive`, { token: driver, body: { stopSeq: 1 } });
  check(res.status === 200 && res.data.trip.currentStopName === 'Gate 2', 'Arrived at Gate 2', res.data);
  const riderView = (await call('GET', `/api/rider/bookings/${booking.id}`, { token: rider })).data;
  check(riderView.progress.stage === 'AT_PICKUP', 'Rider app: "Your cab has arrived"', riderView.progress);

  const pickups = res.data.trip.stops.find((s) => s.seq === 1).pickups;
  const byName = (n) => pickups.find((p) => p.riderName === n);
  const wrong = await call('POST', `/api/driver/trips/${tripId}/board`, {
    token: driver, body: { bookingId: byName('A. Raghavan').bookingId, otp: booking.otp === '0000' ? '1111' : '0000' },
  });
  check(wrong.status === 422 && wrong.data.code === 'OTP_INVALID' && /4 tries left/.test(wrong.data.message),
    'A wrong OTP is refused and counted (4 tries left)', wrong.data);
  const early = await call('POST', `/api/driver/trips/${tripId}/arrive`, { token: driver, body: { stopSeq: 3 } });
  check(early.status === 409 && early.data.code === 'CANNOT_ARRIVE', 'Cannot leave Gate 2 with riders still waiting', early.data);

  for (const [name, otp] of [['A. Raghavan', booking.otp], ['M. Iyer', '9317'], ['S. Banerjee', '5540']]) {
    res = await call('POST', `/api/driver/trips/${tripId}/board`, { token: driver, body: { bookingId: byName(name).bookingId, otp } });
    check(res.status === 200, `${name} boarded`, res.data);
  }
  check(res.data.trip.status === 'IN_PROGRESS' && res.data.trip.onBoard === 3, 'Trip in progress, 3 on board', res.data.trip);

  section('Drops close the trip (geofence-equivalent arrival)');
  res = await call('POST', `/api/driver/trips/${tripId}/arrive`, { token: driver, body: { stopSeq: 3 } });
  check(res.status === 200 && res.data.trip.onBoard === 2, 'Coke Plant: M. Iyer dropped, 2 still on board', res.data);
  res = await call('POST', `/api/driver/trips/${tripId}/arrive`, { token: driver, body: { stopSeq: 4 } });
  check(res.status === 200 && res.data.trip === null, 'Blast Furnace: last drop, trip closed', res.data);

  const done = (await call('GET', `/api/rider/bookings/${booking.id}`, { token: rider })).data;
  check(done.status === 'COMPLETED' && Number(done.distanceKm) === 3.4, 'Rider: trip complete, 3.4 km', done);
  check(Number(done.costAmount) > 0 && done.costCentreCode === 'CC-4471',
    `Charged ₹${done.costAmount} to ${done.costCentreCode} (${done.costCentreName})`, done);
  const expired = await call('GET', `/api/public/track/${booking.trackingToken}`);
  check(expired.status === 410, 'Tracking link expired when the trip closed', expired.data);
  const rated = await call('POST', `/api/rider/bookings/${booking.id}/rating`, {
    token: rider, body: { rating: 5, tags: ['ON_TIME', 'CLEAN_CAB'], note: 'Smooth ride' },
  });
  check(rated.status === 200 && rated.data.rating === 5, 'Rider rated the trip 5★', rated.data);

  section('Driver signs off; desk and finance see the result');
  const signOff = await call('POST', '/api/driver/duty/end', {
    token: driver, body: { endOdometer: c12.odometerKm + 6, fuelLitres: 2.5 },
  });
  check(signOff.status === 200 && signOff.data.tripsCompleted === 1 && signOff.data.ridersCarried === 3,
    `Signed off: 1 trip, 3 riders, ${signOff.data.gpsKm} GPS km, odometer ${signOff.data.odometerCheck}`, signOff.data);
  const billing = (await call('GET', '/api/billing/summary', { token: admin })).data;
  const ops = billing.costCentres.find((c) => c.code === 'CC-4471');
  check(ops && Number(ops.amount) > 0, `CC-4471 Operations: ₹${ops?.amount} this month (${ops?.budgetUsedPct}% of budget)`, ops);
  check(billing.vendors.some((v) => v.status === 'DISPUTE'), 'Vendor B claim flagged against GPS kilometres', billing.vendors);
  const csv = await call('GET', '/api/billing/export', { token: admin, raw: true });
  check(csv.status === 200 && String(csv.data).includes(booking.bookingCode), 'ERP export contains the ride', String(csv.data).slice(0, 200));
  const audit = (await call('GET', '/api/admin/audit?limit=20', { token: admin })).data;
  check(audit.some((a) => a.action === 'TRIP_COMPLETED'), 'Audit trail records the completed trip');

  if (!process.env.KEEP_DATA) {
    section('Reset demo data');
    const reset = await call('POST', '/api/admin/demo/reset', { token: admin });
    check(reset.status === 200, reset.data?.message ?? 'Demo data reset', reset.data);
  }

  console.log(failures === 0 ? green(`\nAll checks passed.`) : red(`\n${failures} check(s) failed.`));
  process.exit(failures === 0 ? 0 : 1);
}

main().catch((err) => {
  console.error(red(`\nSmoke test aborted: ${err.message}`));
  process.exit(2);
});
