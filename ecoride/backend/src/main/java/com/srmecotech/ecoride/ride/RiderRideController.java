package com.srmecotech.ecoride.ride;

import com.srmecotech.ecoride.common.error.NotFoundException;
import com.srmecotech.ecoride.common.security.AuthenticatedUser;
import com.srmecotech.ecoride.common.settings.Setting;
import com.srmecotech.ecoride.common.settings.SettingsService;
import com.srmecotech.ecoride.driver.DriverLocator;
import com.srmecotech.ecoride.geo.GeoPoint;
import com.srmecotech.ecoride.pricing.FareQuote;
import com.srmecotech.ecoride.pricing.QuoteService;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rider")
@Tag(name = "Rider app · rides")
public class RiderRideController {

    /** A finished trip stays "current" this long, so the rating screen survives an app restart. */
    private static final Duration RATE_WINDOW = Duration.ofMinutes(30);

    private final QuoteService quotes;
    private final RideRequestService requests;
    private final RideViewService views;
    private final RideRepository rides;
    private final DriverLocator locator;
    private final DispatchService dispatch;
    private final SettingsService settings;
    private final Clock clock;

    public RiderRideController(QuoteService quotes, RideRequestService requests, RideViewService views,
                               RideRepository rides, DriverLocator locator, DispatchService dispatch,
                               SettingsService settings, Clock clock) {
        this.quotes = quotes;
        this.requests = requests;
        this.views = views;
        this.rides = rides;
        this.locator = locator;
        this.dispatch = dispatch;
        this.settings = settings;
        this.clock = clock;
    }

    @GetMapping("/nearby")
    @Operation(summary = "Free cars around a point, for the home map")
    public RideDtos.Nearby nearby(@RequestParam double lat, @RequestParam double lng) {
        Instant fresh = clock.instant().minusSeconds(settings.getInt(Setting.DRIVER_LOCATION_FRESH_S));
        List<DriverLocator.NearbyDriver> found = locator.idleNear(new GeoPoint(lat, lng),
                settings.getInt(Setting.DISPATCH_RADIUS_M), null, fresh, null, null, false);
        Map<String, Integer> eta = new LinkedHashMap<>();
        for (DriverLocator.NearbyDriver d : found) {
            eta.putIfAbsent(d.vehicleType(), dispatch.etaSeconds(d.distanceM()));
        }
        List<RideDtos.NearbyCar> cars = found.stream().limit(25)
                .map(d -> new RideDtos.NearbyCar(d.vehicleType(), d.lat(), d.lng(), d.heading()))
                .toList();
        return new RideDtos.Nearby(cars, eta);
    }

    @PostMapping("/quotes")
    @Operation(summary = "Upfront fares for every ride type, with the road route and nearest-driver ETAs")
    public RideDtos.QuoteResponse quote(@AuthenticationPrincipal AuthenticatedUser user,
                                        @Valid @RequestBody RideDtos.QuoteRequest request) {
        QuoteService.QuoteResult result = quotes.quote(user.id(), request.pickup(), request.drop(), request.promoCode());
        FareQuote q = result.quote();
        return new RideDtos.QuoteResponse(
                q.getId(),
                q.getExpiresAt(),
                (int) Duration.between(clock.instant(), q.getExpiresAt()).toSeconds(),
                new RideViews.Place(q.getPickupLat(), q.getPickupLng(), q.getPickupAddress()),
                new RideViews.Place(q.getDropLat(), q.getDropLng(), q.getDropAddress()),
                new RideDtos.RouteInfo(q.getDistanceM(), q.getDurationS(), q.getPolyline(), q.getRouteProvider()),
                new RideDtos.SurgeInfo(result.surge().multiplier(), result.surge().zone(), result.surge().mode()),
                result.promo().code() == null ? null
                        : new RideDtos.PromoInfo(result.promo().code(), result.promo().valid(), result.promo().message()),
                q.getOptions().items());
    }

