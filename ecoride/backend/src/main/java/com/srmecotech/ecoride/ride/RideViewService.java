package com.srmecotech.ecoride.ride;

import com.srmecotech.ecoride.common.settings.Setting;
import com.srmecotech.ecoride.common.settings.SettingsService;
import com.srmecotech.ecoride.common.util.Money;
import com.srmecotech.ecoride.driver.Driver;
import com.srmecotech.ecoride.driver.DriverRepository;
import com.srmecotech.ecoride.geo.GeoMath;
import com.srmecotech.ecoride.geo.GeoPoint;
import com.srmecotech.ecoride.geo.RouteTracker;
import com.srmecotech.ecoride.pricing.VehicleType;
import com.srmecotech.ecoride.pricing.VehicleTypeRepository;
import com.srmecotech.ecoride.rider.Rider;
import com.srmecotech.ecoride.rider.RiderRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Builds the rider's and driver's views of a ride, including the live distances: the cab's
 * latest GPS fix is snapped onto the planned road route and the distance left is measured
 * along it; the ETA uses that route's own speed. A cab more than 250 m off the route falls
 * back to straight line x road factor.
 */
@Service
public class RideViewService {

    private static final double OFF_ROUTE_M = 250;

    private final RideRepository rides;
    private final RideOfferRepository offers;
    private final DriverRepository drivers;
    private final RiderRepository riders;
    private final VehicleTypeRepository vehicleTypes;
    private final RideRequestService requests;
    private final RideLifecycleService lifecycle;
    private final SettingsService settings;
    private final RideLog rideLog;
    private final Clock clock;

    public RideViewService(RideRepository rides, RideOfferRepository offers, DriverRepository drivers,
                           RiderRepository riders, VehicleTypeRepository vehicleTypes, RideRequestService requests,
                           RideLifecycleService lifecycle, SettingsService settings, RideLog rideLog, Clock clock) {
        this.rides = rides;
        this.offers = offers;
        this.drivers = drivers;
        this.riders = riders;
        this.vehicleTypes = vehicleTypes;
        this.requests = requests;
        this.lifecycle = lifecycle;
        this.settings = settings;
        this.rideLog = rideLog;
        this.clock = clock;
    }

    public RideViews.RiderRide forRider(Ride ride) {
        Instant now = clock.instant();
        VehicleType vt = vehicleTypes.findById(ride.getVehicleType()).orElseThrow();
        Driver driver = ride.getDriverId() == null ? null : drivers.findById(ride.getDriverId()).orElse(null);
        boolean showDriver = driver != null && ride.getStatus() != RideStatus.SEARCHING;
        RideViews.DriverCard driverCard = showDriver ? driverCard(driver, vt, now,
                ride.getStatus().isAssigned()) : null;
        RideViews.Search search = null;
        if (ride.getStatus() == RideStatus.SEARCHING || ride.getStatus() == RideStatus.NO_DRIVERS) {
            RideOffer pending = offers.findFirstByRideIdAndStatus(ride.getId(), RideOffer.OfferStatus.PENDING).orElse(null);
            search = new RideViews.Search(
                    (int) Duration.between(ride.getSearchStartedAt(), now).toSeconds(),
                    settings.getInt(Setting.SEARCH_TIMEOUT_S),
                    (int) offers.countByRideId(ride.getId()),
                    pending == null ? null : (int) Math.max(0, Duration.between(now, pending.getExpiresAt()).toSeconds()));
        }
        boolean otpVisible = ride.getStatus() == RideStatus.ACCEPTED || ride.getStatus() == RideStatus.ARRIVED
                || ride.getStatus() == RideStatus.SEARCHING;
        return new RideViews.RiderRide(
                ride.getId(),
                ride.getCode(),
                ride.getStatus(),
                vt.getCode(),
                vt.getName(),
                new RideViews.Place(ride.getPickupLat(), ride.getPickupLng(), ride.getPickupAddress()),
                new RideViews.Place(ride.getDropLat(), ride.getDropLng(), ride.getDropAddress()),
                ride.getEstDistanceM(),
                ride.getEstDurationS(),
                ride.getRoutePolyline(),
                ride.getStatus() == RideStatus.ACCEPTED ? ride.getPickupPolyline() : null,
                otpVisible ? ride.getOtp() : null,
                driverCard,
                live(ride, driver, vt, now),
                search,
                fare(ride),
                ride.getActualDistanceM(),
                ride.getRequestedAt(),
                ride.getAcceptedAt(),
                ride.getArrivedAt(),
                ride.getStartedAt(),
                ride.getCompletedAt(),
                ride.getCancelledAt(),
                ride.getCancelledBy(),
                ride.getCancelReason(),
                ride.getDriverRating(),
                requests.cancellationFeeApplies(ride, now),
                requests.freeCancelLeftS(ride, now),
                Money.of(vt.getCancelFee()),
                timeline(ride.getId()));
    }

