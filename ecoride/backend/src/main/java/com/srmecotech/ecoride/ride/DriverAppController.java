package com.srmecotech.ecoride.ride;

import com.srmecotech.ecoride.common.config.ClockConfig;
import com.srmecotech.ecoride.common.error.ForbiddenOperationException;
import com.srmecotech.ecoride.common.error.NotFoundException;
import com.srmecotech.ecoride.common.security.AuthenticatedUser;
import com.srmecotech.ecoride.driver.Availability;
import com.srmecotech.ecoride.driver.Driver;
import com.srmecotech.ecoride.driver.DriverAccountService;
import com.srmecotech.ecoride.driver.DriverDtos;
import com.srmecotech.ecoride.driver.DriverLedgerService;
import com.srmecotech.ecoride.driver.DriverRepository;
import com.srmecotech.ecoride.geo.GeoPoint;
import com.srmecotech.ecoride.geo.RouteResult;
import com.srmecotech.ecoride.geo.RoutingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/driver")
@Tag(name = "Driver app")
public class DriverAppController {

    /** A finished trip stays on the driver's screen this long while cash or the rider rating is outstanding. */
    private static final Duration LAST_TRIP_WINDOW = Duration.ofMinutes(30);

    private final DriverAccountService accounts;
    private final DriverRepository drivers;
    private final DriverLedgerService ledger;
    private final RideLifecycleService lifecycle;
    private final RideRequestService requests;
    private final LocationService locations;
    private final RideViewService views;
    private final RideRepository rides;
    private final RideOfferRepository offers;
    private final RoutingService routing;
    private final Clock clock;

    public DriverAppController(DriverAccountService accounts, DriverRepository drivers, DriverLedgerService ledger,
                               RideLifecycleService lifecycle, RideRequestService requests, LocationService locations,
                               RideViewService views, RideRepository rides, RideOfferRepository offers,
                               RoutingService routing, Clock clock) {
        this.accounts = accounts;
        this.drivers = drivers;
        this.ledger = ledger;
        this.lifecycle = lifecycle;
        this.requests = requests;
        this.locations = locations;
        this.views = views;
        this.rides = rides;
        this.offers = offers;
        this.routing = routing;
        this.clock = clock;
    }

    @GetMapping("/me")
    public DriverDtos.Profile me(@AuthenticationPrincipal AuthenticatedUser user) {
        return accounts.profile(user.id());
    }

    @GetMapping("/state")
    @Operation(summary = "One poll for the driver app: status, the request on screen, the current ride, today's earnings")
    @Transactional(readOnly = true)
    public RideDtos.DriverState state(@AuthenticationPrincipal AuthenticatedUser user) {
        return buildState(user.id());
    }

    @PostMapping("/online")
    @Operation(summary = "Go online (approved drivers only) at the given position")
    public RideDtos.DriverState online(@AuthenticationPrincipal AuthenticatedUser user,
                                       @Valid @RequestBody(required = false) RideDtos.OnlineRequest request) {
        GeoPoint at = request != null && request.lat() != null && request.lng() != null
                ? new GeoPoint(request.lat(), request.lng()) : null;
        lifecycle.goOnline(user.id(), at);
        return buildState(user.id());
    }

    @PostMapping("/offline")
    public RideDtos.DriverState offline(@AuthenticationPrincipal AuthenticatedUser user) {
        lifecycle.goOffline(user.id());
        return buildState(user.id());
    }

