package com.srmecotech.ecoride.ride;

import com.srmecotech.ecoride.IntegrationTestBase;
import com.srmecotech.ecoride.common.error.ApiException;
import com.srmecotech.ecoride.common.settings.Setting;
import com.srmecotech.ecoride.driver.Availability;
import com.srmecotech.ecoride.driver.DriverLedgerService;
import com.srmecotech.ecoride.geo.GeoMath;
import com.srmecotech.ecoride.geo.GeoPoint;
import com.srmecotech.ecoride.geo.Polyline;
import com.srmecotech.ecoride.geo.RouteTracker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RideFlowIntegrationTest extends IntegrationTestBase {

    @Autowired DriverLedgerService ledger;
    @Autowired RideViewService views;

    @Test
    void fullTripFromRequestToReceipt() {
        long rider = newRider();
        long driver = onlineDriver("MINI", near(PICKUP, 800, 200));

        Ride ride = book(rider, "MINI", PaymentMethod.UPI);
        assertThat(ride.getStatus()).isEqualTo(RideStatus.SEARCHING);
        assertThat(ride.getEstDistanceM()).isGreaterThan(5000);

        RideOffer offer = pendingOffer(ride.getId());
        assertThat(offer).isNotNull();
        assertThat(offer.getDriverId()).isEqualTo(driver);

        lifecycle.accept(driver, offer.getId(), null);
        assertThat(reload(ride.getId()).getStatus()).isEqualTo(RideStatus.ACCEPTED);
        assertThat(drivers.findById(driver).orElseThrow().getAvailability()).isEqualTo(Availability.ON_TRIP);

        // Too far to mark arrival, then drive to the pickup.
        assertThatThrownBy(() -> lifecycle.arrived(driver, ride.getId()))
                .isInstanceOf(ApiException.class).hasMessageContaining("from the pickup");
        drive(driver, near(PICKUP, 800, 200), PICKUP, 20);
        assertThat(reload(ride.getId()).getPickupTravelM()).isBetween(700, 900);
        lifecycle.arrived(driver, ride.getId());

        assertThatThrownBy(() -> lifecycle.start(driver, ride.getId(), "0000"))
                .isInstanceOf(ApiException.class).hasMessageContaining("does not match");
        lifecycle.start(driver, ride.getId(), reload(ride.getId()).getOtp());

        // Drive the planned route (straight line in tests): the GPS odometer follows.
        drive(driver, PICKUP, DROP, 60);
        Ride onTrip = reload(ride.getId());
        assertThat(views.forRider(onTrip).live().remainingM()).isLessThan(100);

        Ride done = lifecycle.complete(driver, ride.getId());
        assertThat(done.getStatus()).isEqualTo(RideStatus.COMPLETED);
        double straight = GeoMath.distanceM(PICKUP, DROP);
        assertThat(done.getActualDistanceM()).isCloseTo((int) straight, org.assertj.core.data.Percentage.withPercentage(3));
        // The straight-line "planned route" is 1.35x longer than the GPS track: beyond 20%, so the fare is recalculated.
        assertThat(done.getFarePolicy()).isEqualTo("RECALCULATED");
        assertThat(done.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(done.getDriverEarning()).isEqualByComparingTo(done.getFare().fareBeforeDiscount().subtract(done.getCommission()));
        assertThat(ledger.balance(driver)).isEqualByComparingTo(done.getDriverEarning());
        assertThat(drivers.findById(driver).orElseThrow().getAvailability()).isEqualTo(Availability.ONLINE);

        requests.rate(rider, ride.getId(), 5, List.of("Smooth ride"), null, new BigDecimal("20"));
        assertThat(ledger.balance(driver)).isEqualByComparingTo(done.getDriverEarning().add(new BigDecimal("20")));
    }

    @Test
    void upfrontFareStandsWhenTheGpsTrackFollowsTheRoute() {
        settings.update(Setting.FARE_RECALC_TOLERANCE_PCT, "40");
        long rider = newRider();
        long driver = onlineDriver("SEDAN", near(PICKUP, 300, 0));
        Ride ride = book(rider, "SEDAN", PaymentMethod.CASH);
        lifecycle.accept(driver, pendingOffer(ride.getId()).getId(), null);
        drive(driver, near(PICKUP, 300, 0), PICKUP, 5);
        lifecycle.arrived(driver, ride.getId());
        lifecycle.start(driver, ride.getId(), reload(ride.getId()).getOtp());
        drive(driver, PICKUP, DROP, 40);
        Ride done = lifecycle.complete(driver, ride.getId());
        assertThat(done.getFarePolicy()).isEqualTo("UPFRONT");
        assertThat(done.getFareTotal()).isEqualByComparingTo(done.getEstFare());
        // Cash stays due until the driver confirms it; then the driver's ledger shows the cash kept.
        assertThat(done.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        lifecycle.cashCollected(driver, ride.getId());
        assertThat(reload(ride.getId()).getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(ledger.balance(driver)).isEqualByComparingTo(done.getDriverEarning().subtract(done.getFareTotal()));
    }

    @Test
    void nearestRealDriverGetsTheOfferAndADeclineMovesItOn() {
        long rider = newRider();
        long near = onlineDriver("MINI", near(PICKUP, 400, 90));
        long far = onlineDriver("MINI", near(PICKUP, 1500, 270));
        onlineDriver("SEDAN", near(PICKUP, 100, 0)); // wrong type: never offered a Mini

        Ride ride = book(rider, "MINI", PaymentMethod.UPI);
        RideOffer first = pendingOffer(ride.getId());
        assertThat(first.getDriverId()).isEqualTo(near);
        lifecycle.decline(near, first.getId());

        RideOffer second = pendingOffer(ride.getId());
        assertThat(second.getDriverId()).isEqualTo(far);

        // The second driver does not answer: after the timeout nobody is left, the ride keeps searching ...
        mutableClock().advance(Duration.ofSeconds(settings.getInt(Setting.OFFER_TIMEOUT_S) + 1));
        assertThat(pendingOffer(ride.getId())).isNull();
        assertThat(offers.findByRideIdOrderByIdAsc(ride.getId())).extracting(RideOffer::getStatus)
                .containsExactly(RideOffer.OfferStatus.DECLINED, RideOffer.OfferStatus.EXPIRED);
        // ... until the search times out and the rider is told no cars are free.
        mutableClock().advance(Duration.ofSeconds(settings.getInt(Setting.SEARCH_TIMEOUT_S)));
        dispatch.sweep();
        assertThat(reload(ride.getId()).getStatus()).isEqualTo(RideStatus.NO_DRIVERS);
    }

    @Test
    void driversBeyondTheRadiusOrWithStaleGpsGetNoOffer() {
        long rider = newRider();
        onlineDriver("AUTO", near(PICKUP, 8000, 45));
        long stale = onlineDriver("AUTO", near(PICKUP, 500, 45));
        jdbc.update("UPDATE driver SET located_at = DATE_SUB(located_at, INTERVAL 10 MINUTE) WHERE id = ?", stale);
        Ride ride = book(rider, "AUTO", PaymentMethod.UPI);
        assertThat(pendingOffer(ride.getId())).isNull();
    }

    @Test
    void cancellingIsFreeWhileSearchingAndWithinTheGraceWindowThenCharged() {
        long rider = newRider();
        riderAccounts.topUp(rider, new BigDecimal("1000"));
        long driver = onlineDriver("MINI", near(PICKUP, 300, 0));

        Ride searching = book(rider, "MINI", PaymentMethod.WALLET);
        Ride cancelled = requests.cancel(rider, searching.getId(), "Changed my plans");
        assertThat(cancelled.getCancellationFee()).isEqualByComparingTo("0");

        Ride ride = book(rider, "MINI", PaymentMethod.WALLET);
        lifecycle.accept(driver, pendingOffer(ride.getId()).getId(), null);
        mutableClock().advance(Duration.ofSeconds(settings.getInt(Setting.RIDER_CANCEL_GRACE_S) + 5));
        BigDecimal walletBefore = jdbc.queryForObject("SELECT wallet_balance FROM rider WHERE id = ?", BigDecimal.class, rider);
        Ride late = requests.cancel(rider, ride.getId(), "Too late");
        assertThat(late.getCancellationFee()).isEqualByComparingTo("50");
        BigDecimal walletAfter = jdbc.queryForObject("SELECT wallet_balance FROM rider WHERE id = ?", BigDecimal.class, rider);
        assertThat(walletBefore.subtract(walletAfter)).isEqualByComparingTo("50");
        // The driver gets their share of the fee and is free again.
        assertThat(ledger.balance(driver)).isEqualByComparingTo("40");
        assertThat(drivers.findById(driver).orElseThrow().getAvailability()).isEqualTo(Availability.ONLINE);
    }

    @Test
    void driverCancellationIsPenalisedAndTheRideIsRedispatched() {
        long rider = newRider();
        long first = onlineDriver("MINI", near(PICKUP, 300, 0));
        long second = onlineDriver("MINI", near(PICKUP, 900, 180));
        Ride ride = book(rider, "MINI", PaymentMethod.UPI);
        lifecycle.accept(first, pendingOffer(ride.getId()).getId(), null);

        Ride back = lifecycle.driverCancel(first, ride.getId(), "Flat tyre");
        assertThat(back.getStatus()).isEqualTo(RideStatus.SEARCHING);
        assertThat(back.getDriverId()).isNull();
        assertThat(ledger.balance(first)).isNegative();
        RideOffer next = pendingOffer(ride.getId());
        assertThat(next.getDriverId()).isEqualTo(second);
    }

    @Test
    void riderNoShowAfterTheWaitChargesTheRider() {
        long rider = newRider();
        long driver = onlineDriver("AUTO", PICKUP);
        Ride ride = book(rider, "AUTO", PaymentMethod.UPI);
        lifecycle.accept(driver, pendingOffer(ride.getId()).getId(), null);
        lifecycle.arrived(driver, ride.getId());
        mutableClock().advance(Duration.ofMinutes(settings.getInt(Setting.NO_SHOW_WAIT_MIN)).plusSeconds(5));
        Ride noShow = lifecycle.driverCancel(driver, ride.getId(), null);
        assertThat(noShow.getStatus()).isEqualTo(RideStatus.CANCELLED);
        assertThat(noShow.getCancelReason()).contains("did not come");
        assertThat(noShow.getCancellationFee()).isEqualByComparingTo("30");
    }

    @Test
    void aRiderHasOneRideInFlightAndAPendingDriverCannotGoOnline() {
        long rider = newRider();
        book(rider, "MINI", PaymentMethod.CASH);
        assertThatThrownBy(() -> book(rider, "MINI", PaymentMethod.CASH))
                .isInstanceOf(ApiException.class).hasMessageContaining("already have a ride");

        long pending = driverAccounts.signup(new com.srmecotech.ecoride.driver.DriverDtos.SignupRequest("New Applicant",
                "6000012345", null, "Secret@123", "MH1220200000002", "MINI", "Tata", "Tiago", "Blue", "MH12ZZ0001", 2023)).profile().id();
        assertThatThrownBy(() -> lifecycle.goOnline(pending, PICKUP))
                .isInstanceOf(ApiException.class).hasMessageContaining("verified");
    }

    @Test
    void gpsJumpsAreNotAddedToTheTripDistance() {
        long rider = newRider();
        long driver = onlineDriver("MINI", PICKUP);
        Ride ride = book(rider, "MINI", PaymentMethod.UPI);
        lifecycle.accept(driver, pendingOffer(ride.getId()).getId(), null);
        lifecycle.arrived(driver, ride.getId());
        lifecycle.start(driver, ride.getId(), reload(ride.getId()).getOtp());
        // A real (non-simulated) fix 5 km away one moment later implies thousands of km/h: ignored.
        locations.driverPing(driver, near(PICKUP, 5000, 90), null, 30.0, 10, false);
        assertThat(reload(ride.getId()).getActualDistanceM()).isZero();
        // A poor-accuracy fix is not counted either.
        locations.driverPing(driver, near(PICKUP, 40, 90), null, 30.0, 400, false);
        assertThat(reload(ride.getId()).getActualDistanceM()).isZero();
    }

    @Test
    void liveDistanceToPickupFollowsTheRoadRouteNotTheStraightLine() {
        long rider = newRider();
        long driver = onlineDriver("MINI", near(PICKUP, 1000, 90));
        Ride ride = book(rider, "MINI", PaymentMethod.UPI);
        // An L-shaped road route to the pickup: 1 km south, then 1 km west... seen from the driver.
        GeoPoint start = near(PICKUP, 1000, 90);
        GeoPoint corner = near(start, 600, 180);
        List<GeoPoint> road = List.of(start, corner, near(PICKUP, 600, 180), PICKUP);
        RouteTracker tracker = new RouteTracker(road);
        lifecycle.accept(driver, pendingOffer(ride.getId()).getId(),
                new com.srmecotech.ecoride.geo.RouteResult((int) tracker.totalM(), 300, Polyline.encode(road), "OSRM"));
        RideViews.Live live = views.forRider(reload(ride.getId())).live();
        assertThat(live.toPickupM()).isCloseTo((int) tracker.totalM(), org.assertj.core.data.Offset.offset(10));
        assertThat(live.toPickupM()).isGreaterThan((int) GeoMath.distanceM(start, PICKUP) + 500);
        assertThat(live.toPickupEtaS()).isCloseTo(300, org.assertj.core.data.Offset.offset(5));
    }

    /** Sends simulated GPS fixes along a straight line, in `steps` steps. */
    private void drive(long driverId, GeoPoint from, GeoPoint to, int steps) {
        for (int i = 1; i <= steps; i++) {
            double t = i / (double) steps;
            locations.driverPing(driverId, GeoMath.interpolate(from, to, t), null, 30.0, 8, true);
        }
    }
}