    public RideViews.DriverRide forDriver(Ride ride) {
        Instant now = clock.instant();
        VehicleType vt = vehicleTypes.findById(ride.getVehicleType()).orElseThrow();
        Driver driver = ride.getDriverId() == null ? null : drivers.findById(ride.getDriverId()).orElse(null);
        Rider rider = riders.findById(ride.getRiderId()).orElseThrow();
        RideViews.Position riderPosition = null;
        if (ride.getRiderLat() != null && (ride.getStatus() == RideStatus.ACCEPTED || ride.getStatus() == RideStatus.ARRIVED)) {
            riderPosition = new RideViews.Position(ride.getRiderLat(), ride.getRiderLng(), null, null,
                    ageS(ride.getRiderLocatedAt(), now));
        }
        Integer noShowIn = null;
        if (ride.getStatus() == RideStatus.ARRIVED && ride.getArrivedAt() != null) {
            long waited = Duration.between(ride.getArrivedAt(), now).toSeconds();
            noShowIn = (int) Math.max(0, settings.getInt(Setting.NO_SHOW_WAIT_MIN) * 60L - waited);
        }
        return new RideViews.DriverRide(
                ride.getId(),
                ride.getCode(),
                ride.getStatus(),
                vt.getCode(),
                new RideViews.Place(ride.getPickupLat(), ride.getPickupLng(), ride.getPickupAddress()),
                new RideViews.Place(ride.getDropLat(), ride.getDropLng(), ride.getDropAddress()),
                ride.getEstDistanceM(),
                ride.getEstDurationS(),
                ride.getRoutePolyline(),
                ride.getPickupPolyline(),
                new RideViews.RiderCard(rider.getId(), rider.getName(), rider.getPhone(), rider.getRatingAvg(),
                        rider.getRatingCount(), riderPosition),
                live(ride, driver, vt, now),
                fare(ride),
                ride.getDriverEarning() != null ? ride.getDriverEarning()
                        : estimatedEarning(ride.getQuote() == null ? ride.getEstFare() : ride.getQuote().fareBeforeDiscount()),
                ride.getActualDistanceM(),
                ride.getPickupTravelM(),
                ride.getAcceptedAt(),
                ride.getArrivedAt(),
                ride.getStartedAt(),
                ride.getCompletedAt(),
                ride.getCancelledAt(),
                ride.getCancelledBy(),
                ride.getCancelReason(),
                ride.getRiderRating(),
                lifecycle.isNoShow(ride, now),
                noShowIn);
    }

