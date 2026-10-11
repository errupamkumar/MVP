package com.srmecotech.ecoride.demo;

import com.srmecotech.ecoride.common.config.AppProperties;
import com.srmecotech.ecoride.common.settings.Setting;
import com.srmecotech.ecoride.common.settings.SettingsService;
import com.srmecotech.ecoride.driver.Availability;
import com.srmecotech.ecoride.driver.Driver;
import com.srmecotech.ecoride.driver.DriverLocator;
import com.srmecotech.ecoride.driver.DriverRepository;
import com.srmecotech.ecoride.driver.DriverStatus;
import com.srmecotech.ecoride.geo.GeoMath;
import com.srmecotech.ecoride.geo.GeoPoint;
import com.srmecotech.ecoride.geo.LocationInput;
import com.srmecotech.ecoride.geo.RouteResult;
import com.srmecotech.ecoride.geo.RouteTracker;
import com.srmecotech.ecoride.geo.RoutingService;
import com.srmecotech.ecoride.pricing.QuoteService;
import com.srmecotech.ecoride.ride.LocationService;
import com.srmecotech.ecoride.ride.PaymentMethod;
import com.srmecotech.ecoride.ride.PaymentStatus;
import com.srmecotech.ecoride.ride.Ride;
import com.srmecotech.ecoride.ride.RideLifecycleService;
import com.srmecotech.ecoride.ride.RideOffer;
import com.srmecotech.ecoride.ride.RideOfferRepository;
import com.srmecotech.ecoride.ride.RideRepository;
import com.srmecotech.ecoride.ride.RideRequestService;
import com.srmecotech.ecoride.ride.RideStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The simulated part of the demo world, driven through the same services the apps use:
 * <ul>
 *   <li>Simulated drivers cruise around their neighbourhood on real road routes while free,
 *       accept offers after a few seconds, drive to the pickup, start (the rider's code),
 *       drive the trip at DEMO_SIM_SPEED_X real time, complete and rate. Their GPS fixes go
 *       through {@link LocationService} exactly like a phone's.</li>
 *   <li>Simulated riders request a ride every DEMO_DEMAND_INTERVAL_S between real Pune places;
 *       those rides are served by simulated drivers only.</li>
 * </ul>
 * A real rider's request reaches a simulated driver only when no real driver in range takes it
 * (PREFER_REAL_DRIVERS); the simulated driver then serves it like any other ride.
 */
@Component
public class DemoFleetSimulator {

    private static final Logger log = LoggerFactory.getLogger(DemoFleetSimulator.class);
    private static final double CRUISE_SPEED_KMH = 24;
    private static final double TRIP_SPEED_KMH = 30;
    private static final long ACCEPT_AFTER_MS = 3500;
    private static final long START_AFTER_MS = 7000;

    private final AppProperties properties;
    private final SettingsService settings;
    private final DriverRepository drivers;
    private final RideRepository rides;
    private final RideOfferRepository offers;
    private final RideLifecycleService lifecycle;
    private final RideRequestService requests;
    private final LocationService locations;
    private final RoutingService routing;
    private final QuoteService quotes;
    private final DriverLocator locator;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final Random random = new Random();
    private final Map<Long, Plan> plans = new ConcurrentHashMap<>();
    private final Map<Long, GeoPoint> homes = new ConcurrentHashMap<>();
    private volatile boolean paused;
    private volatile long nextDemandAt;

    public DemoFleetSimulator(AppProperties properties, SettingsService settings, DriverRepository drivers,
                              RideRepository rides, RideOfferRepository offers, RideLifecycleService lifecycle,
                              RideRequestService requests, LocationService locations, RoutingService routing,
                              QuoteService quotes, DriverLocator locator, JdbcTemplate jdbc, Clock clock) {
        this.properties = properties;
        this.settings = settings;
        this.drivers = drivers;
        this.rides = rides;
        this.offers = offers;
        this.lifecycle = lifecycle;
        this.requests = requests;
        this.locations = locations;
        this.routing = routing;
        this.quotes = quotes;
        this.locator = locator;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public void pause(boolean pause) {
        this.paused = pause;
    }

    /** Drops in-memory plans (after a demo reset the drivers have new rows). */
    public void forget() {
        plans.clear();
        homes.clear();
        nextDemandAt = 0;
    }

    @Scheduled(fixedDelayString = "${ecoride.demo.simulation-interval-ms:2000}", initialDelay = 5000)
    public void tick() {
        if (paused || !properties.demo().simulationEnabled() || !settings.getBool(Setting.DEMO_FLEET_ENABLED)) {
            return;
        }
        long nowMs = System.currentTimeMillis();
        for (Driver bot : drivers.findBySimulatedTrueAndStatus(DriverStatus.APPROVED)) {
            if (paused) {
                return;
            }
            try {
                step(bot, nowMs);
            } catch (RuntimeException ex) {
                plans.remove(bot.getId());
                log.debug("Simulated driver {} skipped a step: {}", bot.getName(), ex.getMessage());
            }
        }
        if (settings.getBool(Setting.DEMO_DEMAND_ENABLED) && nowMs >= nextDemandAt) {
            int interval = settings.getInt(Setting.DEMO_DEMAND_INTERVAL_S);
            nextDemandAt = nowMs + (long) (interval * (0.6 + random.nextDouble() * 0.8) * 1000);
            try {
                requestRide();
            } catch (RuntimeException ex) {
                log.debug("Simulated request skipped: {}", ex.getMessage());
            }
        }
    }

    private void step(Driver bot, long nowMs) {
        if (bot.getLat() == null) {
            return;
        }
        GeoPoint here = new GeoPoint(bot.getLat(), bot.getLng());
        homes.putIfAbsent(bot.getId(), here);
        if (bot.getAvailability() == Availability.OFFLINE) {
            lifecycle.goOnline(bot.getId(), here);
            return;
        }
        RideOffer offer = offers.findFirstByDriverIdAndStatusOrderByIdAsc(bot.getId(), RideOffer.OfferStatus.PENDING).orElse(null);
        if (offer != null && bot.getAvailability() == Availability.ONLINE) {
            if (Duration.between(offer.getOfferedAt(), clock.instant()).toMillis() >= ACCEPT_AFTER_MS) {
                Ride ride = rides.findById(offer.getRideId()).orElseThrow();
                RouteResult toPickup = routing.route(here, ride.pickup());
                lifecycle.accept(bot.getId(), offer.getId(), toPickup);
                plans.remove(bot.getId());
            }
            return;
        }
        Ride ride = rides.findFirstByDriverIdAndStatusInOrderByIdDesc(bot.getId(), RideStatus.ASSIGNED).orElse(null);
        if (ride != null) {
            drive(bot, ride, here, nowMs);
            return;
        }
        cruise(bot, here, nowMs);
    }

    private void drive(Driver bot, Ride ride, GeoPoint here, long nowMs) {
        double speedKmh = TRIP_SPEED_KMH * settings.getInt(Setting.DEMO_SIM_SPEED_X);
        switch (ride.getStatus()) {
            case ACCEPTED -> {
                Plan plan = plan(bot.getId(), "PICKUP", ride.getId(), ride.getPickupPolyline(), here, ride.pickup(), speedKmh, nowMs);
                if (advance(bot, plan, nowMs)) {
                    lifecycle.arrived(bot.getId(), ride.getId());
                    plans.remove(bot.getId());
                }
            }
            case ARRIVED -> {
                long waited = Duration.between(ride.getArrivedAt(), clock.instant()).toMillis();
                if (waited >= START_AFTER_MS) {
                    lifecycle.start(bot.getId(), ride.getId(), ride.getOtp());
                }
            }
            case IN_PROGRESS -> {
                Plan plan = plan(bot.getId(), "TRIP", ride.getId(), ride.getRoutePolyline(), here, ride.drop(), speedKmh, nowMs);
                if (advance(bot, plan, nowMs)) {
                    Ride done = lifecycle.complete(bot.getId(), ride.getId());
                    plans.remove(bot.getId());
                    if (done.getPaymentMethod() == PaymentMethod.CASH && done.getPaymentStatus() == PaymentStatus.PENDING) {
                        lifecycle.cashCollected(bot.getId(), done.getId());
                    }
                    lifecycle.rateRider(bot.getId(), done.getId(), random.nextDouble() < 0.85 ? 5 : 4);
                    if (done.isSimulated()) {
                        int stars = random.nextDouble() < 0.75 ? 5 : 4;
                        BigDecimal tip = random.nextDouble() < 0.15 ? BigDecimal.valueOf(20) : BigDecimal.ZERO;
                        requests.rate(done.getRiderId(), done.getId(), stars,
                                stars == 5 ? List.of("Smooth ride") : List.of(), null, tip);
                    }
                }
            }
            default -> {
            }
        }
    }

    private void cruise(Driver bot, GeoPoint here, long nowMs) {
        Plan plan = plans.get(bot.getId());
        if (plan != null && !"CRUISE".equals(plan.purpose())) {
            plans.remove(bot.getId());
            plan = null;
        }
        if (plan == null || (plan.finished(nowMs) && nowMs >= plan.restUntilMs())) {
            GeoPoint home = homes.getOrDefault(bot.getId(), here);
            double bearing = random.nextDouble() * 360;
            GeoPoint target = GeoMath.offset(home, 400 + random.nextDouble() * 1800, bearing);
            RouteResult route = routing.route(here, target);
            if (route.distanceM() > 6000) {
                return;
            }
            plan = new Plan("CRUISE", null, RouteTracker.of(route.polyline()), nowMs, CRUISE_SPEED_KMH / 3.6,
                    0L);
            long travelMs = (long) (plan.tracker().totalM() / plan.speedMs() * 1000);
            plan = plan.withRest(nowMs + travelMs + 15_000 + random.nextInt(45_000));
            plans.put(bot.getId(), plan);
        }
        if (!plan.finished(nowMs)) {
            advance(bot, plan, nowMs);
        } else if (bot.getLocatedAt() == null
                || Duration.between(bot.getLocatedAt(), clock.instant()).toSeconds() > 20) {
            // Parked: keep the fix fresh so the driver stays dispatchable.
            locations.driverPing(bot.getId(), new GeoPoint(bot.getLat(), bot.getLng()), bot.getHeading(), 0.0, 8, true);
        }
    }

    private Plan plan(long botId, String purpose, long rideId, String polyline, GeoPoint here, GeoPoint target,
                      double speedKmh, long nowMs) {
        Plan plan = plans.get(botId);
        if (plan != null && purpose.equals(plan.purpose()) && plan.rideId() != null && plan.rideId() == rideId) {
            return plan;
        }
        String encoded = polyline != null && !polyline.isEmpty() ? polyline : routing.route(here, target).polyline();
        // Road routes end on the nearest road; the last few metres go to the exact pickup or drop.
        List<GeoPoint> points = new java.util.ArrayList<>(com.srmecotech.ecoride.geo.Polyline.decode(encoded));
        points.add(target);
        RouteTracker tracker = new RouteTracker(points);
        // Start from where the cab is along the route (it may already be part-way).
        double along = tracker.locate(here).alongM();
        long startedMs = nowMs - (long) (along / (speedKmh / 3.6) * 1000);
        plan = new Plan(purpose, rideId, tracker, startedMs, speedKmh / 3.6, 0L);
        plans.put(botId, plan);
        return plan;
    }

    /** Moves the cab along its plan and sends the GPS fix. @return true when it reached the end */
    private boolean advance(Driver bot, Plan plan, long nowMs) {
        double along = Math.min(plan.tracker().totalM(), (nowMs - plan.startedMs()) / 1000d * plan.speedMs());
        GeoPoint at = plan.tracker().pointAt(along);
        int heading = plan.tracker().headingAt(along);
        boolean arrived = plan.tracker().totalM() - along < 15;
        locations.driverPing(bot.getId(), at, heading, arrived ? 0.0 : Math.round(plan.speedMs() * 3.6 * 10) / 10d, 8, true);
        return arrived;
    }

    /** A simulated rider books a ride near a free simulated driver. */
    private void requestRide() {
        Instant fresh = clock.instant().minusSeconds(settings.getInt(Setting.DRIVER_LOCATION_FRESH_S));
        List<Driver> idle = drivers.findBySimulatedTrueAndStatus(DriverStatus.APPROVED).stream()
                .filter(d -> d.getAvailability() == Availability.ONLINE && d.getLat() != null
                        && d.getLocatedAt() != null && d.getLocatedAt().isAfter(fresh))
                .toList();
        if (idle.isEmpty()) {
            return;
        }
        Driver near = idle.get(random.nextInt(idle.size()));
        GeoPoint around = new GeoPoint(near.getLat(), near.getLng());
        List<DemoData.Place> pickups = DemoSeeder.placesNear(around, 0, 3500);
        if (pickups.isEmpty()) {
            return;
        }
        DemoData.Place pickup = pickups.get(random.nextInt(pickups.size()));
        List<DemoData.Place> drops = DemoSeeder.placesNear(pickup.point(), 2500, 11000);
        if (drops.isEmpty()) {
            return;
        }
        DemoData.Place drop = drops.get(random.nextInt(drops.size()));
        List<Long> riderIds = jdbc.queryForList("""
                SELECT r.id FROM rider r WHERE r.simulated = TRUE AND r.status = 'ACTIVE'
                AND NOT EXISTS (SELECT 1 FROM ride x WHERE x.rider_id = r.id AND x.status IN ('SEARCHING', 'ACCEPTED', 'ARRIVED', 'IN_PROGRESS'))
                """, Long.class);
        if (riderIds.isEmpty()) {
            return;
        }
        long riderId = riderIds.get(random.nextInt(riderIds.size()));
        QuoteService.QuoteResult quote = quotes.quote(riderId,
                new LocationInput(pickup.lat(), pickup.lng(), pickup.name() + ", " + pickup.address()),
                new LocationInput(drop.lat(), drop.lng(), drop.name() + ", " + drop.address()), null);
        double m = random.nextDouble();
        PaymentMethod method = m < 0.45 ? PaymentMethod.UPI : m < 0.8 ? PaymentMethod.CASH : PaymentMethod.WALLET;
        boolean nearbyOfType = !locator.idleNear(pickup.point(), settings.getInt(Setting.DISPATCH_RADIUS_M),
                near.getVehicleType(), fresh, true, null, false).isEmpty();
        if (!nearbyOfType) {
            return;
        }
        requests.request(riderId, quote.quote().getId(), near.getVehicleType(), method);
    }

    private record Plan(String purpose, Long rideId, RouteTracker tracker, long startedMs, double speedMs,
                        long restUntilMs) {

        boolean finished(long nowMs) {
            return (nowMs - startedMs) / 1000d * speedMs >= tracker.totalM();
        }

        Plan withRest(long until) {
            return new Plan(purpose, rideId, tracker, startedMs, speedMs, until);
        }
    }
}
