package com.srmecotech.ecoride;

import com.srmecotech.ecoride.common.settings.Setting;
import com.srmecotech.ecoride.common.settings.SettingsService;
import com.srmecotech.ecoride.driver.DriverAccountService;
import com.srmecotech.ecoride.driver.DriverDtos;
import com.srmecotech.ecoride.driver.DriverRepository;
import com.srmecotech.ecoride.driver.DriverStatus;
import com.srmecotech.ecoride.geo.GeoMath;
import com.srmecotech.ecoride.geo.GeoPoint;
import com.srmecotech.ecoride.geo.LocationInput;
import com.srmecotech.ecoride.pricing.QuoteService;
import com.srmecotech.ecoride.ride.DispatchService;
import com.srmecotech.ecoride.ride.LocationService;
import com.srmecotech.ecoride.ride.PaymentMethod;
import com.srmecotech.ecoride.ride.Ride;
import com.srmecotech.ecoride.ride.RideLifecycleService;
import com.srmecotech.ecoride.ride.RideOffer;
import com.srmecotech.ecoride.ride.RideOfferRepository;
import com.srmecotech.ecoride.ride.RideRepository;
import com.srmecotech.ecoride.ride.RideRequestService;
import com.srmecotech.ecoride.rider.RiderAccountService;
import com.srmecotech.ecoride.rider.RiderDtos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Real MySQL (database ecoride_test), straight-line routing, no network. Each test makes its own
 * riders and drivers; the seeded simulated fleet is taken offline so dispatch only sees them.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestClockConfig.class)
public abstract class IntegrationTestBase {

    /** Balewadi High Street and two points a few hundred metres away. */
    protected static final GeoPoint PICKUP = new GeoPoint(18.570719, 73.774308);
    protected static final GeoPoint DROP = new GeoPoint(18.552844, 73.826644);

    private static final AtomicInteger SEQ = new AtomicInteger((int) (System.currentTimeMillis() % 100000));

    @Autowired protected Clock clock;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected SettingsService settings;
    @Autowired protected RiderAccountService riderAccounts;
    @Autowired protected DriverAccountService driverAccounts;
    @Autowired protected DriverRepository drivers;
    @Autowired protected QuoteService quotes;
    @Autowired protected RideRequestService requests;
    @Autowired protected RideLifecycleService lifecycle;
    @Autowired protected DispatchService dispatch;
    @Autowired protected LocationService locations;
    @Autowired protected RideRepository rides;
    @Autowired protected RideOfferRepository offers;

    @BeforeEach
    void isolateFromTheSimulatedFleet() {
        ((MutableClock) clock).reset();
        jdbc.update("UPDATE ride_offer SET status = 'CANCELLED' WHERE status = 'PENDING'");
        jdbc.update("UPDATE ride SET status = 'CANCELLED', cancelled_by = 'SYSTEM' WHERE status IN ('SEARCHING', 'ACCEPTED', 'ARRIVED', 'IN_PROGRESS')");
        jdbc.update("UPDATE driver SET availability = 'OFFLINE'");
        settings.update(Setting.SURGE_ENABLED, "false");
    }

    @AfterEach
    void restoreSettings() {
        settings.resetToDefaults();
    }

    protected long newRider() {
        int n = SEQ.incrementAndGet();
        return riderAccounts.signup(new RiderDtos.SignupRequest("Test Rider " + n, "7" + String.format("%09d", n), null, "Secret@123"))
                .profile().id();
    }

    /** An approved driver of the given type, online at a point. */
    protected long onlineDriver(String type, GeoPoint at) {
        int n = SEQ.incrementAndGet();
        long id = driverAccounts.signup(new DriverDtos.SignupRequest("Test Driver " + n, "8" + String.format("%09d", n), null,
                "Secret@123", "MH1220200000001", type, "Maruti Suzuki", "Swift", "White",
                "MH12TT" + String.format("%04d", n % 10000), 2022)).profile().id();
        jdbc.update("UPDATE driver SET status = ?, approved_at = NOW() WHERE id = ?", DriverStatus.APPROVED.name(), id);
        lifecycle.goOnline(id, at);
        return id;
    }

    protected GeoPoint near(GeoPoint p, double metres, double bearing) {
        return GeoMath.offset(p, metres, bearing);
    }

    protected Ride book(long riderId, String type, PaymentMethod method) {
        var q = quotes.quote(riderId, new LocationInput(PICKUP.lat(), PICKUP.lng(), "Balewadi High Street"),
                new LocationInput(DROP.lat(), DROP.lng(), "Savitribai Phule Pune University"), null);
        return requests.request(riderId, q.quote().getId(), type, method);
    }

    protected RideOffer pendingOffer(long rideId) {
        dispatch.sweep();
        return offers.findFirstByRideIdAndStatus(rideId, RideOffer.OfferStatus.PENDING).orElse(null);
    }

    protected Ride reload(long rideId) {
        return rides.findById(rideId).orElseThrow();
    }

    protected MutableClock mutableClock() {
        return (MutableClock) clock;
    }
}