    @PostMapping("/location")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "A GPS fix from the driver's phone (every few seconds while online)")
    public void location(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody RideDtos.LocationPing ping) {
        locations.driverPing(user.id(), new GeoPoint(ping.lat(), ping.lng()), ping.heading(), ping.speedKmh(),
                ping.accuracyM(), Boolean.TRUE.equals(ping.simulated()));
    }

    @PostMapping("/offers/{offerId}/accept")
    @Operation(summary = "Accept a ride request; the road route to the pickup is planned from the driver's position")
    public RideViews.DriverRide accept(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long offerId) {
        RideOffer offer = offers.findById(offerId).orElseThrow(() -> new NotFoundException("Offer", offerId));
        if (!offer.getDriverId().equals(user.id())) {
            throw new ForbiddenOperationException("This offer was sent to another driver.");
        }
        Ride ride = rides.findById(offer.getRideId()).orElseThrow();
        Driver driver = drivers.findById(user.id()).orElseThrow();
        // Route outside the transaction: no row stays locked while the routing server answers.
        RouteResult toPickup = driver.getLat() == null ? null
                : routing.route(new GeoPoint(driver.getLat(), driver.getLng()), ride.pickup());
        return views.forDriver(lifecycle.accept(user.id(), offerId, toPickup));
    }

    @PostMapping("/offers/{offerId}/decline")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void decline(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long offerId) {
        lifecycle.decline(user.id(), offerId);
    }

    @GetMapping("/rides/{id}")
    @Transactional(readOnly = true)
    public RideViews.DriverRide ride(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id) {
        Ride ride = rides.findById(id).orElseThrow(() -> new NotFoundException("Ride", id));
        if (ride.getDriverId() == null || !ride.getDriverId().equals(user.id())) {
            throw new ForbiddenOperationException("This ride is not assigned to you.");
        }
        return views.forDriver(ride);
    }

    @GetMapping("/rides")
    @Transactional(readOnly = true)
    public List<RideDtos.RideSummary> history(@AuthenticationPrincipal AuthenticatedUser user,
                                              @RequestParam(defaultValue = "0") int page) {
        return rides.findByDriverIdAndStatusInOrderByIdDesc(user.id(),
                        List.of(RideStatus.COMPLETED, RideStatus.CANCELLED), PageRequest.of(Math.max(0, page), 20))
                .map(views::driverSummary)
                .getContent();
    }

    @PostMapping("/rides/{id}/arrived")
    @Operation(summary = "At the pickup (only within ARRIVAL_RADIUS_M of it)")
    public RideViews.DriverRide arrived(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id) {
        return views.forDriver(lifecycle.arrived(user.id(), id));
    }

    @PostMapping("/rides/{id}/start")
    @Operation(summary = "Start the trip with the rider's 4-digit code")
    public RideViews.DriverRide start(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id,
                                      @Valid @RequestBody RideDtos.StartRequest request) {
        return views.forDriver(lifecycle.start(user.id(), id, request.otp()));
    }

    @PostMapping("/rides/{id}/complete")
    @Operation(summary = "End the trip: final fare from the GPS distance, payment, earnings")
    public RideViews.DriverRide complete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id) {
        return views.forDriver(lifecycle.complete(user.id(), id));
    }

    @PostMapping("/rides/{id}/cash-collected")
    public RideViews.DriverRide cashCollected(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id) {
        return views.forDriver(lifecycle.cashCollected(user.id(), id));
    }

    @PostMapping("/rides/{id}/cancel")
    @Operation(summary = "Cancel before the trip starts (rider no-show after the wait; otherwise a penalty and re-dispatch)")
    public RideViews.DriverRide cancel(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id,
                                       @Valid @RequestBody(required = false) RideDtos.CancelRequest request) {
        // After a driver cancellation the ride is back in dispatch (status SEARCHING, no driver): the app goes home.
        return views.forDriver(lifecycle.driverCancel(user.id(), id, request == null ? null : request.reason()));
    }

    @PostMapping("/rides/{id}/rate")
    public RideViews.DriverRide rate(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id,
                                     @Valid @RequestBody RideDtos.RateRiderRequest request) {
        return views.forDriver(lifecycle.rateRider(user.id(), id, request.stars()));
    }

    @PostMapping("/rides/{id}/sos")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> sos(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id,
                                   @Valid @RequestBody(required = false) RideDtos.SosRequest request) {
        Ride ride = rides.findById(id).orElseThrow(() -> new NotFoundException("Ride", id));
        if (ride.getDriverId() == null || !ride.getDriverId().equals(user.id())) {
            throw new ForbiddenOperationException("This ride is not assigned to you.");
        }
        GeoPoint at = request != null && request.lat() != null && request.lng() != null
                ? new GeoPoint(request.lat(), request.lng()) : null;
        SosAlert alert = requests.sos(id, "DRIVER", at, request == null ? null : request.note());
        return Map.of("id", alert.getId(), "message", "EcoRide safety team alerted.");
    }

    @GetMapping("/earnings")
    @Operation(summary = "Today, the last 7 days, the payout balance and the ledger")
    public DriverDtos.Earnings earnings(@AuthenticationPrincipal AuthenticatedUser user) {
        return accounts.earnings(user.id());
    }

    private RideDtos.DriverState buildState(long driverId) {
        Driver driver = drivers.findById(driverId).orElseThrow(() -> new NotFoundException("Driver", driverId));
        Instant now = clock.instant();
        RideViews.Position position = driver.getLat() == null ? null
                : new RideViews.Position(driver.getLat(), driver.getLng(), driver.getHeading(), driver.getSpeedKmh(),
                driver.getLocatedAt() == null ? null : (int) Duration.between(driver.getLocatedAt(), now).toSeconds());
        RideViews.Offer offer = driver.getAvailability() == Availability.ONLINE
                ? offers.findFirstByDriverIdAndStatusOrderByIdAsc(driverId, RideOffer.OfferStatus.PENDING)
                .filter(o -> o.getExpiresAt().isAfter(now))
                .map(views::offer)
                .orElse(null)
                : null;
        RideViews.DriverRide ride = rides.findFirstByDriverIdAndStatusInOrderByIdDesc(driverId, RideStatus.ASSIGNED)
                .map(views::forDriver)
                .orElse(null);
        RideViews.DriverRide lastTrip = ride != null ? null
                : rides.findFirstByDriverIdAndStatusInOrderByIdDesc(driverId, List.of(RideStatus.COMPLETED))
                .filter(r -> r.getCompletedAt().isAfter(now.minus(LAST_TRIP_WINDOW)))
                .filter(r -> r.getRiderRating() == null
                        || (r.getPaymentMethod() == PaymentMethod.CASH && r.getPaymentStatus() == PaymentStatus.PENDING))
                .map(views::forDriver)
                .orElse(null);
        LocalDate today = LocalDate.now(clock);
        Instant startToday = today.atStartOfDay(ClockConfig.CITY_ZONE).toInstant();
        Instant endToday = today.plusDays(1).atStartOfDay(ClockConfig.CITY_ZONE).toInstant();
        var earnedToday = ledger.sum(driverId, startToday, endToday, DriverLedgerService.EARNING, DriverLedgerService.TIP,
                DriverLedgerService.CANCEL_FEE, DriverLedgerService.PENALTY, DriverLedgerService.ADJUSTMENT);
        int tripsToday = (int) rides.countCompletedByDriverBetween(driverId, startToday, endToday);
        Integer onlineMinutes = driver.getOnlineSince() == null ? null
                : (int) Duration.between(driver.getOnlineSince(), now).toMinutes();
        return new RideDtos.DriverState(accounts.toProfile(driver), position,
                new RideDtos.Today(earnedToday, tripsToday, onlineMinutes), offer, ride, lastTrip);
    }
}
