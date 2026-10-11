package com.srmecotech.ecoride.ride;

import com.srmecotech.ecoride.IntegrationTestBase;
import com.srmecotech.ecoride.driver.Availability;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Two people acting on the same ride at the same instant must leave it in one consistent state. */
class ConcurrencyIntegrationTest extends IntegrationTestBase {

    @RepeatedTest(5)
    void riderCancelRacingDriverAcceptLeavesOneOutcome() throws Exception {
        long rider = newRider();
        long driver = onlineDriver("MINI", near(PICKUP, 300, 0));
        Ride ride = book(rider, "MINI", PaymentMethod.UPI);
        long offerId = pendingOffer(ride.getId()).getId();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        Future<Boolean> accept = pool.submit(() -> {
            go.await();
            try {
                lifecycle.accept(driver, offerId, null);
                return true;
            } catch (RuntimeException ex) {
                return false;
            }
        });
        Future<Boolean> cancel = pool.submit(() -> {
            go.await();
            try {
                requests.cancel(rider, ride.getId(), "Changed my plans");
                return true;
            } catch (RuntimeException ex) {
                return false;
            }
        });
        go.countDown();
        boolean accepted = accept.get(20, TimeUnit.SECONDS);
        cancel.get(20, TimeUnit.SECONDS);
        pool.shutdown();

        Ride after = reload(ride.getId());
        Availability availability = drivers.findById(driver).orElseThrow().getAvailability();
        // The cancel always wins in the end (it is allowed after acceptance too); what matters is that
        // the driver is never left marked as busy on a cancelled ride.
        assertThat(after.getStatus()).isEqualTo(RideStatus.CANCELLED);
        assertThat(availability).isEqualTo(Availability.ONLINE);
        if (!accepted) {
            assertThat(offers.findById(offerId).orElseThrow().getStatus()).isEqualTo(RideOffer.OfferStatus.CANCELLED);
        }
    }

    @Test
    void twoAcceptsOfTheSameOfferOnlyOneSucceeds() throws Exception {
        long rider = newRider();
        long driver = onlineDriver("SEDAN", near(PICKUP, 300, 0));
        Ride ride = book(rider, "SEDAN", PaymentMethod.UPI);
        long offerId = pendingOffer(ride.getId()).getId();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        Future<Boolean> a = pool.submit(() -> {
            go.await();
            try {
                lifecycle.accept(driver, offerId, null);
                return true;
            } catch (RuntimeException ex) {
                return false;
            }
        });
        Future<Boolean> b = pool.submit(() -> {
            go.await();
            try {
                lifecycle.accept(driver, offerId, null);
                return true;
            } catch (RuntimeException ex) {
                return false;
            }
        });
        go.countDown();
        int successes = (a.get(20, TimeUnit.SECONDS) ? 1 : 0) + (b.get(20, TimeUnit.SECONDS) ? 1 : 0);
        pool.shutdown();
        assertThat(successes).isEqualTo(1);
        assertThat(reload(ride.getId()).getStatus()).isEqualTo(RideStatus.ACCEPTED);
    }

    @Test
    void oneDriverIsNeverOfferedTwoRidesAtOnce() {
        long driver = onlineDriver("BIKE", near(PICKUP, 200, 0));
        Ride first = book(newRider(), "BIKE", PaymentMethod.UPI);
        Ride second = book(newRider(), "BIKE", PaymentMethod.UPI);
        dispatch.sweep();
        long pendingForDriver = offers.findAll().stream()
                .filter(o -> o.getDriverId() == driver && o.getStatus() == RideOffer.OfferStatus.PENDING)
                .count();
        assertThat(pendingForDriver).isEqualTo(1);
        // The other ride is still searching, not lost.
        assertThat(java.util.List.of(reload(first.getId()).getStatus(), reload(second.getId()).getStatus()))
                .containsOnly(RideStatus.SEARCHING);
    }

    @Test
    void gpsPingsDuringCompletionDoNotDeadlock() throws Exception {
        long rider = newRider();
        long driver = onlineDriver("MINI", PICKUP);
        Ride ride = book(rider, "MINI", PaymentMethod.UPI);
        lifecycle.accept(driver, pendingOffer(ride.getId()).getId(), null);
        lifecycle.arrived(driver, ride.getId());
        lifecycle.start(driver, ride.getId(), reload(ride.getId()).getOtp());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        Future<?> pings = pool.submit(() -> {
            go.await();
            for (int i = 1; i <= 40; i++) {
                try {
                    locations.driverPing(driver, near(PICKUP, i * 20.0, 90), null, 30.0, 8, true);
                } catch (RuntimeException ignored) {
                    // A ping may lose a lock wait; the next one carries on.
                }
            }
            return null;
        });
        Future<Ride> complete = pool.submit(() -> {
            go.await();
            Thread.sleep(30);
            return lifecycle.complete(driver, ride.getId());
        });
        go.countDown();
        pings.get(30, TimeUnit.SECONDS);
        assertThat(complete.get(30, TimeUnit.SECONDS).getStatus()).isEqualTo(RideStatus.COMPLETED);
        pool.shutdown();
    }
}