    @PostMapping("/rides")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Book: the held upfront fare for one ride type; dispatch starts at once")
    public RideViews.RiderRide request(@AuthenticationPrincipal AuthenticatedUser user,
                                       @Valid @RequestBody RideDtos.RideRequest request) {
        Ride ride = requests.request(user.id(), request.quoteId(), request.vehicleType(), request.paymentMethod());
        return views.forRider(ride);
    }

    @GetMapping("/rides/current")
    @Operation(summary = "The ride in progress, or the trip just finished and not yet rated")
    @Transactional(readOnly = true)
    public RideDtos.CurrentRide current(@AuthenticationPrincipal AuthenticatedUser user) {
        Ride ride = rides.findFirstByRiderIdAndStatusInOrderByIdDesc(user.id(), RideStatus.ACTIVE)
                .or(() -> rides.findFirstByRiderIdAndStatusInOrderByIdDesc(user.id(), List.of(RideStatus.COMPLETED))
                        .filter(r -> r.getDriverRating() == null
                                && r.getCompletedAt().isAfter(clock.instant().minus(RATE_WINDOW))))
                .orElse(null);
        return new RideDtos.CurrentRide(ride == null ? null : views.forRider(ride));
    }

    @GetMapping("/rides/{id}")
    @Transactional(readOnly = true)
    public RideViews.RiderRide ride(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id) {
        Ride ride = rides.findById(id).orElseThrow(() -> new NotFoundException("Ride", id));
        requests.requireOwner(ride, user.id());
        return views.forRider(ride);
    }

    @GetMapping("/rides")
    @Operation(summary = "Ride history, newest first")
    @Transactional(readOnly = true)
    public List<RideDtos.RideSummary> history(@AuthenticationPrincipal AuthenticatedUser user,
                                              @RequestParam(defaultValue = "0") int page) {
        return rides.findByRiderIdOrderByIdDesc(user.id(), PageRequest.of(Math.max(0, page), 20))
                .map(views::riderSummary)
                .getContent();
    }

    @PostMapping("/rides/{id}/cancel")
    @Operation(summary = "Cancel: free while searching and during the free window after a driver accepts")
    public RideViews.RiderRide cancel(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id,
                                      @Valid @RequestBody(required = false) RideDtos.CancelRequest request) {
        Ride ride = requests.cancel(user.id(), id, request == null ? null : request.reason());
        return views.forRider(ride);
    }

    @PostMapping("/rides/{id}/location")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Share the rider's position with the driver while the cab is on its way")
    public void location(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id,
                         @Valid @RequestBody RideDtos.LocationPing ping) {
        requests.shareLocation(user.id(), id, new GeoPoint(ping.lat(), ping.lng()));
    }

    @PostMapping("/rides/{id}/rate")
    @Operation(summary = "Rate the driver, optionally with compliments and a tip")
    public RideViews.RiderRide rate(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id,
                                    @Valid @RequestBody RideDtos.RateDriverRequest request) {
        Ride ride = requests.rate(user.id(), id, request.stars(), request.tags(), request.comment(), request.tip());
        return views.forRider(ride);
    }

    @PostMapping("/rides/{id}/sos")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Emergency: alerts the EcoRide safety desk with the ride and position")
    public Map<String, Object> sos(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id,
                                   @Valid @RequestBody(required = false) RideDtos.SosRequest request) {
        Ride ride = rides.findById(id).orElseThrow(() -> new NotFoundException("Ride", id));
        requests.requireOwner(ride, user.id());
        GeoPoint at = request != null && request.lat() != null && request.lng() != null
                ? new GeoPoint(request.lat(), request.lng()) : null;
        SosAlert alert = requests.sos(id, "RIDER", at, request == null ? null : request.note());
        return Map.of("id", alert.getId(), "message", "EcoRide safety team alerted. Stay on the line if you can.");
    }
}
