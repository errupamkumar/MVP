package com.srmecotech.ecoride.ride;

import com.srmecotech.ecoride.common.settings.Setting;
import com.srmecotech.ecoride.common.settings.SettingsService;
import com.srmecotech.ecoride.driver.DriverLocator;
import com.srmecotech.ecoride.driver.DriverRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Sequential dispatch, as the ride-hailing apps do it: a searching ride is offered to one
 * driver at a time, nearest first, with OFFER_TIMEOUT_S to accept. A decline or a timeout
 * moves it to the next driver; after SEARCH_TIMEOUT_S the rider is told no cars are free.
 * <p>
 * Only {@link DispatchScheduler}'s single thread calls {@link #sweep()}, so two rides are
 * never offered to the same driver at the same moment. Each ride is handled in its own
 * transaction with its row locked, so a rider cancelling or a driver accepting at the same
 * instant simply waits its turn.
 */
@Service
public class DispatchService {

    private static final Logger log = LoggerFactory.getLogger(DispatchService.class);

    private final RideRepository rides;
    private final RideOfferRepository offers;
    private final DriverRepository drivers;
    private final DriverLocator locator;
    private final SettingsService settings;
    private final RideLog rideLog;
    private final TransactionTemplate tx;
    private final Clock clock;

    public DispatchService(RideRepository rides, RideOfferRepository offers, DriverRepository drivers,
                           DriverLocator locator, SettingsService settings, RideLog rideLog,
                           TransactionTemplate tx, Clock clock) {
        this.rides = rides;
        this.offers = offers;
        this.drivers = drivers;
        this.locator = locator;
        this.settings = settings;
        this.rideLog = rideLog;
        this.tx = tx;
        this.clock = clock;
    }

    /** One pass over every searching ride. */
    public void sweep() {
        for (Long rideId : rides.findSearchingIds()) {
            try {
                tx.executeWithoutResult(status -> advance(rideId));
            } catch (RuntimeException ex) {
                log.warn("Dispatch of ride {} failed this round: {}", rideId, ex.getMessage());
            }
        }
    }

    void advance(long rideId) {
        Ride ride = rides.lockById(rideId).orElse(null);
        if (ride == null || ride.getStatus() != RideStatus.SEARCHING) {
            return;
        }
        Instant now = clock.instant();
        RideOffer pending = offers.findFirstByRideIdAndStatus(rideId, RideOffer.OfferStatus.PENDING).orElse(null);
        if (pending != null) {
            if (pending.getExpiresAt().isAfter(now)) {
                return;
            }
            pending.setStatus(RideOffer.OfferStatus.EXPIRED);
            pending.setRespondedAt(now);
            offers.saveAndFlush(pending);
            rideLog.event(rideId, "OFFER_EXPIRED", "SYSTEM", "Driver %s did not answer in %d s"
                    .formatted(driverName(pending.getDriverId()), settings.getInt(Setting.OFFER_TIMEOUT_S)));
        }
        long searchingFor = Duration.between(ride.getSearchStartedAt(), now).toSeconds();
        if (searchingFor >= settings.getInt(Setting.SEARCH_TIMEOUT_S)) {
            ride.setStatus(RideStatus.NO_DRIVERS);
            ride.setCancelledAt(now);
            ride.setCancelledBy("SYSTEM");
            ride.setCancelReason("No driver accepted within " + searchingFor + " s");
            rides.save(ride);
            rideLog.event(rideId, "NO_DRIVERS", "SYSTEM", "No driver accepted. The rider was told no cars are available.");
            return;
        }
        DriverLocator.NearbyDriver next = nextCandidate(ride, now);
        if (next == null) {
            return;
        }
        int timeout = settings.getInt(Setting.OFFER_TIMEOUT_S);
        int etaS = etaSeconds(next.distanceM());
        RideOffer offer = new RideOffer();
        offer.setRideId(rideId);
        offer.setDriverId(next.id());
        offer.setStatus(RideOffer.OfferStatus.PENDING);
        offer.setDistanceM((int) Math.round(next.distanceM()));
        offer.setEtaS(etaS);
        offer.setOfferedAt(now);
        offer.setExpiresAt(now.plusSeconds(timeout));
        offers.saveAndFlush(offer);
        drivers.countOffer(next.id());
        rideLog.event(rideId, "OFFERED", "SYSTEM", "Offered to %s%s, %.1f km away (about %d min)"
                .formatted(driverName(next.id()), next.simulated() ? " (simulated)" : "", next.distanceM() / 1000d,
                        Math.max(1, Math.round(etaS / 60.0))));
    }

    /**
     * The nearest free driver of the right type who has not seen this ride yet. Simulated rides
     * go to simulated drivers only; with PREFER_REAL_DRIVERS, real drivers come first.
     */
    DriverLocator.NearbyDriver nextCandidate(Ride ride, Instant now) {
        Instant fresh = now.minusSeconds(settings.getInt(Setting.DRIVER_LOCATION_FRESH_S));
        List<DriverLocator.NearbyDriver> candidates = locator.idleNear(ride.pickup(), settings.getInt(Setting.DISPATCH_RADIUS_M),
                ride.getVehicleType(), fresh, ride.isSimulated() ? Boolean.TRUE : null, ride.getId(), true);
        Comparator<DriverLocator.NearbyDriver> order = Comparator.comparingDouble(DriverLocator.NearbyDriver::distanceM);
        if (!ride.isSimulated() && settings.getBool(Setting.PREFER_REAL_DRIVERS)) {
            order = Comparator.comparing(DriverLocator.NearbyDriver::simulated).thenComparing(order);
        }
        return candidates.stream().min(order).orElse(null);
    }

    /** Pickup ETA from straight-line distance: road factor and average city speed, at least a minute. */
    public int etaSeconds(double straightLineM) {
        double road = straightLineM * settings.getDouble(Setting.ROAD_FACTOR);
        double speedMs = settings.getInt(Setting.CITY_SPEED_KMH) / 3.6;
        return (int) Math.max(60, Math.round(road / speedMs));
    }

    private String driverName(Long driverId) {
        return drivers.findById(driverId).map(d -> d.getName()).orElse("#" + driverId);
    }
}
