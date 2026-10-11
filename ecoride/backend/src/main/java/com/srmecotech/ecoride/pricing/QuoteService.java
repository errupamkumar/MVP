package com.srmecotech.ecoride.pricing;

import com.srmecotech.ecoride.common.error.InvalidRequestException;
import com.srmecotech.ecoride.common.error.NotFoundException;
import com.srmecotech.ecoride.common.settings.Setting;
import com.srmecotech.ecoride.common.settings.SettingsService;
import com.srmecotech.ecoride.driver.DriverLocator;
import com.srmecotech.ecoride.geo.GeoPoint;
import com.srmecotech.ecoride.geo.GeocodingService;
import com.srmecotech.ecoride.geo.LocationInput;
import com.srmecotech.ecoride.geo.RouteResult;
import com.srmecotech.ecoride.geo.RoutingService;
import com.srmecotech.ecoride.geo.ServiceArea;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Upfront pricing: road route, surge at the pickup, promo, and for every product the fare
 * and how soon the nearest free driver could be there. The quote is stored and held, so the
 * price the rider accepted is the price on the ride.
 */
@Service
public class QuoteService {

    private static final int MIN_TRIP_M = 300;
    private static final int MAX_TRIP_M = 100_000;

    private final RoutingService routing;
    private final GeocodingService geocoding;
    private final ServiceArea serviceArea;
    private final SurgeService surge;
    private final PromoService promos;
    private final VehicleTypeRepository vehicleTypes;
    private final FareQuoteRepository quotes;
    private final DriverLocator locator;
    private final SettingsService settings;
    private final Clock clock;

    public QuoteService(RoutingService routing, GeocodingService geocoding, ServiceArea serviceArea, SurgeService surge,
                        PromoService promos, VehicleTypeRepository vehicleTypes, FareQuoteRepository quotes,
                        DriverLocator locator, SettingsService settings, Clock clock) {
        this.routing = routing;
        this.geocoding = geocoding;
        this.serviceArea = serviceArea;
        this.surge = surge;
        this.promos = promos;
        this.vehicleTypes = vehicleTypes;
        this.quotes = quotes;
        this.locator = locator;
        this.settings = settings;
        this.clock = clock;
    }

    @Transactional
    public QuoteResult quote(long riderId, LocationInput pickupIn, LocationInput dropIn, String promoCode) {
        GeoPoint pickup = pickupIn.point();
        GeoPoint drop = dropIn.point();
        serviceArea.require(pickup, "The pickup");
        serviceArea.require(drop, "The drop");
        if (pickup.distanceTo(drop) < MIN_TRIP_M) {
            throw new InvalidRequestException("TRIP_TOO_SHORT",
                    "The pickup and drop are less than " + MIN_TRIP_M + " m apart. Choose a different drop.");
        }
        RouteResult route = routing.route(pickup, drop);
        if (route.distanceM() > MAX_TRIP_M) {
            throw new InvalidRequestException("TRIP_TOO_LONG", "EcoRide covers trips up to 100 km by road.");
        }
        SurgeService.SurgeInfo surgeInfo = surge.surgeAt(pickup);
        PromoService.PromoCheck promo = promos.check(promoCode, riderId);
        double gst = settings.getDouble(Setting.GST_PCT);

        Map<String, List<DriverLocator.NearbyDriver>> nearbyByType = nearbyFreeDrivers(pickup);
        double roadFactor = settings.getDouble(Setting.ROAD_FACTOR);
        double speedMs = settings.getInt(Setting.CITY_SPEED_KMH) / 3.6;

        List<FareQuote.QuoteOption> options = new ArrayList<>();
        for (VehicleType vt : vehicleTypes.findByActiveTrueOrderBySortOrder()) {
            FareBreakdown noPromo = FareCalculator.calculate(vt, route.distanceM(), route.durationS(),
                    surgeInfo.multiplier(), 0, null, gst);
            FareCalculator.Promo applied = promo.valid()
                    && noPromo.fareBeforeDiscount().compareTo(promo.promo().getMinFare()) >= 0
                    ? promo.promo().toPromo() : null;
            FareBreakdown fare = applied == null ? noPromo
                    : FareCalculator.calculate(vt, route.distanceM(), route.durationS(), surgeInfo.multiplier(), 0, applied, gst);
            List<DriverLocator.NearbyDriver> nearby = nearbyByType.getOrDefault(vt.getCode(), List.of());
            Integer eta = nearby.isEmpty() ? null
                    : (int) Math.max(60, Math.round(nearby.get(0).distanceM() * roadFactor / speedMs));
            options.add(new FareQuote.QuoteOption(vt.getCode(), vt.getName(), vt.getDescription(), vt.getCapacity(),
                    fare, eta, nearby.size()));
        }

        Instant now = clock.instant();
        FareQuote quote = new FareQuote();
        quote.setId(UUID.randomUUID().toString());
        quote.setRiderId(riderId);
        quote.setPickupLat(pickup.lat());
        quote.setPickupLng(pickup.lng());
        quote.setPickupAddress(addressOf(pickupIn));
        quote.setDropLat(drop.lat());
        quote.setDropLng(drop.lng());
        quote.setDropAddress(addressOf(dropIn));
        quote.setDistanceM(route.distanceM());
        quote.setDurationS(route.durationS());
        quote.setPolyline(route.polyline());
        quote.setRouteProvider(route.provider());
        quote.setSurgeMultiplier(surgeInfo.multiplier());
        quote.setSurgeZone(surgeInfo.zone());
        quote.setPromoCode(promo.valid() ? promo.code() : null);
        quote.setOptions(new FareQuote.Options(options));
        quote.setCreatedAt(now);
        quote.setExpiresAt(now.plusSeconds(settings.getInt(Setting.QUOTE_TTL_S)));
        quotes.save(quote);
        quotes.deleteExpired(now.minus(Duration.ofHours(1)));
        return new QuoteResult(quote, surgeInfo, promo);
    }

    /** A quote the rider owns and that is still held. */
    public FareQuote held(String quoteId, long riderId) {
        FareQuote quote = quotes.findById(quoteId)
                .filter(q -> q.getRiderId() == riderId)
                .orElseThrow(() -> new NotFoundException("This price is no longer available. Check the fare again."));
        if (quote.getExpiresAt().isBefore(clock.instant())) {
            throw new InvalidRequestException("QUOTE_EXPIRED", "Prices have changed since you checked. Check the fare again.");
        }
        return quote;
    }

    private Map<String, List<DriverLocator.NearbyDriver>> nearbyFreeDrivers(GeoPoint pickup) {
        Instant fresh = clock.instant().minusSeconds(settings.getInt(Setting.DRIVER_LOCATION_FRESH_S));
        return locator.idleNear(pickup, settings.getInt(Setting.DISPATCH_RADIUS_M), null, fresh, null, null, false)
                .stream()
                .sorted(Comparator.comparingDouble(DriverLocator.NearbyDriver::distanceM))
                .collect(Collectors.groupingBy(DriverLocator.NearbyDriver::vehicleType));
    }

    private String addressOf(LocationInput in) {
        if (in.address() != null && !in.address().isBlank()) {
            return in.address().trim();
        }
        GeocodingService.PlaceResult place = geocoding.reverse(in.point());
        String label = place.name().equals(place.address()) || place.address().toLowerCase().startsWith(place.name().toLowerCase())
                ? place.address() : place.name() + ", " + place.address();
        return label.length() > 255 ? label.substring(0, 255) : label;
    }

    public record QuoteResult(FareQuote quote, SurgeService.SurgeInfo surge, PromoService.PromoCheck promo) {
    }
}