    public RideViews.Offer offer(RideOffer offer) {
        Instant now = clock.instant();
        Ride ride = rides.findById(offer.getRideId()).orElseThrow();
        Rider rider = riders.findById(ride.getRiderId()).orElseThrow();
        return new RideViews.Offer(
                offer.getId(),
                ride.getId(),
                (int) Math.max(0, Duration.between(now, offer.getExpiresAt()).toSeconds()),
                settings.getInt(Setting.OFFER_TIMEOUT_S),
                ride.getVehicleType(),
                new RideViews.Place(ride.getPickupLat(), ride.getPickupLng(), ride.getPickupAddress()),
                new RideViews.Place(ride.getDropLat(), ride.getDropLng(), ride.getDropAddress()),
                offer.getDistanceM(),
                offer.getEtaS(),
                ride.getEstDistanceM(),
                ride.getEstDurationS(),
                ride.getEstFare(),
                estimatedEarning(ride.getQuote() == null ? ride.getEstFare() : ride.getQuote().fareBeforeDiscount()),
                ride.getPaymentMethod(),
                ride.getSurgeMultiplier(),
                rider.getName(),
                rider.getRatingAvg(),
                ride.getRoutePolyline());
    }

    public RideDtos.RideSummary riderSummary(Ride ride) {
        String driverName = ride.getDriverId() == null ? null
                : drivers.findById(ride.getDriverId()).map(Driver::getName).orElse(null);
        return summary(ride, driverName, ride.getDriverRating());
    }

    public RideDtos.RideSummary driverSummary(Ride ride) {
        String riderName = riders.findById(ride.getRiderId()).map(Rider::getName).orElse(null);
        return summary(ride, riderName, ride.getRiderRating());
    }

    private RideDtos.RideSummary summary(Ride ride, String counterpart, Integer rating) {
        String typeName = vehicleTypes.findById(ride.getVehicleType()).map(VehicleType::getName).orElse(ride.getVehicleType());
        BigDecimal total = ride.getStatus() == RideStatus.COMPLETED ? ride.getFareTotal()
                : ride.getStatus() == RideStatus.CANCELLED ? ride.getCancellationFee() : ride.getEstFare();
        int metres = ride.getStatus() == RideStatus.COMPLETED && ride.getActualDistanceM() > 0
                ? ride.getActualDistanceM() : ride.getEstDistanceM();
        return new RideDtos.RideSummary(ride.getId(), ride.getCode(), ride.getStatus(), ride.getVehicleType(), typeName,
                ride.getPickupAddress(), ride.getDropAddress(), ride.getRequestedAt(), ride.getCompletedAt(), total,
                ride.getPaymentMethod(), counterpart, rating, Math.round(metres / 100d) / 10d);
    }

    public RideViews.DriverCard driverCard(Driver driver, VehicleType vt, Instant now, boolean withPosition) {
        RideViews.Position position = withPosition && driver.getLat() != null
                ? new RideViews.Position(driver.getLat(), driver.getLng(), driver.getHeading(), driver.getSpeedKmh(),
                ageS(driver.getLocatedAt(), now))
                : null;
        return new RideViews.DriverCard(driver.getId(), driver.getName(), driver.getPhone(), driver.getRatingAvg(),
                driver.getRatingCount(), rides.countCompletedByDriver(driver.getId()),
                new RideViews.Vehicle(vt.getCode(), vt.getName(), driver.getVehicleMake(), driver.getVehicleModel(),
                        driver.getVehicleColor(), driver.getVehiclePlate()),
                position);
    }

    public RideViews.Fare fare(Ride ride) {
        BigDecimal total = ride.getFareTotal() != null ? ride.getFareTotal() : ride.getEstFare();
        return new RideViews.Fare(ride.getQuote(), ride.getFare(), ride.getFarePolicy(), total, ride.getPaymentMethod(),
                ride.getPaymentStatus(), ride.getTip(), ride.getCancellationFee());
    }

    public List<RideViews.TimelineEntry> timeline(long rideId) {
        return rideLog.events(rideId).stream()
                .map(e -> new RideViews.TimelineEntry(e.type(), e.actor(), e.message(), e.at()))
                .toList();
    }

    /** The driver's share of a fare before tax and discount. */
    public BigDecimal estimatedEarning(BigDecimal gross) {
        if (gross == null) {
            return Money.ZERO;
        }
        return Money.of(gross.subtract(Money.percent(gross, settings.getInt(Setting.COMMISSION_PCT))));
    }

    public RideViews.Live live(Ride ride, Driver driver, VehicleType vt, Instant now) {
        GeoPoint cab = driver != null && driver.getLat() != null ? new GeoPoint(driver.getLat(), driver.getLng()) : null;
        Integer toPickupM = null;
        Integer toPickupEta = null;
        Integer remainingM = null;
        Integer remainingEta = null;
        Integer waitingS = null;
        Integer freeWaitLeft = null;
        double progress = 0;
        boolean offRoute = false;
        switch (ride.getStatus()) {
            case ACCEPTED -> {
                if (cab != null) {
                    Leg leg = leg(ride.getPickupPolyline(), cab, ride.pickup(), ride.getPickupDistanceM(), ride.getPickupDurationS());
                    toPickupM = leg.remainingM();
                    toPickupEta = leg.etaS();
                    progress = leg.progress();
                    offRoute = leg.offRoute();
                }
            }
            case ARRIVED -> {
                toPickupM = 0;
                toPickupEta = 0;
                progress = 1;
                waitingS = (int) Duration.between(ride.getArrivedAt(), now).toSeconds();
                freeWaitLeft = Math.max(0, vt.getWaitFreeMin() * 60 - waitingS);
            }
            case IN_PROGRESS -> {
                Leg leg = leg(ride.getRoutePolyline(), cab != null ? cab : ride.pickup(), ride.drop(),
                        ride.getEstDistanceM(), ride.getEstDurationS());
                remainingM = leg.remainingM();
                remainingEta = leg.etaS();
                progress = leg.progress();
                offRoute = leg.offRoute();
            }
            case COMPLETED -> {
                remainingM = 0;
                remainingEta = 0;
                progress = 1;
            }
            default -> {
            }
        }
        Integer riderToPickup = null;
        if (ride.getRiderLat() != null && (ride.getStatus() == RideStatus.ACCEPTED || ride.getStatus() == RideStatus.ARRIVED)) {
            riderToPickup = (int) Math.round(GeoMath.distanceM(ride.getRiderLat(), ride.getRiderLng(),
                    ride.getPickupLat(), ride.getPickupLng()));
        }
        return new RideViews.Live(toPickupM, toPickupEta, remainingM, remainingEta, ride.getActualDistanceM(),
                Math.round(progress * 1000) / 1000d, offRoute, waitingS, freeWaitLeft, riderToPickup);
    }

    private Leg leg(String polyline, GeoPoint from, GeoPoint target, Integer legDistanceM, Integer legDurationS) {
        double speedMs = legDistanceM != null && legDurationS != null && legDistanceM > 0 && legDurationS > 0
                ? legDistanceM / (double) legDurationS
                : settings.getInt(Setting.CITY_SPEED_KMH) / 3.6;
        double remaining;
        double total;
        boolean offRoute = false;
        if (polyline != null && !polyline.isEmpty()) {
            RouteTracker tracker = RouteTracker.of(polyline);
            RouteTracker.Progress p = tracker.locate(from);
            total = tracker.totalM();
            if (p.offRouteM() > OFF_ROUTE_M) {
                offRoute = true;
                remaining = GeoMath.distanceM(from, target) * settings.getDouble(Setting.ROAD_FACTOR);
            } else {
                remaining = p.remainingM();
            }
        } else {
            remaining = GeoMath.distanceM(from, target) * settings.getDouble(Setting.ROAD_FACTOR);
            total = legDistanceM == null ? remaining : legDistanceM;
        }
        if (remaining < 30) {
            remaining = 0;
        }
        double progress = total <= 0 ? 0 : Math.max(0, Math.min(1, 1 - remaining / total));
        return new Leg((int) Math.round(remaining), (int) Math.round(remaining / speedMs), progress, offRoute);
    }

    private static Integer ageS(Instant at, Instant now) {
        return at == null ? null : (int) Math.max(0, Duration.between(at, now).toSeconds());
    }

    private record Leg(int remainingM, int etaS, double progress, boolean offRoute) {
    }
}
